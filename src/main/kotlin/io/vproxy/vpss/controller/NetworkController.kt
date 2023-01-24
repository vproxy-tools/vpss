package io.vproxy.vpss.controller

import io.vproxy.base.util.Utils
import io.vproxy.base.util.exception.XException
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ArrayBuilder
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.entity.Net
import io.vproxy.vpss.entity.NetIP
import io.vproxy.vpss.entity.NetRemoteSw
import io.vproxy.vpss.entity.NetRoute
import io.vproxy.vpss.service.NetworkService
import io.vproxy.vpss.service.impl.NetworkServiceImpl
import io.vproxy.vpss.util.ErrorCode
import io.vproxy.vpss.util.VPSSUtils

class NetworkController(app: CoroutineHttp1Server) : BaseController(app) {
  init {
    app.get("/api/networks", handle(::getNetworks))
    app.post("/api/networks/add", handle(::addNetwork))
    app.post("/api/networks/:vni/del", handle(::delNetwork))
    app.post("/api/networks/:vni/ip/add", handle(::addIp))
    app.post("/api/networks/:vni/ip/:ip/del", handle(::delIp))
    app.post("/api/networks/:vni/ip/:ip/toggle-routing", handle(::toggleRouting))
    app.post("/api/networks/:vni/route/add", handle(::addRoute))
    app.post("/api/networks/:vni/route/:route/del", handle(::delRoute))
    app.get("/api/networks/:vni/arp", handle(::getArp))
    app.post("/api/networks/:vni/arp/add", handle(::addArp))
    app.post("/api/networks/:vni/arp/:mac/del", handle(::delArp))
    app.post("/api/networks/:vni/remote/apply", handle(::applyRemoteSw))
    app.post("/api/networks/:vni/remote/disable", handle(::disableRemoteSw))
    app.post("/api/networks/:vni/toggle-allow-ipv6", handle(::toggleAllowIpv6))
  }

  private val networkService: NetworkService = NetworkServiceImpl

  // return [Net]
  private fun getNetworks(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    val ret = ArrayBuilder()
    for (n in networkService.getNetworks()) {
      ret.addInst(n.toJson())
    }
    return ret.build()
  }

  private fun addNetwork(ctx: RoutingContext): JSON.Instance<*> {
    val body = JSON.deserialize(ctx.req.body().toString(), Net.rule)
    if (body.vni == 0) {
      throw XException(ErrorCode.badArgsMissingNetworkVni)
    }
    if (body.v4net == null) {
      throw XException(ErrorCode.badArgsMissingNetworkV4net)
    }
    val ret = networkService.addNetwork(body)
    return ret.toJson()
  }

  private fun getVni(ctx: RoutingContext): Int {
    val vniStr = ctx.param("vni")
    if (Utils.isInteger(vniStr)) {
      val n = Integer.parseInt(vniStr)
      if (n > 0) {
        return n
      }
    }
    throw XException(ErrorCode.badArgsInvalidVni)
  }

  private fun delNetwork(ctx: RoutingContext): JSON.Instance<*>? {
    val vni = getVni(ctx)
    networkService.delNetwork(vni)
    return null
  }

  // body: NetIP
  // return NetIP
  private fun addIp(ctx: RoutingContext): JSON.Instance<*> {
    val vni = getVni(ctx)
    val body = JSON.deserialize(ctx.req.body().toString(), NetIP.rule)
    if (body.ip == null) {
      throw XException(ErrorCode.badArgsMissingIpIp)
    }
    if (body.mac == null) {
      throw XException(ErrorCode.badArgsMissingIpMac)
    }
    val ret = networkService.addIp(vni, body)
    return ret.toJson()
  }

  private fun delIp(ctx: RoutingContext): JSON.Instance<*>? {
    val vni = getVni(ctx)
    val ip = try {
      IP.from(ctx.param("ip"))
    } catch (e: RuntimeException) {
      throw XException(ErrorCode.badArgsInvalidIp)
    }
    networkService.delIp(vni, ip)
    return null
  }

  // return { routing: bool }
  private fun toggleRouting(ctx: RoutingContext): JSON.Instance<*> {
    val vni = getVni(ctx)
    val ip = try {
      IP.from(ctx.param("ip"))
    } catch (e: RuntimeException) {
      throw XException(ErrorCode.badArgsInvalidIp)
    }
    val ret = networkService.toggleIpRouting(vni, ip)
    return ObjectBuilder()
      .put("routing", ret)
      .build()
  }

  private fun addRoute(ctx: RoutingContext): JSON.Instance<*> {
    val vni = getVni(ctx)
    val body = JSON.deserialize(ctx.req.body().toString(), NetRoute.rule)
    if (body.name == null) {
      throw XException(ErrorCode.badArgsMissingRouteName)
    }
    if (body.type == null) {
      throw XException(ErrorCode.badArgsMissingRouteType)
    }
    val ret = networkService.addRoute(vni, body)
    return ret.toJson()
  }

  private fun delRoute(ctx: RoutingContext): JSON.Instance<*>? {
    val vni = getVni(ctx)
    val name = ctx.param("route")
    networkService.delRoute(vni, name)
    return null
  }

  private fun getArp(ctx: RoutingContext): JSON.Instance<*> {
    val vni = getVni(ctx)
    val ls = networkService.getArpEntries(vni)
    val ret = ArrayBuilder()
    for (e in ls) {
      ret.addObject {
        if (e.iface == null) {
          put("iface", null)
        } else {
          put("iface", VPSSUtils.formatIfaceName(e.iface))
        }
        put("mac", e.mac.toString())
        if (e.ip == null) {
          put("ip", null)
        } else {
          put("ip", e.ip.formatToIPString())
        }
        put("macTTL", if (e.macTTL == (-1).toLong()) -1 else e.macTTL / 1000)
        put("arpTTL", if (e.arpTTL == (-1).toLong()) -1 else e.arpTTL / 1000)
      }
    }
    return ret.build()
  }

  private data class ArpEntry(
    var iface: String? = null,
    var mac: MacAddress? = null,
    var ip: IP? = null,
  )

  private val arpEntryRule: Rule<ArpEntry> = ObjectRule { ArpEntry() }
    .put("iface", StringRule) { iface = it }
    .put("mac", StringRule) { mac = MacAddress(it) }
    .put("ip", StringRule) { ip = IP.from(it) }

  private fun addArp(ctx: RoutingContext): JSON.Instance<*>? {
    val vni = getVni(ctx)
    val body = JSON.deserialize(ctx.req.body().toString(), arpEntryRule)
    if (body.mac == null) {
      throw XException(ErrorCode.badArgsMissingArpMac)
    }
    if (body.iface == null && body.ip == null) {
      throw XException(ErrorCode.badArgsMissingArpIfaceOrIp)
    }
    networkService.addArpEntry(vni, body.iface, body.mac!!, body.ip)
    return null
  }

  private fun delArp(ctx: RoutingContext): JSON.Instance<*>? {
    val vni = getVni(ctx)
    val macStr = ctx.param("mac")
    val mac = try {
      MacAddress(macStr)
    } catch (e: Exception) {
      throw XException(ErrorCode.badArgsInvalidMacInArp)
    }
    networkService.delArpEntry(vni, mac)
    return null
  }

  private fun applyRemoteSw(ctx: RoutingContext): JSON.Instance<*> {
    val vni = getVni(ctx)
    val body = JSON.deserialize(ctx.req.body().toString(), NetRemoteSw.rule)
    if (body.ipport == null) {
      throw XException(ErrorCode.badArgsMissingIPPort)
    }
    if (body.username == null) {
      throw XException(ErrorCode.badArgsMissingUsername)
    }
    if (body.password == null) {
      throw XException(ErrorCode.badArgsMissingPassword)
    }
    val ret = networkService.applyRemoteSw(vni, body)
    return ret.toJson()
  }

  private fun disableRemoteSw(ctx: RoutingContext): JSON.Instance<*>? {
    val vni = getVni(ctx)
    networkService.disableRemoteSw(vni)
    return null
  }

  // return { allowIpv6: true/false }
  private fun toggleAllowIpv6(ctx: RoutingContext): JSON.Instance<*> {
    val vni = getVni(ctx)
    val ret = networkService.toggleAllowIpv6(vni)
    return ObjectBuilder()
      .put("allowIpv6", ret)
      .build()
  }
}
