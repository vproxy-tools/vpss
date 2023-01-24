package io.vproxy.vpss.entity

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ObjectBuilder

data class WBList(
  var name: String? = null,
  var sourceMac: String? = null,
  var target: String? = null,
  var type: WBType? = null,
) {
  companion object {
    val rule: Rule<WBList> = ObjectRule { WBList() }
      .put("name", StringRule) { name = it.trim() }
      .put("sourceMac", StringRule) { sourceMac = it.trim() }
      .put("target", StringRule) { target = it.trim() }
      .put("type", StringRule) { type = WBType.valueOf(it.trim()) }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("name", name)
      .put("sourceMac", sourceMac)
      .put("target", target)
      .put("type", type!!.name)
      .build()
  }
}
