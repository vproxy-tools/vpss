package io.vproxy.vpss.controller

import io.vproxy.base.util.exception.XException
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ArrayBuilder
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.entity.WBList
import io.vproxy.vpss.service.WBListService
import io.vproxy.vpss.service.impl.WBListServiceImpl
import io.vproxy.vpss.util.ErrorCode

class WBListController(app: CoroutineHttp1Server) : BaseController(app) {
  private val wblistService: WBListService = WBListServiceImpl

  init {
    app.get("/api/wblists", handle(::getWBLists))
    app.post("/api/wblists/add", handle(::addRule))
    app.post("/api/wblists/:wblist/del", handle(::delRule))
  }

  // return [WBList]
  private fun getWBLists(@Suppress("UNUSED_PARAMETER") ctx: RoutingContext): JSON.Instance<*> {
    val wblists = wblistService.getWBLists()
    val ret = ArrayBuilder()
    for (wblist in wblists) {
      ret.addInst(wblist.toJson())
    }
    return ret.build()
  }

  // body: WBList
  // return: WBList
  private fun addRule(ctx: RoutingContext): JSON.Instance<*> {
    val wblist = JSON.deserialize(ctx.req.body().toString(), WBList.rule)
    if (wblist.name == null || wblist.name!!.isBlank()) {
      throw XException(ErrorCode.badArgsMissingWBListName)
    }
    if (wblist.sourceMac == null || wblist.sourceMac!!.isBlank()) {
      throw XException(ErrorCode.badArgsMissingWBListSourceMac)
    }
    if (wblist.target == null || wblist.target!!.isBlank()) {
      throw XException(ErrorCode.badArgsMissingWBListTarget)
    }
    if (wblist.type == null) {
      throw XException(ErrorCode.badArgsMissingWBListType)
    }
    val ret = wblistService.addRule(wblist)
    return ret.toJson()
  }

  // return null
  private fun delRule(ctx: RoutingContext): JSON.Instance<*>? {
    val name = ctx.param("wblist")
    wblistService.delRule(name)
    return null
  }
}
