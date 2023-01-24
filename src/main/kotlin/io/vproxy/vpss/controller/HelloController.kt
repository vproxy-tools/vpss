package io.vproxy.vpss.controller

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.util.Consts

class HelloController(app: CoroutineHttp1Server) : BaseController(app) {
  init {
    app.get("/api/ping", handle(::ping))
    app.get("/api/version", handle(::version))
  }

  // return null
  private fun ping(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*>? {
    return null
  }

  // return { version }
  private fun version(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    return ObjectBuilder()
      .put("version", Consts.VERSION)
      .build()
  }
}
