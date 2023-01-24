package io.vproxy.vpss.launch

import io.vproxy.base.util.*
import io.vproxy.base.util.coll.Tuple
import io.vproxy.base.util.coll.Tuple4
import io.vproxy.base.util.net.Nic
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPv4
import io.vproxy.vfd.IPv6
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.Net
import io.vproxy.vpss.network.DHCPOnlyPacketFilter
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global
import io.vproxy.vpss.util.VPSSUtils
import io.vproxy.vswitch.RouteTable

object DHCPLaunch {
  fun launch(skip: Boolean) {
    Logger.alert("launching dhcp ...")

    val nics = VPSSUtils.getNetworkInterfaces()
    val nicsMap = HashMap<String, Nic>()
    for (nic in nics) {
      nicsMap[nic.name] = nic
    }
    val toHandleNics = ArrayList<Nic>()
    // only add nics in config
    for (iface in Config.get().config.ifaces) {
      val nic = nicsMap[iface.name] ?: continue
      toHandleNics.add(nic)
    }
    // if no nics recorded, add all nics instead
    if (toHandleNics.isEmpty()) {
      for ((_, nic) in nicsMap) {
        toHandleNics.add(nic)
      }
    }
    // otherwise, throw exception
    if (toHandleNics.isEmpty()) {
      throw Exception("no physical network interfaces available")
    }
    VPSSUtils.syncDockerNetworks()
    var result: Tuple4<IPv4, Network, IPv6?, Network?>? = null
    if (!skip) {
      val dhcpOnlyPacketFilter = DHCPOnlyPacketFilter()
      Global.getSwitch().addIfaceWatcher(dhcpOnlyPacketFilter)

      for (nic in toHandleNics) {
        // do not use driver mode here because the iface would go down for a while when launching into driver mode
        VPSSUtils.manageNic(nic.name, Consts.systemInternalVni, checkMac = true, tryDriverMode = false)
      }
      result = handle()
      for (nic in toHandleNics) {
        VPSSUtils.unmanageNic(nic.name)
      }
      if (result == null) {
        throw Exception("dhcp failed, network is unknown")
      }

      Global.getSwitch().removeIfaceWatcher(dhcpOnlyPacketFilter)
      for (iface in Global.getSwitch().ifaces) {
        iface.removeIngressFilter(dhcpOnlyPacketFilter)
      }
    }
    if (result == null) {
      Logger.warn(LogType.ALERT, "unable to get ip and network via dhcp, using default network configuration")
      result = Tuple4(Consts.vgwIp, Network.from("0.0.0.0/0"), null, Network.from("::/0"))
    } else {
      Logger.alert("using $result as the default network configuration")
    }

    val network = Global.getSwitch().addNetwork(Consts.defaultNetwork, result._2, result._4, Annotations())
    network.addIp(result._1, Config.get().config.virtualMac, Annotations())
    if (result._3 != null) {
      network.addIp(result._3!!, Config.get().config.virtualMac, Annotations())
    }
    if (result._1 != Consts.vgwIp) {
      val vgwip = network.ips.__add(
        Consts.vgwIp,
        Config.get().config.virtualMac,
        true,
        Annotations()
      )
      vgwip.routing = false
      // add routing rule for admin
      network.routeTable.addRule(RouteTable.RouteRule("vgw-admin", Consts.vgwAdminNet, Consts.defaultNetwork))
    }

    Config.update {
      var netDefault = config.networks.find { it.vni == Consts.defaultNetwork }
      if (netDefault == null) {
        netDefault = Net(vni = network.vni, system = true)
        config.networks.add(0, netDefault)
      }
      network.putUserData(Consts.allowIpv6Key, netDefault.allowIpv6)
      VPSSUtils.syncSystemNetEntity(network, netDefault)
    }
  }

  private fun handle(): Tuple4<IPv4, Network, IPv6?, Network?>? {
    try {
      val containerName = "vpss-dhcp"
      Utils.execute(
        """
        #!/bin/bash
        set -x
        set +e
        docker kill $containerName
        docker rm $containerName
        set -e
        docker run --rm -d --privileged \
            --name=$containerName \
            --net=${Consts.dockerNetworkNamePrefix}${Consts.systemInternalVni} \
            --entrypoint=/bin/bash \
            --mac-address=${Config.get().config.virtualMac} \
            ${Config.get().mainArgs.imagePrefix}/tools-dhclient:${Config.get().getTagOf("tools-dhclient")} \
            -c 'sleep 20s'
        docker exec $containerName ip addr flush eth0
        set +e
        docker exec -d $containerName dhclient -4 --no-pid -cf /dhclient.conf eth0
        docker exec -d $containerName dhclient -6 --no-pid -cf /dhclient.conf eth0
        exit 0
        """.trimIndent()
      )
      Thread.sleep(8_500)
      val execResult = Utils.execute("docker exec $containerName ip addr show eth0", true)
      if (execResult.exitCode != 0) {
        throw Exception("failed to retrieve ips when running dhcp: exit: ${execResult.exitCode}, stderr: ${execResult.stderr}")
      }
      val ipv4 = ArrayList<Tuple<IPv4, Network>>()
      val ipv6 = ArrayList<Tuple<IPv6, Network>>()
      val lines = execResult.stdout.split("\n")
      for (line0 in lines) {
        val line = line0.trim()
        val split = line.split(" ")
        if (split.size < 2) {
          continue
        }
        if (line.startsWith("inet6")) {
          val v6 = split[1]
          val mask = try {
            Integer.parseInt(v6.substring(v6.lastIndexOf("/") + 1))
          } catch (ignore: NumberFormatException) {
            // invalid mask
            continue
          }
          if (mask < 0 || mask > 128) {
            continue
          }
          if (!v6.contains("/")) {
            // not network
            continue
          }
          val v6ip = v6.substring(0, v6.lastIndexOf("/"))
          val ip = try {
            IP.from(v6ip) as IPv6
          } catch (ignore: RuntimeException) {
            // invalid v6 address
            continue
          }
          if (ip.isLinkLocalAddress) {
            // skip link local address
            continue
          }
          ipv6.add(Tuple(ip, Network.eraseToNetwork(ip, mask)))
        } else if (line.startsWith("inet")) {
          val v4 = split[1]
          if (!v4.contains("/")) {
            // not network
            continue
          }
          val mask = try {
            Integer.parseInt(v4.substring(v4.lastIndexOf("/") + 1))
          } catch (ignore: NumberFormatException) {
            // invalid mask
            continue
          }
          if (mask < 0 || mask > 32) {
            continue
          }
          val v4ip = v4.substring(0, v4.lastIndexOf("/"))
          val ip = try {
            IP.from(v4ip) as IPv4
          } catch (ignore: RuntimeException) {
            // invalid v4 address
            continue
          }
          ipv4.add(Tuple(ip, Network.eraseToNetwork(ip, mask)))
        }
      }
      Logger.alert("dhcp v4: $ipv4")
      Logger.alert("dhcp v6: $ipv6")

      val v4 = if (ipv4.isEmpty()) {
        null
      } else {
        ipv4[0]
      }
      if (v4 == null) { // will not select a network without ipv4
        return null
      }
      val v6 = if (ipv6.isEmpty()) {
        null
      } else {
        ipv6[0]
      }
      return Tuple4(v4.left, v4.right, v6?.left, v6?.right)
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed running dhcp", e)
      return null
    }
  }
}
