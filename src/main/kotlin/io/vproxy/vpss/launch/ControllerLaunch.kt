package io.vproxy.vpss.launch

import io.vproxy.base.connection.ServerSock
import io.vproxy.base.util.Logger
import io.vproxy.lib.common.coroutine
import io.vproxy.lib.common.launch
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPPort
import io.vproxy.vpss.controller.*
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global

object ControllerLaunch {
  fun launch() {
    Logger.alert("launching controller ...")

    val vgw = ServerSock.create(IPPort(Consts.vgwIp.formatToIPString() + ":80"), Global.getSwitch().getNetwork(Consts.defaultNetwork).fds())
    Global.getLoop().launch {
      val app = CoroutineHttp1Server(vgw.coroutine())
      app.all("/", ::accessLog)
      app.all("/*", ::accessLog)
      app.all("/", ::checkHost)
      app.all("/*", ::checkHost)
      app.get("/") { it.conn.response(301).header("Location", "/login.html").send() }
      MockController(app)
      val staticController = StaticController(app)
      HelloController(app)
      LoginController(app)
      SysController(app)
      NetworkController(app)
      IfaceController(app)
      LimitController(app)
      StatisticsController(app)
      VPWSAgentController(app)
      FlowController(app)
      WBListController(app)
      staticController.handleLast()
      app.start()
    }
  }

  private fun accessLog(ctx: RoutingContext) {
    Logger.access("${ctx.req.method()} ${ctx.req.uri()} ${ctx.req.body()}")
    ctx.allowNext()
  }

  private suspend fun checkHost(ctx: RoutingContext) {
    val host = ctx.req.headers().get("host")
    if (host == null ||
      IP.isIpLiteral(host) ||
      IPPort.validL4AddrStr(host) ||
      host == "vgw.special.vproxy.io" ||
      host.startsWith("vgw.special.vproxy.io:")
    ) {
      ctx.allowNext()
      return
    }
    if (ctx.req.uri().startsWith("/redirect") || ctx.req.uri().startsWith("/connecttest.txt")) {
      ctx.allowNext()
      return
    }
    ctx.conn.response(404).send("404 page not found\n")
  }
}
