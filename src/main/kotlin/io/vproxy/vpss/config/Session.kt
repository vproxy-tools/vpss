package io.vproxy.vpss.config

import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Timer
import io.vproxy.vpss.util.Global

class Session(val id: String, val pwd: Pwd) : Timer(SelectorEventLoop.current(), timeout) {
  init {
    Logger.alert("session recorded: $id for user ${pwd.username}")
    start()
  }

  override fun cancel() {
    super.cancel()
    Global.sessions.remove(id)
    Logger.warn(LogType.ALERT, "session removed: $id for user ${pwd.username}")
  }

  companion object {
    private val noTimeout: Boolean = Config.get().mainArgs.noSessionTimeout
    private val timeout: Int = if (noTimeout) {
      -1
    } else {
      180_000
    }
  }
}
