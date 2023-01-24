package io.vproxy.vpss.controller

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.base.util.Logger
import io.vproxy.base.util.exception.XException
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.config.*
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.ErrorCode
import io.vproxy.vpss.util.Global
import java.util.*

class LoginController(app: CoroutineHttp1Server) : BaseController(app) {
  init {
    app.post("/api/login", handle(::login))
    app.all("/api/*", ::checkSessionLogin)
    app.post("/api/logout", handle(::logout))
    app.get("/api/whoami", handle(::currentUser))
    app.post("/api/password/change", handle(::changePassword))
  }

  private fun find(username: String): Pwd? {
    for (p in Config.get().pwd.pwds) {
      if (p.username == username) {
        return p
      }
    }
    return null
  }

  // body: { username, password }
  // return { cookieKey, cookieValue }
  private fun login(ctx: RoutingContext): JSON.Instance<*> {
    val json = JSON.parse(ctx.req.body().toString()) as JSON.Object
    val username = json.getString("username").trim()
    val password = json.getString("password").trim()
    var pwd = find(username)
    if (pwd == null) {
      if (Config.get().pwd.initiated) {
        throw XException(ErrorCode.forbidden)
      }
      if (username != Consts.adminUsername) {
        throw XException(ErrorCode.forbidden)
      }
      if (password.isBlank()) {
        throw XException(ErrorCode.forbidden)
      }
      if (password.length < Consts.minPassLength) {
        throw XException(ErrorCode.badArgsPassLengthTooShort)
      }
      val salt = UUID.randomUUID().toString()
      val hash = SaltHashMethod.V1.func(password, salt)
      pwd = Pwd(username = username, salt = salt, saltHashMethod = SaltHashMethod.V1, passhash = hash)
      Config.updatePwd {
        this.pwds.add(pwd)
        this.initiated = true
      }
      Logger.alert("user ${Consts.adminUsername} initiated")
    }
    val hash = pwd.saltHashMethod!!.func(password, pwd.salt!!)
    if (pwd.passhash != hash) {
      throw XException(ErrorCode.forbidden)
    }

    val cookie = UUID.randomUUID().toString()
    Global.sessions[cookie] = Session(cookie, pwd)

    return ObjectBuilder()
      .put("cookieKey", Consts.sessionCookieKey)
      .put("cookieValue", cookie)
      .build()
  }

  private suspend fun checkSessionLogin(ctx: RoutingContext) {
    val cookieHdr = ctx.req.headers().get("Cookie")
    if (cookieHdr == null) {
      respondError(ctx, XException(ErrorCode.forbidden))
      return
    }
    val cookies = cookieHdr.split(";")
    var sessionId: String? = null
    for (cookie0 in cookies) {
      val cookie = cookie0.trim()
      if (cookie.startsWith(Consts.sessionCookieKey + "=")) {
        sessionId = cookie.substring((Consts.sessionCookieKey + "=").length)
        break
      }
    }
    if (sessionId == null) {
      respondError(ctx, XException(ErrorCode.forbidden))
      return
    }
    val session = Global.sessions[sessionId]
    if (session == null) {
      respondError(ctx, XException(ErrorCode.forbidden))
      return
    }
    session.resetTimer()
    ctx.put(SessionKey, session)
    ctx.allowNext()
  }

  // return null
  private fun logout(ctx: RoutingContext): JSON.Instance<*>? {
    val session = ctx.get(SessionKey)!!
    session.cancel()
    Global.sessions.remove(session.id)
    return null
  }

  // return { username }
  private fun currentUser(ctx: RoutingContext): JSON.Instance<*> {
    val username = ctx.get(SessionKey)!!.pwd.username
    return ObjectBuilder()
      .put("username", username)
      .build()
  }

  // body = {"old": "...", "new": "..."}
  // return null
  private fun changePassword(ctx: RoutingContext): JSON.Instance<*>? {
    val body = JSON.parse(ctx.req.body().toString()) as JSON.Object
    if (!body.containsKey("old")) {
      throw XException(ErrorCode.badArgsMissingOldPassword)
    }
    if (!body.containsKey("new")) {
      throw XException(ErrorCode.badArgsMissingNewPassword)
    }
    val oldPass = body.getString("old").trim()
    val newPass = body.getString("new").trim()
    if (oldPass.isEmpty()) {
      throw XException(ErrorCode.badArgsMissingOldPassword)
    }
    if (newPass.isEmpty()) {
      throw XException(ErrorCode.badArgsMissingNewPassword)
    }

    val pwd = ctx.get(SessionKey)!!.pwd
    if (pwd.saltHashMethod!!.func(oldPass, pwd.salt!!) != pwd.passhash) {
      throw XException(ErrorCode.forbiddenOldPasswordWrong)
    }
    if (newPass.length < Consts.minPassLength) {
      throw XException(ErrorCode.badArgsPassLengthTooShort)
    }
    val salt = UUID.randomUUID().toString()
    val newhash = pwd.saltHashMethod!!.func(newPass, salt)
    Config.updatePwd {
      pwd.salt = salt
      pwd.passhash = newhash
    }

    // remove session
    ctx.put(SessionKey, null)

    return null
  }
}
