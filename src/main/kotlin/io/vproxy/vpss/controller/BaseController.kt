package io.vproxy.vpss.controller

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.base.util.exception.XException
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server

abstract class BaseController(protected val app: CoroutineHttp1Server) {
  protected fun handle(func: suspend (RoutingContext) -> JSON.Instance<*>?): suspend (RoutingContext) -> Unit {
    return {
      val res = try {
        val obj = func(it)
        val ob = ObjectBuilder()
          .put("ok", true)
        if (obj != null) {
          ob.putInst("result", obj)
        } else {
          ob.put("result", null)
        }
        ob.build()
      } catch (e: Throwable) {
        if (e !is XException) {
          Logger.warn(LogType.ALERT, "api thrown exception", e)
        }
        ObjectBuilder()
          .put("ok", false)
          .put("error", Utils.formatErr(e))
          .build()
      }
      it.conn.response(200).send(res)
    }
  }

  protected suspend fun respondError(ctx: RoutingContext, e: Throwable) {
    ctx.conn.response(200).send(
      ObjectBuilder()
        .put("ok", false)
        .put("error", Utils.formatErr(e))
        .build()
    )
  }
}
