package io.vproxy.vpss.config

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.BoolRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ObjectBuilder

data class Flow(
  var enable: Boolean = false,
  var flow: String = "",
) {
  companion object {
    val rule: Rule<Flow> = ObjectRule { Flow() }
      .put("enable", BoolRule) { enable = it }
      .put("flow", StringRule) { flow = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("enable", enable)
      .put("flow", flow)
      .build()
  }
}
