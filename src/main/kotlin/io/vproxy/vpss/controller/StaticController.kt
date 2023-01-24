package io.vproxy.vpss.controller

import io.vproxy.base.util.ByteArray
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.util.Consts
import java.io.File
import java.nio.file.Files

class StaticController(app: CoroutineHttp1Server) : BaseController(app) {
  companion object {
    private val staticJSURLs = listOf(
      "/js/jquery-3.1.1.min.js",
      "/js/js.cookie.min.js",
      "/js/semantic.min.js",
      "/js/vue.min.js",
      "/js/vue-i18n.min.js",
      "/js/vue-resource.min.js",
      "/js/chart.min.js"
    )
  }

  private val pages = HashMap<String, Page>()

  init {
    app.get("/", ::handlePage)
  }

  fun handleLast() {
    app.get("/*", ::handlePage)
  }

  private suspend fun handlePage(ctx: RoutingContext) {
    var url = ctx.req.uri()
    if (url.contains("?")) {
      url = url.substring(0, url.indexOf("?"))
    }
    val page = getPage(url)
    if (page == null) {
      Logger.warn(LogType.ALERT, "static resource ${ctx.req.uri()} not found")
      ctx.conn.response(404).send(
        "" +
          "<html>" +
          "<head>" +
          "</head>" +
          "<body>" +
          "<h2>404 Not Found - ${ctx.req.uri()}</h2>" +
          "</body>" +
          "</html>"
      )
    } else if (page.redirect) {
      Logger.warn(LogType.ALERT, "static resource ${ctx.req.uri()} will be redirected")
      ctx.conn.response(301).header("Location", ctx.req.uri() + "/").send()
    } else {
      Logger.trace(LogType.ALERT, "static resource ${ctx.req.uri()}: ${page.content.length()} bytes")
      val resp = ctx.conn.response(200)
      if (url.startsWith("/css/") || staticJSURLs.contains(url)) {
        resp.header("Cache-Control", "public, max-age=86400")
      }
      resp.send(page.content)
    }
  }

  private fun getPage(url: String): Page? {
    var file = File(Consts.staticFileDirectory + url)
    if (!file.exists()) {
      return null
    }
    if (file.isDirectory) {
      if (!url.endsWith("/")) {
        return Page(ByteArray.allocate(0), 0, redirect = true)
      }
      file = File(Consts.staticFileDirectory + url + "index.html")
      if (!file.exists()) {
        return null
      }
    }
    if (pages.containsKey(file.absolutePath)) {
      val page = pages[file.absolutePath]!!
      if (page.lastModified == file.lastModified()) {
        return page
      }
      Logger.warn(LogType.ACCESS, "reloading static resource into memory: ${file.absolutePath}")
    } else {
      Logger.warn(LogType.ACCESS, "loading static resource into memory: ${file.absolutePath}")
    }
    val page = Page(ByteArray.from(Files.readAllBytes(file.toPath())), file.lastModified())
    pages[file.absolutePath] = page
    return page
  }

  private data class Page(val content: ByteArray, val lastModified: Long, val redirect: Boolean = false)
}
