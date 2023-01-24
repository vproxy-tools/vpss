package io.vproxy.vpss.controller

import io.vproxy.base.util.exception.XException
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.service.FlowService
import io.vproxy.vpss.service.impl.FlowServiceImpl
import io.vproxy.vpss.util.ErrorCode

class FlowController(app: CoroutineHttp1Server) : BaseController(app) {
  private val flowService: FlowService = FlowServiceImpl

  init {
    app.post("/api/flow/toggle-enable", handle(::toggleEnable))
    app.get("/api/flow/config", handle(::getConfig))
    app.post("/api/flow/update", handle(::updateFlow))
  }

  // return { enable: bool }
  private suspend fun toggleEnable(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    val ret = flowService.toggleFlowEnableDisable()
    return ObjectBuilder()
      .put("enable", ret)
      .build()
  }

  // return {"enable": bool,"config": "..."}
  private fun getConfig(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    return Config.get().config.flow.toJson()
  }

  // body = {"config": "..."}
  // return {"enable": "..."}
  private suspend fun updateFlow(ctx: RoutingContext): JSON.Instance<*> {
    val body = JSON.parse(ctx.req.body().toString()) as JSON.Object
    if (!body.containsKey("flow")) {
      throw XException(ErrorCode.badArgsMissingFlow)
    }
    val flow = body.getString("flow")
    val enable = flowService.updateFlow(flow)
    return ObjectBuilder()
      .put("enable", enable)
      .build()
  }
}
