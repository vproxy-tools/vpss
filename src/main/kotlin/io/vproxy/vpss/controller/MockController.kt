package io.vproxy.vpss.controller

import io.vproxy.lib.http1.CoroutineHttp1Server

class MockController(app: CoroutineHttp1Server) : BaseController(app) {
  init {
    // handles www.msftconnecttest.com to make sure windows computers get normal network connection report
    app.get("/connecttest.txt") { it.conn.response(200).send("Microsoft Connect Test") }
    // also handle the commonly used /redirect url
    @Suppress("HttpUrlsUsage")
    app.get("/redirect") { it.conn.response(302).header("Location", "http://go.microsoft.com/fwlink/?LinkID=219472&clcid=0x409").send() }
    app.get("/ncsi.txt") { it.conn.response(200).send("Microsoft NCSI") }
  }
}
