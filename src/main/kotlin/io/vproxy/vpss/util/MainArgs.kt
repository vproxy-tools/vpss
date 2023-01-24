package io.vproxy.vpss.util

class MainArgs {
  var ignoreIfaces: Collection<String> = listOf()
  var includeIfaces: Collection<String> = listOf()
  var noSessionTimeout: Boolean = false
  var skipDhcp: Boolean = false
  var imagePrefix: String = "vproxyio"
}
