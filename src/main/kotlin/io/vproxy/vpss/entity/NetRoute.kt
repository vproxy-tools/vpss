package io.vproxy.vpss.entity

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.BoolRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.base.util.Network

data class NetRoute(
  var name: String? = null,
  var target: Network? = null,
  var type: NetRouteType? = null,
  var argument: String? = null,

  var system: Boolean = false,
) {
  companion object {
    val rule: Rule<NetRoute> = ObjectRule { NetRoute() }
      .put("name", StringRule) { name = it.trim() }
      .put("target", StringRule) { target = Network.from(it.trim()) }
      .put("type", StringRule) { type = NetRouteType.valueOf(it.trim()) }
      .put("argument", StringRule) { argument = it.trim() }
      .put("system", BoolRule) { system = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("name", name)
      .put("target", target!!.toString())
      .put("type", type!!.name)
      .put("argument", argument)
      .put("system", system)
      .build()
  }
}
