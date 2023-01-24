package io.vproxy.vpss.config

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.ArrayRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.entity.Interface
import io.vproxy.vpss.entity.Limit
import io.vproxy.vpss.entity.Net
import io.vproxy.vpss.entity.WBList

data class ConfigJson(
  var virtualMac: MacAddress? = null,
  var virtualSSHMac: MacAddress? = null,
  var vpwsAgentMac: MacAddress? = null,
  var networks: MutableList<Net> = ArrayList(),
  var ifaces: MutableList<Interface> = ArrayList(),
  var limits: MutableList<Limit> = ArrayList(),
  var system: SystemConfig = SystemConfig(),
  var flow: Flow = Flow(),
  var wblists: MutableList<WBList> = ArrayList(),
) {
  companion object {
    val rule = ObjectRule { ConfigJson() }
      .put("virtualMac", StringRule) { virtualMac = MacAddress(it) }
      .put("virtualSSHMac", StringRule) { virtualSSHMac = MacAddress(it) }
      .put("vpwsAgentMac", StringRule) { vpwsAgentMac = MacAddress(it) }
      .put("networks", ArrayRule({ ArrayList<Net>() }, { add(it) }, Net.rule)) { networks = it }
      .put("ifaces", ArrayRule({ ArrayList<Interface>() }, { add(it) }, Interface.rule)) { ifaces = it }
      .put("limits", ArrayRule({ ArrayList<Limit>() }, { add(it) }, Limit.rule)) { limits = it }
      .put("system", SystemConfig.rule) { system = it }
      .put("flow", Flow.rule) { flow = it }
      .put("wblists", ArrayRule({ ArrayList<WBList>() }, { add(it) }, WBList.rule)) { wblists = it }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("virtualMac", virtualMac.toString())
      .put("virtualSSHMac", virtualSSHMac.toString())
      .put("vpwsAgentMac", vpwsAgentMac.toString())
      .putArray("networks") { networks.forEach { addInst(it.toJson()) } }
      .putArray("ifaces") { ifaces.forEach { addInst(it.toJson()) } }
      .putArray("limits") { limits.forEach { addInst(it.toJson()) } }
      .putInst("system", system.toJson())
      .putInst("flow", flow.toJson())
      .putArray("wblists") { wblists.forEach { addInst(it.toJson()) } }
      .build()
  }
}
