package io.vproxy.vpss.config

import io.vproxy.base.util.ByteArray
import io.vproxy.base.util.Utils

enum class SaltHashMethod(val func: (String, String) -> String) {
  V1(::v1),
}

private fun v1(pass: String, salt: String): String =
  Utils.bytesToHex(Utils.sha1(ByteArray.from(pass).concat(ByteArray.from(salt)).toJavaArray()))
