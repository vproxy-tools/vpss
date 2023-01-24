package io.vproxy.vpss.controller

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.base.util.exception.XException
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.service.VPWSAgentService
import io.vproxy.vpss.service.VPWSAgentStatus
import io.vproxy.vpss.service.impl.VPWSAgentServiceImpl
import io.vproxy.vpss.util.ErrorCode
import java.io.File
import java.nio.file.Files

class VPWSAgentController(app: CoroutineHttp1Server) : BaseController(app) {
  private val vpwsAgentService: VPWSAgentService = VPWSAgentServiceImpl

  init {
    app.get("/api/vpws-agent/status", handle(::getStatus))
    app.get("/api/vpws-agent/config", handle(::getConfig))
    app.post("/api/vpws-agent/config/update", handle(::updateConfig))
  }

  // return {"status": "..."}
  private suspend fun getStatus(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    val status = vpwsAgentService.getStatus()
    return ObjectBuilder()
      .put("status", status.name)
      .build()
  }

  // return {"config": "..."}
  private fun getConfig(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    val file = File("/etc/vpss/vpws-agent.conf")
    if (!file.exists()) {
      return ObjectBuilder()
        .put("config", "")
        .build()
    }
    val content = Files.readString(file.toPath())
    return ObjectBuilder()
      .put("config", content)
      .build()
  }

  // body = {"config": "..."}
  // return {"status": "..."}
  private suspend fun updateConfig(ctx: RoutingContext): JSON.Instance<*> {
    val body = JSON.parse(ctx.req.body().toString()) as JSON.Object
    if (!body.containsKey("config")) {
      throw XException(ErrorCode.badArgsMissingVPWSAgentConfig)
    }
    val conf = body.getString("config")
    vpwsAgentService.updateConfigAndRestart(conf)
    return ObjectBuilder()
      .put("status", VPWSAgentStatus.starting.name)
      .build()
  }
}
