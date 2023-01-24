package io.vproxy.vpss.launch

import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global

object RemoteSwitchLaunch {
  fun launch() {
    Logger.alert("launching remote switches ...")
    // wait for a while to make sure nics are prepared
    Thread.sleep(10_000)

    for (net in Config.get().config.networks) {
      if (!net.remote.enable) {
        continue
      }
      try {
        val iface = Global.getSwitch().addUserClient(net.remote.username!!, net.remote.password!!, net.vni, net.remote.ipport!!)
        iface.putUserData(Consts.allowDhcpKey, net.remote.allowDhcp)
        Logger.alert("remote switch ${iface.name()} added")
      } catch (e: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to add remote switch to ${net.remote.ipport}", e)
        throw e
      }
    }
  }
}
