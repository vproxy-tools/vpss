package io.vproxy.vpss.service.impl

import io.vproxy.base.util.coll.Tuple
import io.vproxy.base.util.exception.NotFoundException
import io.vproxy.base.util.exception.XException
import io.vproxy.base.util.net.Nic
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.Interface
import io.vproxy.vpss.entity.InterfaceVLan
import io.vproxy.vpss.service.InterfaceService
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.ErrorCode
import io.vproxy.vpss.util.Global
import io.vproxy.vpss.util.VPSSUtils

object InterfaceServiceImpl : InterfaceService {
  override fun getIfaces(): Tuple<List<Interface>, List<Interface>> {
    val managed = ArrayList<Interface>()
    val managedNames = HashSet<String>()
    for (o in Config.get().config.ifaces) {
      managed.add(o)
      managedNames.add(o.name!!)
    }
    val nics = VPSSUtils.getNetworkInterfaces()
    val unmanaged = ArrayList<Interface>()
    for (nic in nics) {
      if (managedNames.contains(nic.name)) {
        continue
      }
      val iface = Interface(name = nic.name, speed = nic.speed)
      unmanaged.add(iface)
    }
    return Tuple(managed, unmanaged)
  }

  private fun getInterface(name: String): Interface? {
    for (i in Config.get().config.ifaces) {
      if (i.name == name) {
        return i
      }
    }
    return null
  }

  private fun getVLanIface(name: String, vlan: Int): InterfaceVLan? {
    val iface = getInterface(name) ?: throw XException(ErrorCode.notFoundManagedIface)
    for (v in iface.vlans) {
      if (v.remoteVLan == vlan) {
        return v
      }
    }
    return null
  }

  override fun manageIface(i: Interface): Interface {
    if (getInterface(i.name!!) != null) {
      throw XException(ErrorCode.conflictIface)
    }
    if (i.vni < 1) {
      throw XException(ErrorCode.badArgsInvalidIfaceVni)
    }

    val nics = VPSSUtils.getNetworkInterfaces()
    var selected: Nic? = null
    for (nic in nics) {
      if (nic.name == i.name) {
        selected = nic
        break
      }
    }
    if (selected == null) {
      throw XException(ErrorCode.notFoundUnmanagedIface)
    }

    val xdp = VPSSUtils.manageNic(i.name!!, i.vni, checkMac = true)
    VPSSUtils.initStatisticsForIface(xdp)
    xdp.putUserData(Consts.ifaceMacAddress, selected.mac)

    val ret = Interface(
      name = i.name,
      enable = true,
      allowDhcp = true,
      speed = selected.speed,
      i.vni,
    )
    Config.update {
      config.ifaces.add(ret)
    }
    VPSSUtils.syncLocalIPs()
    return ret
  }

  override fun unmanageIface(name: String) {
    var nic: Interface? = null
    for (o in Config.get().config.ifaces) {
      if (name == o.name) {
        nic = o
      }
    }
    if (nic == null) {
      throw XException(ErrorCode.notFoundManagedIface)
    }
    if (Config.get().config.ifaces.size <= 1) {
      throw XException(ErrorCode.preconditionFailedTooFewManagedIfaces)
    }

    VPSSUtils.unmanageNic(nic.name!!)
    Config.update {
      config.ifaces.removeIf { it.name == name }
    }
    VPSSUtils.syncLocalIPs()
  }

  override fun toggleIfaceEnableDisable(name: String): Boolean {
    val iface = VPSSUtils.getIface(name) ?: throw XException(ErrorCode.notFoundManagedIface)
    val configIface = getInterface(name) ?: throw XException(ErrorCode.notFoundManagedIface)
    Config.update {
      configIface.enable = !configIface.enable
    }
    iface.isDisabled = !configIface.enable
    return configIface.enable
  }

  override fun toggleVLanEnableDisable(name: String, vlan: Int): Boolean {
    val realIface = VPSSUtils.getIface(name) ?: throw XException(ErrorCode.notFoundManagedIface)

    val configVLan = getVLanIface(name, vlan) ?: throw XException(ErrorCode.notFoundVLanIface)
    val vlanIface = VPSSUtils.getIface("vlan.$vlan@${realIface.name()}") ?: throw XException(ErrorCode.notFoundVLanIface)

    Config.update {
      configVLan.enable = !configVLan.enable
    }
    vlanIface.isDisabled = !configVLan.enable
    return configVLan.enable
  }

  override fun toggleIfaceAllowDisallowDhcp(name: String): Boolean {
    val iface = VPSSUtils.getIface(name) ?: throw XException(ErrorCode.notFoundManagedIface)
    val configIf = getInterface(name) ?: throw XException(ErrorCode.notFoundManagedIface)
    Config.update {
      configIf.allowDhcp = !configIf.allowDhcp
    }
    iface.putUserData(Consts.allowDhcpKey, configIf.allowDhcp)
    return configIf.allowDhcp
  }

  override fun toggleVLanAllowDisallowDhcp(name: String, vlan: Int): Boolean {
    val realIface = VPSSUtils.getIface(name) ?: throw XException(ErrorCode.notFoundManagedIface)

    val configVLan = getVLanIface(name, vlan) ?: throw XException(ErrorCode.notFoundVLanIface)
    val vlanIface = VPSSUtils.getIface("vlan.$vlan@${realIface.name()}") ?: throw XException(ErrorCode.notFoundVLanIface)

    Config.update {
      configVLan.allowDhcp = !configVLan.allowDhcp
    }
    vlanIface.putUserData(Consts.allowDhcpKey, configVLan.allowDhcp)
    return configVLan.allowDhcp
  }

  override fun joinVLan(name: String, vlan: InterfaceVLan): InterfaceVLan {
    val configIface = getInterface(name) ?: throw XException(ErrorCode.notFoundManagedIface)
    val vlanIface = getVLanIface(configIface.name!!, vlan.remoteVLan)
    if (vlanIface != null) {
      throw XException(ErrorCode.conflictVLanIface)
    }

    val vlanIfaceCreated = Global.getSwitch().addVLanAdaptor("xdp:${configIface.name}", vlan.remoteVLan, vlan.localVni)
    VPSSUtils.initStatisticsForIface(vlanIfaceCreated)
    vlanIfaceCreated.putUserData(Consts.ifaceMacAddress, VPSSUtils.getNicMac(name))

    val ret = InterfaceVLan(remoteVLan = vlanIfaceCreated.remoteVLan, enable = true, allowDhcp = true, localVni = vlanIfaceCreated.localVni)
    Config.update {
      configIface.vlans.add(ret)
    }
    return ret
  }

  override fun leaveVLan(name: String, vlan: Int) {
    val realIface = VPSSUtils.getIface(name) ?: throw XException(ErrorCode.notFoundManagedIface)
    val configIface: Interface = getInterface(name) ?: throw XException(ErrorCode.notFoundManagedIface)

    try {
      Global.getSwitch().delIface("vlan.$vlan@${realIface.name()}")
    } catch (e: NotFoundException) {
      throw XException(ErrorCode.notFoundVLanIface)
    }

    Config.update {
      configIface.vlans.removeIf { it.remoteVLan == vlan }
    }
  }
}
