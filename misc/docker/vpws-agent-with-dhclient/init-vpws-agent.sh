#!/bin/bash
set -e
/usr/sbin/ip addr flush eth0
/usr/sbin/dhclient eth0
/usr/sbin/ip addr show eth0
exec /init.sh -Deploy=WebSocksProxyAgent -Dvfd=posix -Dvproxy.DhcpGetDnsListNics=eth0 -jar /vproxy.jar $@
