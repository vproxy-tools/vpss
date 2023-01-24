FROM ubuntu:20.04

COPY sources.list /etc/apt/sources.list

RUN apt-get update -y
RUN apt-get install -y iproute2 iputils-ping libelf-dev wget isc-dhcp-client ethtool redis-tools apt-transport-https ca-certificates curl gnupg lsb-release tcpdump net-tools lsof arping vim procps iptables socat kmod tar zip
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
RUN echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu `lsb_release -cs` stable" | tee /etc/apt/sources.list.d/docker.list
RUN apt-get update -y
RUN apt-get install -y docker-ce-cli

RUN wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_x64_linux_hotspot_17_35.tar.gz && tar zxf OpenJDK17-jdk_x64_linux_hotspot_17_35.tar.gz && rm -f OpenJDK17-jdk_x64_linux_hotspot_17_35.tar.gz

ENV JAVA_HOME="/jdk-17+35"
ENV PATH="${PATH}:/jdk-17+35/bin"

RUN mkdir -p /etc/vpss
RUN mkdir -p /vproxy/vpss/ui

COPY dhclient.conf /dhclient.conf
COPY init.sh /init.sh

RUN chmod +x /init.sh
RUN mkdir -p /var/run/docker

ENTRYPOINT []
CMD []
