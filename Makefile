.DEFAULT_GOAL := compile

VPROXY_VERSION = $(shell curl https://raw.githubusercontent.com/wkgcass/vproxy/master/base/src/main/java/io/vproxy/base/util/Version.java | grep '_THE_VERSION_' | cut -d '"' -f 2)
VERSION = $(shell cat ./src/main/kotlin/io/vproxy/vpss/util/Consts.kt | grep '_THE_VERSION_' | cut -d '"' -f 2)

REAL_VPROXY_VERSION = $(shell java -jar vproxy.jar version 2>&1 >/dev/null)

DOCKER_IMAGE_VERSION ?= "latest"

.PHONY: clean
clean:
	bash ./gradlew clean
	cd launcher && bash ./gradlew clean
	rm  -f ./misc/docker/vpss/vpss.jar
	rm -rf ./misc/docker/vpss/ui
	rm -f ./misc/docker/vpws-agent-with-dhclient/vproxy.jar
	rm -f ./misc/docker/vpss-launcher/vpss-launcher.jar
	rm -f ./vproxy-no-kt-runtime.jar

vproxy.jar:
	wget https://github.com/wkgcass/vproxy/releases/download/$(VPROXY_VERSION)/vproxy-$(VPROXY_VERSION).jar
	mv vproxy-$(VPROXY_VERSION).jar vproxy.jar

.PHONY: vproxy-no-kt-runtime.jar
vproxy-no-kt-runtime.jar: vproxy.jar
	cp vproxy.jar vproxy-no-kt-runtime.jar
	zip -d -q vproxy-no-kt-runtime.jar 'org/*'
	zip -d -q vproxy-no-kt-runtime.jar 'kotlin*'
	zip -d -q vproxy-no-kt-runtime.jar 'DebugProbesKt.bin'
	zip -d -q vproxy-no-kt-runtime.jar 'META-INF/*kotlin*'
	zip -d -q vproxy-no-kt-runtime.jar 'META-INF/maven/*'
	zip -d -q vproxy-no-kt-runtime.jar 'META-INF/proguard/*'
	zip -d -q vproxy-no-kt-runtime.jar 'META-INF/versions/*'

.PHONY: pktgen
pktgen: vproxy.jar
	java -Deploy=PacketFilterGenerator -jar vproxy.jar in=./src/main/resources/flows.txt out=./src/main/java/io/vproxy/vpss/network/VPSSPacketFilterBase.java class=io.vproxy.vpss.network.VPSSPacketFilterBase

.PHONY: compile
compile: vproxy-no-kt-runtime.jar pktgen
	bash ./gradlew shadowJar
.PHONY: jar
jar: compile

.PHONY: compile-launcher
compile-launcher: vproxy-no-kt-runtime.jar
	cd launcher && bash ./gradlew shadowJar

.PHONY: docker-base
docker-base:
	docker rmi -f vproxyio/vpss-base:$(DOCKER_IMAGE_VERSION)
	docker build --no-cache -t vproxyio/vpss-base:$(DOCKER_IMAGE_VERSION) ./misc/docker/vpss-base

.PHONY: docker
docker: compile
	cp build/libs/vpss.jar misc/docker/vpss/vpss.jar
	rm -rf ./misc/docker/vpss/ui
	cp -r ui misc/docker/vpss/ui
	docker rmi -f vproxyio/vpss:$(DOCKER_IMAGE_VERSION)
	docker build --no-cache -t vproxyio/vpss:$(DOCKER_IMAGE_VERSION) ./misc/docker/vpss

.PHONY: docker-launcher
docker-launcher: compile-launcher
	cp launcher/build/libs/vpss-launcher.jar misc/docker/vpss-launcher/vpss-launcher.jar
	docker rmi -f vproxyio/vpss-launcher:latest
	docker build --no-cache -t vproxyio/vpss-launcher:latest ./misc/docker/vpss-launcher

.PHONY: build
build: compile docker-vpss-base docker docker-launcher

.PHONY: install
install:
	bash ./install.sh

.PHONY: uninstall
uninstall:
	bash ./uninstall.sh

.PHONY: docker-dhclient
docker-dhclient:
	docker rmi -f vproxyio/tools-dhclient:$(DOCKER_IMAGE_VERSION)
	docker build --no-cache -t vproxyio/tools-dhclient:$(DOCKER_IMAGE_VERSION) ./misc/docker/dhclient

.PHONY: docker-iperf3-with-dhclient
docker-iperf3-with-dhclient:
	docker rmi -f vproxyio/tools-iperf3-with-dhclient:$(DOCKER_IMAGE_VERSION)
	docker build --no-cache -t vproxyio/tools-iperf3-with-dhclient:$(DOCKER_IMAGE_VERSION) ./misc/docker/iperf3-with-dhclient

.PHONY: docker-socat-with-dhclient
docker-socat-with-dhclient:
	docker rmi -f vproxyio/tools-socat-with-dhclient:$(DOCKER_IMAGE_VERSION)
	docker build --no-cache -t vproxyio/tools-socat-with-dhclient:$(DOCKER_IMAGE_VERSION) ./misc/docker/socat-with-dhclient

.PHONY: docker-vpws-agent-with-dhclient
docker-vpws-agent-with-dhclient: vproxy.jar
	cp vproxy.jar ./misc/docker/vpws-agent-with-dhclient/vproxy.jar
	docker rmi -f vproxyio/vpws-agent-with-dhclient:$(DOCKER_IMAGE_VERSION)
	docker build --no-cache -t vproxyio/vpws-agent-with-dhclient:$(DOCKER_IMAGE_VERSION) ./misc/docker/vpws-agent-with-dhclient
