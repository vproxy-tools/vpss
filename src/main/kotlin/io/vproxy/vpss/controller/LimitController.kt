package io.vproxy.vpss.controller

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ArrayBuilder
import io.vproxy.base.util.exception.XException
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.entity.Limit
import io.vproxy.vpss.service.LimitService
import io.vproxy.vpss.service.impl.LimitServiceImpl
import io.vproxy.vpss.util.ErrorCode

class LimitController(app: CoroutineHttp1Server) : BaseController(app) {
  private val limitService: LimitService = LimitServiceImpl

  init {
    app.get("/api/limits", handle(::getLimits))
    app.post("/api/limits/add", handle(::addLimit))
    app.post("/api/limits/:limit/del", handle(::delLimit))
    app.post("/api/limits/:limit/modify-value", handle(::modifyValue))
  }

  // return [Limit]
  private fun getLimits(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    val limits = limitService.getLimits()
    val ret = ArrayBuilder()
    for (limit in limits) {
      ret.addInst(limit.toJson())
    }
    return ret.build()
  }

  // body: Limit
  // return: Limit
  private fun addLimit(ctx: RoutingContext): JSON.Instance<*> {
    val limit = JSON.deserialize(ctx.req.body().toString(), Limit.rule)
    if (limit.name == null) {
      throw XException(ErrorCode.badArgsMissingLimitName)
    }
    if (limit.sourceMac == null) {
      throw XException(ErrorCode.badArgsMissingLimitSourceMac)
    }
    if (limit.target == null) {
      throw XException(ErrorCode.badArgsMissingLimitTarget)
    }
    if (limit.type == null) {
      throw XException(ErrorCode.badArgsMissingLimitType)
    }
    val ret = limitService.addLimit(limit)
    return ret.toJson()
  }

  // return null
  private fun delLimit(ctx: RoutingContext): JSON.Instance<*>? {
    val name = ctx.param("limit")
    limitService.delLimit(name)
    return null
  }

  // body: { value: ... }
  // return null
  private fun modifyValue(ctx: RoutingContext): JSON.Instance<*>? {
    val name = ctx.param("limit")
    val json = JSON.parse(ctx.req.body().toString()) as JSON.Object
    if (!json.containsKey("value")) {
      throw XException(ErrorCode.badArgsMissingLimitValue)
    }
    val n = json.getDouble("value");
    limitService.updateValue(name, n)
    return null
  }
}
