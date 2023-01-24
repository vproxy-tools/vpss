package io.vproxy.vpss.entity

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.BoolRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress

data class NetIP(
  var ip: IP? = null,
  var mac: MacAddress? = null,
  var routing: Boolean = false,

  var system: Boolean = false,
) {
  companion object {
    val rule: Rule<NetIP> = ObjectRule { NetIP() }
      .put("ip", StringRule) { ip = IP.from(it.trim()) }
      .put("mac", StringRule) { mac = MacAddress(it.trim()) }
      .put("routing", BoolRule) { routing = it }
      .put("system", BoolRule) { system = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("ip", ip!!.formatToIPString())
      .put("mac", mac!!.toString())
      .put("routing", routing)
      .put("system", system)
      .build()
  }
}
