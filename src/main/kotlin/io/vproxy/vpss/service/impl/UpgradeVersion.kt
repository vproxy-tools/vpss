package io.vproxy.vpss.service.impl

import io.vproxy.dep.vjson.deserializer.rule.ArrayRule
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule

data class UpgradeVersion(
  var version: String = "",
  var vproxyVersion: String = "",
  var images: List<UpgradeImageVersion> = listOf(),
) {
  companion object {
    val rule: Rule<UpgradeVersion> = ObjectRule { UpgradeVersion() }
      .put("version", StringRule) { version = it }
      .put("vproxyVersion", StringRule) { vproxyVersion = it }
      .put("images", ArrayRule({ ArrayList<UpgradeImageVersion>() }, { add(it) }, UpgradeImageVersion.rule)) { images = it }
  }
}

data class UpgradeImageVersion(
  var name: String = "",
  var sha256: String = "",
) {
  companion object {
    val rule: Rule<UpgradeImageVersion> = ObjectRule { UpgradeImageVersion() }
      .put("name", StringRule) { name = it }
      .put("sha256", StringRule) { sha256 = it }
  }
}
