package io.vproxy.vpss.entity

import io.vproxy.base.util.Network
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.*
import io.vproxy.dep.vjson.util.ObjectBuilder

data class Net(
  var vni: Int = 0,
  var v4net: Network? = null,
  var v6net: Network? = null,
  var ips: MutableList<NetIP> = ArrayList(),
  var routes: MutableList<NetRoute> = ArrayList(),
  var remote: NetRemoteSw = NetRemoteSw(),
  var allowIpv6: Boolean = false,

  var system: Boolean = false,
) {
  companion object {
    val rule: Rule<Net> = ObjectRule { Net() }
      .put("vni", IntRule) { vni = it }
      .put("v4net", StringRule) { v4net = Network.from(it.trim()) }
      .put("v6net", StringRule) { v6net = Network.from(it.trim()) }
      .put("ips", ArrayRule({ ArrayList<NetIP>() }, { add(it) }, NetIP.rule)) { ips = it }
      .put("routes", ArrayRule({ ArrayList<NetRoute>() }, { add(it) }, NetRoute.rule)) { routes = it }
      .put("remote", NetRemoteSw.rule) { remote = it }
      .put("allowIpv6", BoolRule) { allowIpv6 = it }
      .put("system", BoolRule) { system = it }
  }

  fun toJson(): JSON.Object {
    val rt = ObjectBuilder()
      .put("vni", vni)
      .put("v4net", v4net!!.toString())
    if (v6net != null) {
      rt.put("v6net", v6net!!.toString())
    }
    rt.putArray("ips") { ips.forEach { addInst(it.toJson()) } }
      .putArray("routes") { routes.forEach { addInst(it.toJson()) } }
      .putInst("remote", remote.toJson())
      .put("allowIpv6", allowIpv6)
      .put("system", system)
    return rt.build()
  }
}
