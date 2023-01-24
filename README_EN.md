# vpss

For chinese version README, see `README.md`.  
中文文档请见`README.md`。

VProxy Software Switch.

VPSS is a switch instead of a router, but it has SDN capabilities to filter and modify packets, and provides much more features than traditional l3 switches and routers.

## I. capabilities

1. switching
2. routing
3. virtual networks and vlans
4. remote switch (need to be used with vproxy)
5. ipv6 (can be enabled or disabled)
6. statistics
7. mac and arp tables operations
8. black/white list
9. ratelimit
10. custom flow table
11. vpws-agent

## II. setup

### 2.1 hardware and os

1. x86 machine (arm still in progress)
2. linux based operating system
3. kernel version at least 5.4. It's recommended to use 5.10 or higher.

> 1. You can install ubuntu 20.04 hwe edge (5.11) or debian 11 (5.10).
> 2. No need to configure your modem: keep it in router mode and enable dhcp. This is by default for home modems. VPSS will automatically configure the network with dhcp, and use its SDN capabilities to perform routing functions.

### 2.2 software

Docker is required:

```
apt-get update
apt-get install -y docker.io
```

### 2.3 install

Copy the script and run:

```
bash ./install.sh
```

## III. network topology

### 3.1 If your machine has two or more ports

`vpss` should be put between the router and your devices:

```
+--------+    +------+          +----+          +-------+
| Router |----| VPSS |----+-----| AP |·)))  (((·| Phone |
+--------+    +------+    |     +----+          +-------+
 or modem                 |     bridge
 in router                |
 mode                     |     +----+
                          +-----| PC |
                                +----+
```

### 3.2 If your machine has only one port

You will need a switch that supports VLAN.

```
+--------+                      +------+                     +----+          +-------+
| Router |-------------+        | VPSS |       +-------------| AP |·)))  (((·| Phone |
+--------+             |        +------+       |             +----+          +-------+
 or modem      vlan101 |  nvlan101 ||          | vlan 201    bridge
 in router     (access)|     trunk ||          | (access)
 mode                +---------------------------+           +----+
                     |        VLAN Switch        |-----------| PC |
                     +---------------------------+           +----+
               vlan101 |
               (access)|
                       |
                     admin
```

Note:

1. In VLAN Switch: native VLAN of the port to VPSS should be set to the `Router` vlan (in this example: `101`)
2. In VPSS: VLAN settings should be manually configured
3. The first time you launch vpss, you will need to connect to the admin port, and manually set your device's ip to `100.118.103.118` and mask to `/31` or `255.255.255.254` (in some os, you will need to set the mask to `/28` or `255.255.255.240` because it treats `100.118.103.119` as broadcast address instead of two hosts network).

## IV. configure your network

On your device, visit `http://vgw.special.vproxy.io` or `http://100.118.103.119` in your browser.

If it's the first time you visit the system, use username `admin` and any password you would like to set to login into the system. The password will be persisted and you will need to use this password for the next time. You may change the password after logging-in.

After configuring, remember to check and persist the configuration otherwise it will be lost after rebooting.

## V. Note

### 5.1 Use with switches

VPSS itself is a switch, so it must be careful when using with switches not to connect the ports of VPSS in the same VLAN to ports of switch in the same VLAN, otherwise it will trigger broadcast storm.

It's important to note that, VPSS uses DHCP to automatically configure the network when launching, it will add all managed ports registered in configuration file (or all physical ports if no config or no managed ports registered) to the same virtual network, and transmitted packets will not carry 802.1q tags.  
As a result, on switches supporting VLAN, you must configure native VLAN on the port for VPSS to access the DHCP server (usually your router/modem), and ensure that other ports for VPSS disable native VLAN or are configured to another native VLAN, to prevent broadcast storm.

## VI. License

This project is open source under GPLv2

And includes the source code or binary of the following open source projects:

1. vproxy
2. jquery
3. chartjs
4. semantic ui
5. vue, vue-i18n, vue-resource
6. js-cookie
7. noto

## VII. Develop, Compile and Pack

See `doc/en/develop.md`
