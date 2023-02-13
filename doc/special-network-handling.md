# 特殊的网络处理

## tcp dns

任何目标端口为53的TCP包都会被丢弃，用来禁止TCP DNS。该规则发生在白名单检测后，所以你可以添加白名单来放通TCP DNS。

## vgw.special.vproxy.io

用于访问或者配置VPSS的域名。为防止上联网络异常，VPSS会直接响应该域名给设备，保证即使公网连接有问题也可以正常访问VPSS。

## ssh.vgw.special.vproxy.io

用于通过ssh访问VPSS设备的域名。你可以直接ssh到`vgw.special.vproxy.io`而不是使用这个域名。该域名主要用于网络中有多个vpss实例的场合。

## web.vgw.special.vproxy.io

用于访问VPSS管理页面的域名。你可以直接访问`vgw.special.vproxy.io`而不是使用这个域名。该域名主要用于网络中有多个vpss实例的场合。

## wsagent.vgw.special.vproxy.io

响应由该VPSS实例启动的`wsagent`服务的IP地址。

## Windows连通性检查 (NCSI)

Windows检测网络可用性时会连接该域名。VPSS会直接处理到`www.msftconnecttest.com`和`www.msftncsi.com`的请求，防止某些ISP错误地禁止这些网络流量。

目前VPSS处理了到该域名的如下请求：

1. `GET /connecttest.txt`: 返回字符串`Microsoft Connect Test`
2. `GET /redirect`: 返回302，跳转到`http://go.microsoft.com/fwlink/?LinkID=219472&clcid=0x409`
3. `GET /ncsi.txt`: 返回字符串`Microsoft NCSI`

其他请求会响应404。

此外，VPSS还会直接解析`dns.msftncsi.com`为`131.107.255.255`，以确保通过连通性检查。

另外请注意，`ipv6.msftconnecttest.com`相关的请求以及`dns.msftncsi.com`的AAAA记录均没有被特殊处理，以防止某些实际上无法接入ipv6网络的设备错误地认为其能够接入ipv6。

具体请见[这篇帖子](https://docs.microsoft.com/en-us/answers/questions/348910/good-idea-add-wwwmsftconnecttestcom-dns-zone-on-in.html)和<a href="https://docs.microsoft.com/en-us/previous-versions/windows/it-pro/windows-vista/cc766017(v=ws.10)">这篇文档</a>。
