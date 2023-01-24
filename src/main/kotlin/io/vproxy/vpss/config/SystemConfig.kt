package io.vproxy.vpss.config

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.util.ObjectBuilder

data class SystemConfig(
  var todo: Any? = null,
) {
  companion object {
    val rule: Rule<SystemConfig> = ObjectRule { SystemConfig() }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .build()
  }
}
