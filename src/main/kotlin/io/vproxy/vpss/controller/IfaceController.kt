package io.vproxy.vpss.controller

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ArrayBuilder
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.base.util.Utils
import io.vproxy.base.util.exception.XException
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.Interface
import io.vproxy.vpss.entity.InterfaceVLan
import io.vproxy.vpss.service.InterfaceService
import io.vproxy.vpss.service.impl.InterfaceServiceImpl
import io.vproxy.vpss.util.ErrorCode

class IfaceController(app: CoroutineHttp1Server) : BaseController(app) {
  init {
    app.get("/api/ifaces", handle(::getIfaces))
    app.post("/api/ifaces/add", handle(::manageIface))
    app.post("/api/ifaces/:iface/del", handle(::unmanageIface))
    app.post("/api/ifaces/:iface/toggle-enable", handle(::toggleEnableDisableIface))
    app.post("/api/ifaces/:iface/toggle-allow-dhcp", handle(::toggleAllowDisallowDhcpIface))

    app.post("/api/vlan/:iface/add", handle(::joinVLan))
    app.post("/api/vlan/:iface/:vlan/del", handle(::leaveVLan))
    app.post("/api/vlan/:iface/:vlan/toggle-enable", handle(::toggleEnableDisableVLan))
    app.post("/api/vlan/:iface/:vlan/toggle-allow-dhcp", handle(::toggleAllowDisallowDhcpVLan))
  }

  private val interfaceService: InterfaceService = InterfaceServiceImpl

  // return: { managed: [Interface], unmanaged: [Interface], tombstone: [Interface] }
  private fun getIfaces(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    val tup = interfaceService.getIfaces()
    val managed = ArrayBuilder()
    val unmanaged = ArrayBuilder()
    val tombstone = ArrayBuilder()
    for (i in tup.left) {
      managed.addInst(i.toJson())
    }
    for (i in tup.right) {
      unmanaged.addInst(i.toJson())
    }
    for (i in Config.get().nicTombstone) {
      tombstone.addInst(i.toJson())
    }
    return ObjectBuilder()
      .putInst("managed", managed.build())
      .putInst("unmanaged", unmanaged.build())
      .putInst("tombstone", tombstone.build())
      .build()
  }

  // body: Interface(name, vni)
  // return Interface
  private fun manageIface(ctx: RoutingContext): JSON.Instance<*> {
    val body = JSON.deserialize(ctx.req.body().toString(), Interface.rule)
    if (body.name == null) {
      throw XException(ErrorCode.badArgsMissingIfaceName)
    }
    if (body.vni == 0) {
      throw XException(ErrorCode.badArgsMissingIfaceVni)
    }
    val ret = interfaceService.manageIface(body)
    return ret.toJson()
  }

  // return null
  private fun unmanageIface(ctx: RoutingContext): JSON.Instance<*>? {
    val name = ctx.param("iface")

    interfaceService.unmanageIface(name)
    return null
  }

  // return { enable: bool }
  private fun toggleEnableDisableIface(ctx: RoutingContext): JSON.Instance<*> {
    val name = ctx.param("iface")
    val ret = interfaceService.toggleIfaceEnableDisable(name)
    return ObjectBuilder()
      .put("enable", ret)
      .build()
  }

  // return { allowDhcp: bool }
  private fun toggleAllowDisallowDhcpIface(ctx: RoutingContext): JSON.Instance<*> {
    val name = ctx.param("iface")
    val ret = interfaceService.toggleIfaceAllowDisallowDhcp(name)
    return ObjectBuilder()
      .put("allowDhcp", ret)
      .build()
  }

  // body: InterfaceVLan(remoteVLan, localVni)
  // return InterfaceVLan
  private fun joinVLan(ctx: RoutingContext): JSON.Instance<*> {
    val vlan = JSON.deserialize(ctx.req.body().toString(), InterfaceVLan.rule)
    if (vlan.remoteVLan == 0) {
      throw XException(ErrorCode.badArgsMissingRemoteVLan)
    }
    if (vlan.localVni == 0) {
      throw XException(ErrorCode.badArgsMissingLocalVni)
    }
    if (vlan.remoteVLan < 1) {
      throw XException(ErrorCode.badArgsInvalidRemoteVLan)
    }
    if (vlan.localVni < 1) {
      throw XException(ErrorCode.badArgsInvalidLocalVni)
    }

    val ifaceName = ctx.param("iface")
    val ret = interfaceService.joinVLan(ifaceName, vlan)
    return ret.toJson()
  }

  private fun getVLanOrFail(ctx: RoutingContext): Int {
    val vlanStr = ctx.param("vlan")
    if (!Utils.isInteger(vlanStr)) {
      throw XException("$vlanStr is not a valid integer")
    }
    return Integer.parseInt(vlanStr)
  }

  // return null
  private fun leaveVLan(ctx: RoutingContext): JSON.Instance<*>? {
    val ifaceName = ctx.param("iface")
    val vlan = getVLanOrFail(ctx)

    interfaceService.leaveVLan(ifaceName, vlan)
    return null
  }

  // return { enable: bool }
  private fun toggleEnableDisableVLan(ctx: RoutingContext): JSON.Instance<*> {
    val name = ctx.param("iface")
    val vlan = getVLanOrFail(ctx)
    val ret = interfaceService.toggleVLanEnableDisable(name, vlan)
    return ObjectBuilder()
      .put("enable", ret)
      .build()
  }

  // return { allowDhcp: bool }
  private fun toggleAllowDisallowDhcpVLan(ctx: RoutingContext): JSON.Instance<*> {
    val name = ctx.param("iface")
    val vlan = getVLanOrFail(ctx)
    val ret = interfaceService.toggleVLanAllowDisallowDhcp(name, vlan)
    return ObjectBuilder()
      .put("allowDhcp", ret)
      .build()
  }
}
