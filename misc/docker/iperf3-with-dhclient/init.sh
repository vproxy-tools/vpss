#!/bin/bash
set -e
/usr/sbin/ip addr flush eth0
/usr/sbin/dhclient eth0
/usr/sbin/ip addr show eth0
exec /usr/bin/iperf3 $@
