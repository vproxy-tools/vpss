package io.vproxy.vpss.config

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.deserializer.rule.ObjectRule
import io.vproxy.dep.vjson.deserializer.rule.Rule
import io.vproxy.dep.vjson.deserializer.rule.StringRule
import io.vproxy.dep.vjson.util.ObjectBuilder

data class Pwd(
  var username: String? = null,
  var passhash: String? = null,
  var salt: String? = null,
  var saltHashMethod: SaltHashMethod? = null,
) {
  companion object {
    val rule: Rule<Pwd> = ObjectRule { Pwd() }
      .put("username", StringRule) { username = it }
      .put("passhash", StringRule) { passhash = it }
      .put("salt", StringRule) { salt = it }
      .put("saltHashMethod", StringRule) { saltHashMethod = SaltHashMethod.valueOf(it) }
  }

  fun toJson(): JSON.Object {
    return ObjectBuilder()
      .put("username", username)
      .put("passhash", passhash)
      .put("salt", salt)
      .put("saltHashMethod", saltHashMethod!!.name)
      .build()
  }
}
