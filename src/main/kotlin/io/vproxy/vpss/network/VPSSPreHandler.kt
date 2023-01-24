package io.vproxy.vpss.network

import io.vproxy.vpacket.ArpPacket
import io.vproxy.vpss.util.Consts
import io.vproxy.vswitch.PacketBuffer
import io.vproxy.vswitch.PacketFilterHelper
import io.vproxy.vswitch.iface.Iface
import io.vproxy.vswitch.plugin.FilterResult
import io.vproxy.vswitch.plugin.IfaceWatcher
import io.vproxy.vswitch.plugin.PacketFilter

class VPSSPreHandler : PacketFilter, IfaceWatcher {
  @Suppress("DuplicatedCode")
  override fun handle(helper: PacketFilterHelper, pkb: PacketBuffer): FilterResult {
    if (pkb.devin.isDisabled) {
      if (pkb.pkt.packet is ArpPacket) { // allow all arp packets
        pkb.assumeIfaceEnabled = true
      } else if (pkb.ipPkt != null) {
        val ip = pkb.ipPkt!!
        if (ip.src == Consts.vgwIp || ip.dst == Consts.vgwIp) { // allow all packets to/from vgw
          pkb.assumeIfaceEnabled = true
        } else if (pkb.udpPkt != null) {
          val udp = pkb.udpPkt!!
          if (udp.srcPort == 53 || udp.dstPort == 53) { // allow all dns packets
            pkb.assumeIfaceEnabled = true
          }
        }
      }
    }
    return FilterResult.PASS
  }

  override fun ifaceAdded(iface: Iface) {
    iface.addPreHandler(this)
  }

  override fun ifaceRemoved(iface: Iface) {
    iface.removePreHandler(this)
  }
}
