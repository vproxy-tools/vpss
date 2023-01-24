# Build a developing environment on virtualbox

## common

### setup

```shell
apt-get update
apt-get install -y net-tools arping
```

### docker

```shell
apt-get remove -y docker docker-engine docker.io containerd runc
apt-get update
apt-get install -y \
	ca-certificates \
	curl \
	gnupg \
	lsb-release
mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

## router

### virtualbox

nics:

1. NAT
2. internal network (uplink)
3. host-only network

### /etc/netplan/00-installer-config.yaml

```yaml
network:
  ethernets:
    enp0s3:
      dhcp4: true
    enp0s8:
      dhcp4: false
      addresses: [192.168.114.1/24]
    enp0s9:
      dhcp4: false
      addresses: [192.168.56.2/24]
  version: 2
```

### bash

```shell
apt-get install -y isc-dhcp-server bind9
```

### /etc/dhcp/dhcpd.conf

```
subnet 192.168.114.0 netmask 255.255.255.0 {
  range 192.168.114.2 192.168.114.254;
  option routers 192.168.114.1;
  option domain-name-servers 192.168.114.1;
}
```

### /etc/bind/named.conf.options

```
	listen-on {
		192.168.114.1;
	};

	forwarders {
		127.0.0.53;
	};
```

### /etc/sysctl.conf

```
net.ipv4.ip_forward=1
```

### /etc/systemd/system/setup.service

```ini
[Unit]
Description=Setup
After=network.target

[Service]
Type=oneshot
ExecStart=/root/setup.sh

[Install]
WantedBy=multi-user.target
```

### /root/setup.sh

```shell
#!/bin/bash

/usr/sbin/iptables -t nat -A POSTROUTING -s 192.168.114.1/32 -j ACCEPT
/usr/sbin/iptables -t nat -A POSTROUTING -s 192.168.114.0/24 -j MASQUERADE
```

### bash

```shell
chmod +x /root/setup.sh
systemctl daemon-reload
systemctl enable setup
```

### harbor.yml

```yaml
hostname: harbor.vproxy.io

http:
  port: 80

harbor_admin_password: Harbor12345

database:
  password: root123
  max_idle_conns: 100
  max_open_conns: 900
data_volume: /data

trivy:
  ignore_unfixed: false
  skip_update: false
  offline_scan: false
  insecure: false

jobservice:
  max_job_workers: 10

notification:
  webhook_job_max_retry: 10

chart:
  absolute_url: disabled

log:
  level: info
  local:
    rotate_count: 50
    rotate_size: 200M
    location: /var/log/harbor

_version: 2.5.0

upload_purging:
  enabled: true
  age: 168h
  interval: 24h
  dryrun: false
```

### browser

Go into harbor, create a user and set it to admin

## vpss

### virtualbox

nics:

1. internal network (uplink) enable promiscuous mode
2. internal network (downlink) enable promiscuous mode
3. host-only network

### /etc/netplan/00-installer-config.yaml

```yaml
network:
  ethernets:
    enp0s17:
      dhcp4: true
    enp0s8:
      dhcp4: no
      optional: true
    enp0s9:
      dhcp4: no
      addresses: [192.168.56.3/24]
  version: 2
```

### /etc/hosts

```
192.168.114.1 harbor.vproxy.io
```

### /etc/docker/daemon.json

```json
{
	"insecure-registries": [ "192.168.114.1:80" ]
}
```

### /root/setup.sh

```shell
#!/bin/bash

set -e

/usr/sbin/ip link add veth0 type veth peer name veth1
/usr/sbin/ip link add br0 type bridge
/usr/sbin/ip link set veth1 master br0
/usr/sbin/ip link set veth0 up
/usr/sbin/ip link set veth1 up

/usr/sbin/ip netns add ns100
/usr/sbin/ip netns add ns200
/usr/sbin/ip link add veth100 type veth peer name veth100p
/usr/sbin/ip link add veth200 type veth peer name veth200p

/usr/sbin/ip link set veth100p master br0
/usr/sbin/ip link set veth200p master br0
/usr/sbin/ip link set veth100 netns ns100
/usr/sbin/ip link set veth200 netns ns200
/usr/sbin/ip link set veth100p up
/usr/sbin/ip link set veth200p up
/usr/sbin/ip netns exec ns100 /usr/sbin/ip link set lo up
/usr/sbin/ip netns exec ns200 /usr/sbin/ip link set lo up
/usr/sbin/ip netns exec ns100 /usr/sbin/ip link set veth100 up
/usr/sbin/ip netns exec ns200 /usr/sbin/ip link set veth200 up

/usr/sbin/ip link set br0 up
/usr/sbin/ip link set dev br0 type bridge vlan_filtering 1
/usr/sbin/bridge vlan add dev veth100p vid 100
/usr/sbin/bridge vlan add dev veth200p vid 200
/usr/sbin/bridge vlan add dev veth1 vid 100
/usr/sbin/bridge vlan add dev veth1 vid 200
/usr/sbin/ethtool -K veth1 rxvlan off txvlan off rx-vlan-filter off

/usr/sbin/ip netns exec ns100 /usr/sbin/ip link add link veth100 name veth100.100 type vlan id 100
/usr/sbin/ip netns exec ns200 /usr/sbin/ip link add link veth200 name veth200.200 type vlan id 200
/usr/sbin/ip netns exec ns100 /usr/sbin/ip link set veth100.100 up
/usr/sbin/ip netns exec ns200 /usr/sbin/ip link set veth200.200 up
/usr/sbin/ip netns exec ns100 /usr/sbin/ip addr add 10.1.1.100/24 dev veth100.100
/usr/sbin/ip netns exec ns200 /usr/sbin/ip addr add 10.1.1.200/24 dev veth200.200
```

### bash

```shell
chmod +x /root/setup.sh
systemctl daemon-reload
systemctl enable setup
```

### install.sh

```shell
#!/bin/bash
set -e

mkdir -p /etc/vpss

echo " \$image --ignore-network-interfaces=enp0s9 --no-session-timeout=true --include-network-interfaces=veth0" > /etc/vpss/launch-vpss

echo "ubuntu:20.04" > /etc/vpss/require-images
echo "/vpss-base" >> /etc/vpss/require-images
echo "/vpss" >> /etc/vpss/require-images
echo "/tools-dhclient" >> /etc/vpss/require-images
echo "/tools-socat-with-dhclient" >> /etc/vpss/require-images
echo "/vpws-agent-with-dhclient" >> /etc/vpss/require-images

docker run -d --restart=always \
    --net=host \
    -v /var/run/docker.sock:/var/run/docker.sock:ro \
    -v /etc/vpss:/etc/vpss \
    --name=vpss-launcher \
    192.168.114.1:80/vproxyio/vpss-launcher:latest --image-prefix="192.168.114.1:80/vproxyio"
```

## user device

Install a desktop linux.

### virtualbox

nics:

1. internal network (downlink)
2. host-only-network

### bash

```shell
systemctl disable NetworkManager
systemctl stop NetworkManager
```

### /etc/netplan/1-network-manager-all.yaml

```
network:
  version: 2
  ethernets:
    enp0s3:
      dhcp4: true
    enp0s8:
      dhcp4: no
      addresses: [192.168.56.4/24]
```

## host machine

### /etc/hosts

```
192.168.56.2 harbor.vproxy.io
```

### /etc/docker/daemon.json

```json
{
	"insecure-registries": [ "192.168.56.2:80" ]
}
```
