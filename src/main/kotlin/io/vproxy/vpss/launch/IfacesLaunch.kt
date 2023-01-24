package io.vproxy.vpss.launch

import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.net.Nic
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.Interface
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global
import io.vproxy.vpss.util.VPSSUtils

object IfacesLaunch {
  fun launch() {
    Logger.alert("launching interfaces ...")

    val nics = VPSSUtils.getNetworkInterfaces()
    val nicMap = HashMap<String, Nic>()
    for (nic in nics) {
      nicMap[nic.name] = nic
    }

    if (Config.get().config.ifaces.isEmpty()) {
      Logger.warn(LogType.ALERT, "no interface configuration found, default to manage all physical interfaces")
      Config.update {
        for (nic in nics) {
          val iface = Interface(
            name = nic.name,
            enable = true,
            allowDhcp = true,
            speed = nic.speed,
            vni = Consts.defaultNetwork,
          )
          config.ifaces.add(iface)
        }
      }
    }
    for (iface in ArrayList(Config.get().config.ifaces)) {
      if (!nicMap.containsKey(iface.name)) {
        Logger.error(LogType.ALERT, "interface ${iface.name} not found, skipping ...")
        Config.update {
          config.ifaces.remove(iface)
          Config.get().nicTombstone.add(iface)
        }
        continue
      }
      val nicInfo = nicMap[iface.name]!!
      val xdp = try {
        Logger.alert("manage iface ${iface.name}")
        VPSSUtils.manageNic(iface.name!!, iface.vni, checkMac = true)
      } catch (e: Exception) {
        rollback()
        throw e
      }
      xdp.isDisabled = !iface.enable
      xdp.putUserData(Consts.allowDhcpKey, iface.allowDhcp)
      xdp.putUserData(Consts.ifaceMacAddress, nicInfo.mac)
      VPSSUtils.initStatisticsForIface(xdp)
      for (vlan in iface.vlans) {
        Logger.alert("manage vlan ${vlan.remoteVLan} on iface ${iface.name} to vni ${vlan.localVni}")
        val vlanIface = Global.getSwitch().addVLanAdaptor(xdp.name(), vlan.remoteVLan, vlan.localVni)
        vlanIface.isDisabled = !vlan.enable
        vlanIface.putUserData(Consts.allowDhcpKey, vlan.allowDhcp)
        vlanIface.putUserData(Consts.ifaceMacAddress, nicInfo.mac)
        VPSSUtils.initStatisticsForIface(vlanIface)
      }
    }
    VPSSUtils.syncLocalIPs()
  }

  private fun rollback() {
    for (iface in Config.get().config.ifaces) {
      try {
        VPSSUtils.unmanageNic(iface.name!!)
      } catch (e: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to unmanage ${iface.name} when rolling back", e)
      }
    }
  }
}
