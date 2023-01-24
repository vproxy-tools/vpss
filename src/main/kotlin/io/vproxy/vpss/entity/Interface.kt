package io.vproxy.vpss.entity

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.*
import io.vproxy.dep.vjson.util.ObjectBuilder

data class Interface(
  var name: String? = null,
  var enable: Boolean = false,
  var allowDhcp: Boolean = false,
  var speed: Int = 0,
  var vni: Int = 0,
  var vlans: MutableList<InterfaceVLan> = ArrayList()
) {
  companion object {
    val rule: Rule<Interface> = ObjectRule { Interface() }
      .put("name", StringRule) { name = it.trim() }
      .put("enable", BoolRule) { enable = it }
      .put("allowDhcp", BoolRule) { allowDhcp = it }
      .put("speed", IntRule) { speed = it }
      .put("vni", IntRule) { vni = it }
      .put("vlans", ArrayRule({ ArrayList<InterfaceVLan>() }, { add(it) }, InterfaceVLan.rule)) { vlans = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("name", name)
      .put("enable", enable)
      .put("allowDhcp", allowDhcp)
      .put("speed", speed)
      .put("vni", vni)
      .putArray("vlans") { vlans.forEach { addInst(it.toJson()) } }
      .build()
  }
}
