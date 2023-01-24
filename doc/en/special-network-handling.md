# special network handling

## vgw.special.vproxy.io

The domain used to access or configure VPSS. The VPSS will directly respond the domain request to devices to ensure users can manage the device even when public network is down.

## ssh.vgw.special.vproxy.io

The domain used to ssh to VPSS. You may directly ssh to `vgw.special.vproxy.io` instead of using this domain. This domain is used when multiple vpss instances exist in the network.

## web.vgw.special.vproxy.io

The domain used to visit admin web page of VPSS. Your may directly access `vgw.special.vproxy.io` instead of using this domain. This domain is used when multiple vpss instances exist in the network.

## wsagent.vgw.special.vproxy.io

Respond ip address of `wsagent` service launched by this VPSS instance.

## Windows access availablility check (NCSI)

Windows will access this domain to check network availability. VPSS will directly respond requests to domain `www.msftconnecttest.com` and `www.msftncsi.com`, to prevent ISP mistakenly forbidding these network traffic.

Currently VPSS handles the following requests to this domain:

1. `GET /connecttest.txt`: responds string `Microsoft Connect Test`
2. `GET /redirect`: responds 302 and redirect to `http://go.microsoft.com/fwlink/?LinkID=219472&clcid=0x409`
3. `GET /ncsi.txt`: responds string `Microsoft NCSI`

All other requests will respond 404.

Also, VPSS will directly respond to dns requests to `dns.msftncsi.com` with `131.107.255.255`, to ensure Windows passes NCSI.

Please note that `ipv6.msftconnecttest.com` and AAAA records of `dns.msftncsi.com` are NOT specially handled, to prevent some devices which are actually unable to access ipv6 network to believe they can.

For more info, see [this post](https://docs.microsoft.com/en-us/answers/questions/348910/good-idea-add-wwwmsftconnecttestcom-dns-zone-on-in.html) and <a href="https://docs.microsoft.com/en-us/previous-versions/windows/it-pro/windows-vista/cc766017(v=ws.10)">this doc</a>.
