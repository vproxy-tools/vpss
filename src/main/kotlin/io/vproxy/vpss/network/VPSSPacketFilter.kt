package io.vproxy.vpss.network

import io.vproxy.base.dns.*
import io.vproxy.base.dns.rdata.A
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.ratelimit.RateLimiter
import io.vproxy.base.util.ratelimit.StatisticsRateLimiter
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPv4
import io.vproxy.vpacket.ArpPacket
import io.vproxy.vpacket.Ipv4Packet
import io.vproxy.vpacket.Ipv6Packet
import io.vproxy.vpacket.PacketBytes
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.WBType
import io.vproxy.vpss.service.impl.FlowServiceImpl
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global
import io.vproxy.vswitch.PacketBuffer
import io.vproxy.vswitch.PacketFilterHelper
import io.vproxy.vswitch.RouteTable
import io.vproxy.vswitch.iface.Iface
import io.vproxy.vswitch.iface.SubIface
import io.vproxy.vswitch.plugin.FilterResult

class VPSSPacketFilter : VPSSPacketFilterBase() {
  companion object {
    private val vgwIp: IPv4 = Consts.vgwIp
    private val dnsMsftncsiCom: IPv4 = IP.fromIPv4("131.107.255.255")
  }

  override fun handleIngress(helper: PacketFilterHelper, pkb: PacketBuffer): FilterResult {
    // direct respond dns requests for (*.)vgw.special.vproxy.io
    val specialDnsResult = tryToHandleVGWDnsRequest(helper, pkb)
    if (specialDnsResult != null) {
      return specialDnsResult
    }

    val ret = super.handleIngress(helper, pkb)
    if (ret != FilterResult.PASS) {
      return ret
    }

    var devin: Iface = pkb.devin
    while (true) {
      val statisticsObject = devin.getUserData(Consts.inputStatisticsKey)
      if (statisticsObject != null) {
        val statistics = statisticsObject as StatisticsRateLimiter
        helper.ratelimitByBitsPerSecond(pkb, statistics)
      }
      if (devin is SubIface) {
        devin = devin.parentIface
      } else {
        break
      }
    }

    return FilterResult.PASS
  }

  private fun tryToHandleVGWDnsRequest(helper: PacketFilterHelper, pkb: PacketBuffer): FilterResult? {
    if (pkb.udpPkt == null || pkb.udpPkt.dstPort != 53) return null
    pkb.ensurePartialPacketParsed()
    val data = pkb.udpPkt.data.getRawPacket(0)
    val dnsPackets = try {
      Formatter.parsePackets(data)
    } catch (e: InvalidDNSPacketException) {
      return null
    }
    if (dnsPackets.size != 1) return null
    val dns = dnsPackets[0]
    if (dns.isResponse) return null
    if (dns.questions.size != 1) return null
    val question = dns.questions[0]
    if (question.qclass != DNSClass.IN ||
      (question.qtype != DNSType.A && question.qtype != DNSType.ANY) ||
      (!canHandleDomain(question.qname))
    ) return null

    if (question.qname != "vgw.special.vproxy.io." && question.qname != "vgw.special.vproxy.io") {
      // need to check whether destination has special route
      // if so, the packet should be handled by others
      val route = pkb.network.routeTable.lookup(pkb.ipPkt.dst)
      if (route != null && !route.isLocalDirect(pkb.vni)) {
        // should be redirected by the switch
        Logger.alert("dns request for ${question.qname} is redirected: $route")
        run_mod_dl_dst_to_synthetic_ip(helper, pkb)
        return FilterResult.PASS
      }
      // fallthrough
    }

    val resolvedIp = getIpForDomain(pkb, question.qname) ?: return FilterResult.DROP

    dns.isResponse = true
    dns.rcode = DNSPacket.RCode.NoError
    dns.tc = false
    dns.ra = true
    dns.answers.clear()
    dns.nameServers.clear()
    dns.additionalResources.clear()

    val r = DNSResource()
    r.name = question.qname
    r.clazz = DNSClass.IN
    r.ttl = 600

    val a = A()
    a.address = resolvedIp

    r.type = DNSType.A
    r.rdata = a

    dns.answers.add(r)

    val dnsData = dns.toByteArray()

    pkb.udpPkt.run {
      this.data = PacketBytes(dnsData)
      this.length = dnsData.length() + 8
      val dstPort = this.dstPort
      this.dstPort = this.srcPort
      this.srcPort = dstPort
    }
    pkb.ipPkt.run {
      if (this is Ipv4Packet) {
        val dst = this.dst
        this.dst = this.src
        this.src = dst
      } else {
        val dst = (this as Ipv6Packet).dst
        this.dst = this.src
        this.src = dst
      }
    }
    pkb.pkt.run {
      val dst = this.dst
      this.dst = this.src
      this.src = dst
    }

    Logger.trace(
      LogType.ALERT,
      "directly respond dns request for ${question.qname} on nic ${pkb.devin.name()}: ${resolvedIp.formatToIPString()}"
    )

    return FilterResult.TX
  }

  private fun canHandleDomain(domain0: String): Boolean {
    var domain = domain0
    if (!domain.endsWith(".")) {
      domain = "$domain."
    }
    return domain == "vgw.special.vproxy.io." ||
      domain == "wsagent.vgw.special.vproxy.io." ||
      domain == "ssh.vgw.special.vproxy.io." ||
      domain == "web.vgw.special.vproxy.io." ||
      domain == "www.msftconnecttest.com." ||
      domain == "dns.msftncsi.com." ||
      domain == "www.msftncsi.com"
  }

  private fun getIpForDomain(pkb: PacketBuffer, domain0: String): IPv4? {
    var domain = domain0
    if (!domain.endsWith(".")) {
      domain = "$domain."
    }
    return when (domain) {
      "wsagent.vgw.special.vproxy.io." -> {
        val arpEntry = pkb.network.arpTable.lookupByMac(Config.get().config.vpwsAgentMac!!)?.find { true }
        if (arpEntry != null && arpEntry.ip is IPv4) arpEntry.ip as IPv4 else {
          Logger.warn(LogType.ALERT, "unable to find ipv4 for wsagent ${Config.get().config.vpwsAgentMac}")
          null
        }
      }
      "ssh.vgw.special.vproxy.io." -> {
        val arpEntry = pkb.network.arpTable.lookupByMac(Config.get().config.virtualSSHMac!!)?.find { true }
        if (arpEntry != null && arpEntry.ip is IPv4) arpEntry.ip as IPv4 else {
          Logger.warn(LogType.ALERT, "unable to find ipv4 for ssh ${Config.get().config.virtualSSHMac}")
          null
        }
      }
      "web.vgw.special.vproxy.io." -> { // same as ssh
        val arpEntry = pkb.network.arpTable.lookupByMac(Config.get().config.virtualSSHMac!!)?.find { true }
        if (arpEntry != null && arpEntry.ip is IPv4) arpEntry.ip as IPv4 else {
          Logger.warn(LogType.ALERT, "unable to find ipv4 for web ${Config.get().config.virtualSSHMac}")
          null
        }
      }
      "dns.msftncsi.com." -> dnsMsftncsiCom
      else -> {
        vgwIp
      }
    }
  }

  @Suppress("DuplicatedCode")
  override fun handleEgress(helper: PacketFilterHelper, pkb: PacketBuffer): FilterResult {
    if (pkb.devout.isDisabled) {
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
      if (!pkb.assumeIfaceEnabled) {
        return FilterResult.PASS // will then be dropped by switch
      }
    }

    var devout: Iface = pkb.devout
    while (true) {
      val statisticsObject = devout.getUserData(Consts.outputStatisticsKey)
      if (statisticsObject != null) {
        val statistics = statisticsObject as StatisticsRateLimiter
        helper.ratelimitByBitsPerSecond(pkb, statistics)
      }
      if (devout is SubIface) {
        devout = devout.parentIface
      } else {
        break
      }
    }

    return FilterResult.PASS
  }

  override fun predicate_drop_ipv6(helper: PacketFilterHelper, pkb: PacketBuffer): Boolean {
    val o = pkb.network.getUserData(Consts.allowIpv6Key) ?: return true
    return !(o as Boolean)
  }

  override fun predicate_dl_src_is_vpws_agent(helper: PacketFilterHelper, pkb: PacketBuffer): Boolean {
    return pkb.pkt.src == Config.get().config.vpwsAgentMac
  }

  override fun run_mod_dl_dst_to_vpws_agent(helper: PacketFilterHelper, pkb: PacketBuffer) {
    pkb.pkt.dst = Config.get().config.vpwsAgentMac
  }

  override fun run_mod_dl_dst_to_virtual_ssh(helper: PacketFilterHelper, pkb: PacketBuffer) {
    pkb.pkt.dst = Config.get().config.virtualSSHMac
  }

  override fun run_mod_dl_dst_to_virtual(helper: PacketFilterHelper, pkb: PacketBuffer) {
    pkb.pkt.dst = Config.get().config.virtualMac
  }

  override fun predicate_disallow_dhcp(helper: PacketFilterHelper, pkb: PacketBuffer): Boolean {
    val allowDhcp = pkb.devin.getUserData(Consts.allowDhcpKey)
    return allowDhcp != null && !(allowDhcp as Boolean)
  }

  override fun predicate_whitelist(helper: PacketFilterHelper, pkb: PacketBuffer): Boolean {
    val rule = Global.wblistHolder.lookupWhite(pkb.pkt)
      ?: return false
    return rule.type == WBType.white
  }

  override fun predicate_blacklist(helper: PacketFilterHelper, pkb: PacketBuffer): Boolean {
    val rule = Global.wblistHolder.lookupBlack(pkb.pkt)
      ?: return false
    return rule.type == WBType.black
  }

  override fun predicate_requires_ratelimit(helper: PacketFilterHelper, pkb: PacketBuffer): Boolean {
    val rl = Global.ratelimitHolder.lookup(pkb.pkt)
    return if (rl == null) {
      false
    } else {
      pkb.putUserData(Consts.ratelimitKey, rl)
      true
    }
  }

  override fun invoke_ratelimit(helper: PacketFilterHelper, pkb: PacketBuffer): FilterResult {
    if (pkb.getUserData(Consts.ratelimitKey) == null) {
      if (!predicate_requires_ratelimit(helper, pkb)) {
        return FilterResult.PASS
      }
    }
    return if (helper.ratelimitByBitsPerSecond(pkb, pkb.getUserData(Consts.ratelimitKey) as RateLimiter)) {
      FilterResult.PASS
    } else {
      FilterResult.DROP
    }
  }

  override fun predicate_match_non_local_route(helper: PacketFilterHelper, pkb: PacketBuffer): Boolean {
    val dstIp = pkb.ipPkt.dst
    val rule = pkb.network.routeTable.lookup(dstIp) ?: return false
    return if (rule.isLocalDirect(pkb.vni)) {
      false
    } else {
      pkb.putUserData(Consts.runRouteKey, rule)
      true
    }
  }

  override fun run_mod_dl_dst_to_synthetic_ip(helper: PacketFilterHelper, pkb: PacketBuffer) {
    if (pkb.getUserData(Consts.runRouteKey) == null) {
      if (!predicate_match_non_local_route(helper, pkb)) {
        return
      }
    }
    val rule = pkb.getUserData(Consts.runRouteKey) as RouteTable.RouteRule
    if (rule.ip != null) { // is gateway route, try to use the gateway mac for this packet
      val gwMac = pkb.network.arpTable.lookup(rule.ip)
      if (gwMac != null) {
        pkb.pkt.dst = gwMac
        return
      }
      // gateway mac is not recorded
      // fallthrough, send the packet to a switch local ip, then the switch will send arp/ndp for the gw ip
    }
    val ipmac = pkb.network.ips.findAnyIPForRouting(pkb.ipPkt is Ipv6Packet) ?: return
    pkb.pkt.dst = ipmac.mac
  }

  override fun invoke_run_custom_flow(helper: PacketFilterHelper, pkb: PacketBuffer): FilterResult {
    val pktFilter = FlowServiceImpl.getCurrentFilter()
      ?: return FilterResult.PASS
    return pktFilter.handle(helper, pkb)
  }
}
