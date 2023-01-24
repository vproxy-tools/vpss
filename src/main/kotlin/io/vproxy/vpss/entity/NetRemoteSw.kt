package io.vproxy.vpss.entity

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.BoolRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.vfd.IPPort

data class NetRemoteSw(
  var enable: Boolean = false,
  var ipport: IPPort? = null,
  var username: String? = null,
  var password: String? = null,
  var allowDhcp: Boolean = false,
) {
  companion object {
    val rule: Rule<NetRemoteSw> = ObjectRule { NetRemoteSw() }
      .put("enable", BoolRule) { enable = it }
      .put("ipport", StringRule) { ipport = IPPort(it) }
      .put("username", StringRule) { username = it }
      .put("password", StringRule) { password = it }
      .put("allowDhcp", BoolRule) { allowDhcp = it }
  }

  fun toJson(): JSON.Object {
    if (!enable) {
      return ObjectBuilder()
        .put("enable", false)
        .build()
    }
    return ObjectBuilder()
      .put("enable", enable)
      .put("ipport", ipport!!.formatToIPPortString())
      .put("username", username)
      .put("password", password)
      .put("allowDhcp", allowDhcp)
      .build()
  }
}
