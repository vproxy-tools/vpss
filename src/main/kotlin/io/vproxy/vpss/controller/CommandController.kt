package io.vproxy.vpss.controller

import io.vproxy.base.util.exception.XException
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.service.CommandService
import io.vproxy.vpss.service.impl.CommandServiceImpl
import io.vproxy.vpss.util.ErrorCode

class CommandController(app: CoroutineHttp1Server) : BaseController(app) {
  private val commandService: CommandService = CommandServiceImpl

  init {
    app.post("/api/command/execute", handle(::execute))
  }

  private suspend fun execute(ctx: RoutingContext): JSON.Object {
    val body = JSON.parse(ctx.req.body().toString()) as JSON.Object
    if (!body.containsKey("script")) {
      throw XException(ErrorCode.badArgsMissingScript)
    }
    val script = body.getString("script")
    var timeout = if (body.containsKey("timeout")) body.getInt("timeout") else 5_000
    if (timeout < 1_000) {
      timeout = 1_000
    } else if (timeout > 30_000) {
      timeout = 30_000
    }
    val res = commandService.execute(script, timeout)
    return ObjectBuilder()
      .put("exitCode", res.exitCode)
      .put("stdout", res.stdout)
      .put("stderr", res.stderr)
      .build()
  }
}
