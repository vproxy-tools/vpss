package io.vproxy.vpss.service.impl

import io.vproxy.app.app.cmd.Resource
import io.vproxy.app.app.cmd.ResourceType
import io.vproxy.app.app.cmd.handle.resource.ArpHandle
import io.vproxy.base.util.Annotations
import io.vproxy.base.util.exception.AlreadyExistException
import io.vproxy.base.util.exception.NotFoundException
import io.vproxy.base.util.exception.XException
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.Net
import io.vproxy.vpss.entity.NetIP
import io.vproxy.vpss.entity.NetRemoteSw
import io.vproxy.vpss.entity.NetRoute
import io.vproxy.vpss.service.NetworkService
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.ErrorCode
import io.vproxy.vpss.util.Global
import io.vproxy.vpss.util.VPSSUtils
import io.vproxy.vswitch.VirtualNetwork
import io.vproxy.vswitch.iface.Iface
import io.vproxy.vswitch.iface.UserClientIface

object NetworkServiceImpl : NetworkService {
  override fun getNetworks(): List<Net> {
    return Config.get().config.networks
  }

  override fun addNetwork(net: Net): Net {
    if (getConfigNetwork(net.vni) != null) {
      throw XException(ErrorCode.conflictNetwork)
    }
    val network = try {
      Global.getSwitch().addNetwork(net.vni, net.v4net, net.v6net, Annotations())
    } catch (e: AlreadyExistException) {
      throw XException(ErrorCode.conflictNetwork)
    }
    network.putUserData(Consts.allowIpv6Key, false)
    val ret = Net(vni = network.vni, v4net = network.v4network, v6net = network.v6network, allowIpv6 = false)
    VPSSUtils.syncSystemNetEntity(network, ret)
    Config.update {
      config.networks.add(ret)
    }
    VPSSUtils.syncLocalIPs()
    VPSSUtils.syncDockerNetworks()
    return ret
  }

  override fun delNetwork(vni: Int) {
    val net = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    if (net.system) {
      throw XException(ErrorCode.forbiddenDelSystemNetwork)
    }
    try {
      Global.getSwitch().delNetwork(vni)
    } catch (e: NotFoundException) {
      throw XException(ErrorCode.notFoundNetwork)
    }
    Config.update {
      config.networks.removeIf { it.vni == vni }
    }
    VPSSUtils.syncDockerNetworks()
  }

  private fun getNetwork(vni: Int): VirtualNetwork {
    return try {
      Global.getSwitch().getNetwork(vni)
    } catch (e: NotFoundException) {
      throw XException(ErrorCode.notFoundNetwork)
    }
  }

  private fun getConfigNetwork(vni: Int): Net? {
    return Config.get().config.networks.find { it.vni == vni }
  }

  private fun getConfigIp(vni: Int, ip: IP): NetIP? {
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    return configNet.ips.find { it.ip == ip }
  }

  override fun addIp(vni: Int, netIp: NetIP): NetIP {
    val net = getNetwork(vni)
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    val found = getConfigIp(vni, netIp.ip!!)
    if (found != null) {
      throw XException(ErrorCode.conflictIp)
    }
    try {
      net.addIp(netIp.ip, netIp.mac, Annotations())
    } catch (e: AlreadyExistException) {
      throw XException(ErrorCode.conflictIp)
    }
    val ret = NetIP(ip = netIp.ip, mac = netIp.mac, routing = true)
    Config.update {
      configNet.ips.add(ret)
    }
    return ret
  }

  override fun delIp(vni: Int, ip: IP) {
    val net = getNetwork(vni)
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    val configIp = getConfigIp(vni, ip) ?: throw XException(ErrorCode.notFoundIp)
    if (configIp.system) {
      throw XException(ErrorCode.forbiddenDelSystemIp)
    }
    try {
      net.ips.del(ip)
    } catch (e: NotFoundException) {
      throw XException(ErrorCode.notFoundIp)
    }
    Config.update {
      configNet.ips.removeIf { it.ip == ip }
    }
  }

  override fun toggleIpRouting(vni: Int, ip: IP): Boolean {
    val net = getNetwork(vni)
    val configIp = getConfigIp(vni, ip) ?: throw XException(ErrorCode.notFoundIp)
    if (configIp.system) {
      throw XException(ErrorCode.forbiddenUpdateSystemIp)
    }
    val entry = net.ips.entries().find { it.ip == ip } ?: throw XException(ErrorCode.notFoundIp)
    Config.update {
      configIp.routing = !configIp.routing
    }
    entry.routing = configIp.routing
    return entry.routing
  }

  private fun getConfigRoute(vni: Int, name: String): NetRoute? {
    val net = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    return net.routes.find { it.name == name }
  }

  override fun addRoute(vni: Int, netRoute: NetRoute): NetRoute {
    val net = getNetwork(vni)
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    val configRoute = getConfigRoute(vni, netRoute.name!!)
    if (configRoute != null) {
      throw XException(ErrorCode.conflictRoute)
    }
    val rule = VPSSUtils.buildRuleFromNetRoute(net.vni, netRoute)
    try {
      net.routeTable.addRule(rule)
    } catch (e: AlreadyExistException) {
      throw XException(ErrorCode.conflictRoute)
    }
    val ret = VPSSUtils.buildNetRouteEntity(net.vni, rule)
    Config.update {
      configNet.routes.add(ret)
    }
    return ret
  }

  override fun delRoute(vni: Int, name: String) {
    val net = getNetwork(vni)
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    val configRoute = getConfigRoute(vni, name) ?: throw XException(ErrorCode.notFoundRoute)
    if (configRoute.system) {
      throw XException(ErrorCode.forbiddenDelSystemRoute)
    }
    try {
      net.routeTable.delRule(name)
    } catch (e: NotFoundException) {
      throw XException(ErrorCode.notFoundRoute)
    }
    Config.update {
      configNet.routes.removeIf { it.name == name }
    }
  }

  override fun getArpEntries(vni: Int): Collection<ArpHandle.ArpEntry> {
    getNetwork(vni) // ensure network exists
    val res = Resource()
    res.alias = "" + vni
    res.type = ResourceType.vpc
    res.parentResource = Resource()
    res.parentResource.alias = Global.getSwitch().alias
    res.parentResource.type = ResourceType.sw
    return ArpHandle.list(res)
  }

  override fun delArpEntry(vni: Int, mac: MacAddress) {
    val net = getNetwork(vni)
    net.macTable.remove(mac)
    net.arpTable.remove(mac)
  }

  override fun addArpEntry(vni: Int, iface: String?, mac: MacAddress, ip: IP?) {
    val net = getNetwork(vni)
    if (iface != null) {
      val ifaces = Global.getSwitch().ifaces
      var selectedIface: Iface? = null
      for (vif in ifaces) {
        val name = VPSSUtils.formatIfaceName(vif)
        if (name == iface) {
          selectedIface = vif
          break
        }
      }
      if (selectedIface == null) {
        throw XException(ErrorCode.notFoundManagedIface)
      }
      net.macTable.record(mac, selectedIface, true)
    }
    if (ip != null) {
      net.arpTable.record(mac, ip, true)
    }
  }

  override fun applyRemoteSw(vni: Int, remoteSw: NetRemoteSw): NetRemoteSw {
    val remoteAddr = remoteSw.ipport!!
    val user = remoteSw.username!!
    val password = remoteSw.password!!
    val allowDhcp = remoteSw.allowDhcp

    val chars = user.toCharArray()
    if (chars.size < 3 || chars.size > 8) {
      throw XException(ErrorCode.badArgsInvalidUsernameLength)
    }
    for (c in chars) {
      if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9')) {
        throw XException(ErrorCode.badArgsInvalidUsernameChar)
      }
    }

    getNetwork(vni)
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    for (iface in ArrayList(Global.getSwitch().ifaces)) {
      if (iface is UserClientIface) {
        Global.getSwitch().delIface(iface.name())
      }
    }
    val iface = Global.getSwitch().addUserClient(user, password, vni, remoteAddr)
    iface.putUserData(Consts.allowDhcpKey, allowDhcp)
    Config.update {
      configNet.remote.enable = true
      configNet.remote.username = user
      configNet.remote.password = password
      configNet.remote.ipport = remoteAddr
      configNet.remote.allowDhcp = allowDhcp
    }
    return configNet.remote
  }

  override fun disableRemoteSw(vni: Int) {
    getNetwork(vni)
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    for (iface in ArrayList(Global.getSwitch().ifaces)) {
      if (iface is UserClientIface) {
        Global.getSwitch().delIface(iface.name())
      }
    }
    Config.update {
      configNet.remote.enable = false
      configNet.remote.username = null
      configNet.remote.password = null
      configNet.remote.ipport = null
      configNet.remote.allowDhcp = false
    }
  }

  override fun toggleAllowIpv6(vni: Int): Boolean {
    val net = getNetwork(vni)
    val configNet = getConfigNetwork(vni) ?: throw XException(ErrorCode.notFoundNetwork)
    val setAllowIpv6 = !configNet.allowIpv6
    net.putUserData(Consts.allowIpv6Key, setAllowIpv6)
    Config.update {
      configNet.allowIpv6 = setAllowIpv6
    }
    return setAllowIpv6
  }
}
