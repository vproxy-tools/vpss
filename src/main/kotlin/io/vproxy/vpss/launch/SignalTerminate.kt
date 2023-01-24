package io.vproxy.vpss.launch

import io.vproxy.app.app.util.SignalHook
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.vpss.util.Global
import io.vproxy.vpss.util.VPSSUtils
import io.vproxy.vswitch.iface.XDPIface
import kotlin.system.exitProcess

object SignalTerminate {
  fun launch() {
    Logger.alert("launching signal hooks ...")

    SignalHook.getInstance().sigHup { terminate() }
    SignalHook.getInstance().sigTerm { terminate() }
    SignalHook.getInstance().sigInt { terminate() }
  }

  private var terminated = false
  fun terminate() {
    if (terminated) {
      return
    }
    terminated = true
    Logger.alert("terminating ...")

    val sw = Global.getSwitch()
    for (iface in ArrayList(sw.ifaces)) {
      if (iface is XDPIface) {
        try {
          VPSSUtils.unmanageNic(iface.nic)
        } catch (e: Exception) {
          Logger.error(LogType.SYS_ERROR, "failed to unmanage ${iface.nic} when terminating", e)
        }
      }
    }
    try {
      Utils.execute("docker kill vpws-agent")
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to kill vpws-agent", e)
    }
    try {
      Utils.execute("docker kill vpss-ssh-proxy")
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to kill vpss-ssh-proxy", e)
    }
    exitProcess(1)
  }
}
