package io.vproxy.vpss.config

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.ArrayRule
import io.vproxy.dep.vjson.deserializer.rule.BoolRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.util.ObjectBuilder

data class PwdListJson(
  var initiated: Boolean = false,
  var pwds: MutableList<Pwd> = ArrayList(),
) {
  companion object {
    val rule = ObjectRule { PwdListJson() }
      .put("initiated", BoolRule) { initiated = it }
      .put("pwds", ArrayRule({ ArrayList<Pwd>() }, { add(it) }, Pwd.rule)) { pwds = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("initiated", initiated)
      .putArray("pwds") { pwds.forEach { addInst(it.toJson()) } }
      .build()
  }
}
