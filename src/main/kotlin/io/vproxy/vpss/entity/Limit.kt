package io.vproxy.vpss.entity

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.*
import io.vproxy.dep.vjson.util.ObjectBuilder

data class Limit(
  var name: String? = null,
  var sourceMac: String? = null,
  var target: String? = null,
  var type: LimitType? = null,
  var value: Double = 0.0,
) {
  companion object {
    val rule: Rule<Limit> = ObjectRule { Limit() }
      .put("name", StringRule) { name = it.trim() }
      .put("sourceMac", StringRule) { sourceMac = it.trim() }
      .put("target", StringRule) { target = it.trim() }
      .put("type", StringRule) { type = LimitType.valueOf(it.trim()) }
      .put("value", DoubleRule) { value = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("name", name)
      .put("sourceMac", sourceMac)
      .put("target", target)
      .put("type", type!!.name)
      .put("value", value)
      .build()
  }
}
