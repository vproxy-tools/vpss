package io.vproxy.vpss.util

import io.vproxy.base.util.Network
import io.vproxy.base.util.exception.NotFoundException
import io.vproxy.base.util.exception.XException
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPv4
import io.vproxy.vfd.MacAddress
import io.vproxy.vpacket.AbstractIpPacket
import io.vproxy.vpacket.EthernetPacket
import io.vproxy.vpss.entity.WBList
import io.vproxy.vpss.entity.WBType
import io.vproxy.vswitch.RouteTable
import io.vproxy.vswitch.RouteTable.RouteRule

class WBListHolder {
  private val macOnlyWhite: MutableMap<MacAddress, WBList> = HashMap()
  private val macWhiteListMap: MutableMap<MacAddress, RouteTable> = HashMap()
  private val anySourceWhiteList = RouteTable()

  private val macOnlyBlack: MutableMap<MacAddress, WBList> = HashMap()
  private val macBlackListMap: MutableMap<MacAddress, RouteTable> = HashMap()
  private val anySourceBlackList = RouteTable()

  private class WBListRouteRule(name: String, network: Network, val wblist: WBList) : RouteRule(name, network, 0)

  private fun lookup(
    pkt: EthernetPacket,
    macOnly: Map<MacAddress, WBList>,
    macListMap: Map<MacAddress, RouteTable>,
    anySource: RouteTable
  ): WBList? {
    val src = pkt.src
    var macOnlyRule = macOnly[src]
    if (macOnlyRule != null) {
      return macOnlyRule
    }

    val dst = pkt.dst
    macOnlyRule = macOnly[dst]
    if (macOnlyRule != null) {
      return macOnlyRule
    }

    if (pkt.packet !is AbstractIpPacket) {
      return null
    }

    val ipPkt = pkt.packet as AbstractIpPacket
    var macIPRT = macListMap[src]
    if (macIPRT != null) {
      val route = macIPRT.lookup(ipPkt.dst)
      if (route != null) {
        return (route as WBListRouteRule).wblist
      }
    }

    macIPRT = macListMap[dst]
    if (macIPRT != null) {
      val route = macIPRT.lookup(ipPkt.src)
      if (route != null) {
        return (route as WBListRouteRule).wblist
      }
    }

    var ipRule = anySource.lookup(ipPkt.src)
    if (ipRule != null) {
      return (ipRule as WBListRouteRule).wblist
    }

    ipRule = anySource.lookup(ipPkt.dst)
    if (ipRule != null) {
      return (ipRule as WBListRouteRule).wblist
    }

    return null
  }

  fun lookupWhite(pkt: EthernetPacket): WBList? {
    return lookup(pkt, macOnlyWhite, macWhiteListMap, anySourceWhiteList)
  }

  fun lookupBlack(pkt: EthernetPacket): WBList? {
    return lookup(pkt, macOnlyBlack, macBlackListMap, anySourceBlackList)
  }

  private fun register(
    wblist: WBList,
    macOnly: MutableMap<MacAddress, WBList>,
    macListMap: MutableMap<MacAddress, RouteTable>,
    anySource: RouteTable
  ) {
    if (wblist.target == "*") {
      val mac = MacAddress(wblist.sourceMac)
      if (macOnly.containsKey(mac)) {
        throw XException(ErrorCode.conflictWBListMac)
      }
      macOnly[mac] = wblist
      return
    }

    var network = wblist.target!!
    if (!network.contains("/")) {
      val ip = IP.from(network)
      network += if (ip is IPv4) "/32" else "/128"
    }
    val net = Network.from(network)
    if (wblist.sourceMac == "*") {
      addRuleCheck(anySource, net)
      anySource.addRule(WBListRouteRule(wblist.name!!, net, wblist))
    } else {
      val mac = MacAddress(wblist.sourceMac)
      val routeTable = if (macListMap.containsKey(mac)) {
        macListMap[mac]!!
      } else {
        val rt = RouteTable()
        macListMap[mac] = rt
        rt
      }
      addRuleCheck(routeTable, net)
      routeTable.addRule(WBListRouteRule(wblist.name!!, net, wblist))
    }
  }

  private fun addRuleCheck(rt: RouteTable, rule: Network) {
    for (r in rt.rules) {
      if (r.rule == rule) {
        throw XException(ErrorCode.conflictWBListIp)
      }
    }
  }

  fun register(wblist: WBList) {
    if (wblist.type == WBType.white) {
      register(wblist, macOnlyWhite, macWhiteListMap, anySourceWhiteList)
    } else {
      register(wblist, macOnlyBlack, macBlackListMap, anySourceBlackList)
    }
  }

  fun deregister(name: String) {
    try {
      anySourceWhiteList.delRule(name)
    } catch (_: NotFoundException) {
    }
    try {
      anySourceBlackList.delRule(name)
    } catch (_: NotFoundException) {
    }

    val macToDel = HashSet<MacAddress>()
    for ((mac, rt) in macWhiteListMap.entries) {
      try {
        rt.delRule(name)
      } catch (_: NotFoundException) {
      }
      if (rt.rules.isEmpty()) {
        macToDel.add(mac)
      }
    }
    for (mac in macToDel) {
      macWhiteListMap.remove(mac)
    }

    macToDel.clear()
    for ((mac, rt) in macBlackListMap.entries) {
      try {
        rt.delRule(name)
      } catch (_: NotFoundException) {
      }
      if (rt.rules.isEmpty()) {
        macToDel.add(mac)
      }
    }
    for (mac in macToDel) {
      macBlackListMap.remove(mac)
    }

    macToDel.clear()
    for ((mac, wblist) in macOnlyWhite) {
      if (wblist.name == name) {
        macToDel.add(mac)
      }
    }
    for (mac in macToDel) {
      macOnlyWhite.remove(mac)
    }

    macToDel.clear()
    for ((mac, wblist) in macOnlyBlack) {
      if (wblist.name == name) {
        macToDel.add(mac)
      }
    }
    for (mac in macToDel) {
      macOnlyBlack.remove(mac)
    }
  }
}
