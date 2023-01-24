package io.vproxy.vpss.service

import io.vproxy.app.app.cmd.handle.resource.ArpHandle
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.entity.Net
import io.vproxy.vpss.entity.NetIP
import io.vproxy.vpss.entity.NetRemoteSw
import io.vproxy.vpss.entity.NetRoute

interface NetworkService {
  fun getNetworks(): List<Net>
  fun addNetwork(net: Net): Net
  fun delNetwork(vni: Int)
  fun addIp(vni: Int, netIp: NetIP): NetIP
  fun delIp(vni: Int, ip: IP)
  fun toggleIpRouting(vni: Int, ip: IP): Boolean
  fun addRoute(vni: Int, netRoute: NetRoute): NetRoute
  fun delRoute(vni: Int, name: String)
  fun getArpEntries(vni: Int): Collection<ArpHandle.ArpEntry>
  fun delArpEntry(vni: Int, mac: MacAddress)
  fun addArpEntry(vni: Int, iface: String?, mac: MacAddress, ip: IP?)
  fun applyRemoteSw(vni: Int, remoteSw: NetRemoteSw): NetRemoteSw
  fun disableRemoteSw(vni: Int)
  fun toggleAllowIpv6(vni: Int): Boolean
}
