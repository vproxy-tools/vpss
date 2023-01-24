package io.vproxy.vpss.entity

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.BoolRule
import io.vproxy.dep.vjson.deserializer.rule.IntRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.util.ObjectBuilder

data class InterfaceVLan(
  var remoteVLan: Int = 0,
  var enable: Boolean = false,
  var allowDhcp: Boolean = false,
  var localVni: Int = 0,
) {
  companion object {
    val rule: Rule<InterfaceVLan> = ObjectRule { InterfaceVLan() }
      .put("remoteVLan", IntRule) { remoteVLan = it }
      .put("enable", BoolRule) { enable = it }
      .put("allowDhcp", BoolRule) { allowDhcp = it }
      .put("localVni", IntRule) { localVni = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("remoteVLan", remoteVLan)
      .put("enable", enable)
      .put("allowDhcp", allowDhcp)
      .put("localVni", localVni)
      .build()
  }
}
