package io.vproxy.vpss.network

import io.vproxy.base.util.Consts
import io.vproxy.vpacket.IcmpPacket
import io.vproxy.vswitch.PacketBuffer
import io.vproxy.vswitch.PacketFilterHelper
import io.vproxy.vswitch.iface.Iface
import io.vproxy.vswitch.plugin.FilterResult
import io.vproxy.vswitch.plugin.IfaceWatcher
import io.vproxy.vswitch.plugin.PacketFilter

class DHCPOnlyPacketFilter : PacketFilter, IfaceWatcher {
  override fun handle(helper: PacketFilterHelper, pkb: PacketBuffer): FilterResult {
    if (pkb.ipPkt == null) {
      return FilterResult.PASS
    }
    if (pkb.ipPkt.packet is IcmpPacket) {
      val icmp = pkb.ipPkt.packet as IcmpPacket
      if (icmp.type == Consts.ICMP_PROTOCOL_TYPE_ECHO_REQ || icmp.type == Consts.ICMP_PROTOCOL_TYPE_ECHO_RESP ||
        icmp.type == Consts.ICMPv6_PROTOCOL_TYPE_ECHO_REQ || icmp.type == Consts.ICMPv6_PROTOCOL_TYPE_ECHO_RESP
      ) {
        return FilterResult.DROP
      }
      return FilterResult.PASS
    }
    if (pkb.udpPkt == null) {
      return FilterResult.DROP
    }
    val udp = pkb.udpPkt
    if ((udp.srcPort == 67 && udp.dstPort == 68) || (udp.srcPort == 68 && udp.dstPort == 67)) {
      return FilterResult.PASS
    }
    if ((udp.srcPort == 546 && udp.dstPort == 547) || (udp.srcPort == 547 && udp.dstPort == 546)) {
      return FilterResult.PASS
    }
    return FilterResult.DROP
  }

  override fun ifaceAdded(iface: Iface) {
    iface.addIngressFilter(this)
  }

  override fun ifaceRemoved(iface: Iface) {
    iface.removeIngressFilter(this)
  }
}
