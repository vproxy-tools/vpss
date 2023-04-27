package io.vproxy.vpss.util

import io.vproxy.base.util.Annotations
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.base.util.coll.Tuple
import io.vproxy.base.util.exception.NotFoundException
import io.vproxy.base.util.exception.XException
import io.vproxy.base.util.net.Nic
import io.vproxy.base.util.ratelimit.SimpleRateLimiter
import io.vproxy.base.util.ratelimit.StatisticsRateLimiter
import io.vproxy.commons.util.IOUtils
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.*
import io.vproxy.vswitch.RouteTable
import io.vproxy.vswitch.VirtualNetwork
import io.vproxy.vswitch.dispatcher.BPFMapKeySelectors
import io.vproxy.vswitch.iface.Iface
import io.vproxy.vswitch.iface.VLanAdaptorIface
import io.vproxy.vswitch.iface.XDPIface
import io.vproxy.xdp.*
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

object VPSSUtils {
  private val internalNicNamePattern = Pattern.compile("^(vproxy|vpss)\\d+(sw)?$")

  fun getNetworkInterfaces(includeSystemNics: Boolean = false): List<Nic> {
    val ls = Utils.getNetworkInterfaces()
    return ls.filter { nicShouldBeHandled(it, includeSystemNics) }
  }

  private fun nicShouldBeHandled(nic: Nic, includeSystemNics: Boolean): Boolean {
    if (includeSystemNics) {
      if (internalNicNamePattern.matcher(nic.name).matches()) {
        return true
      }
    }
    if (Config.get().mainArgs.ignoreIfaces.contains(nic.name)) {
      return false
    }
    if (Config.get().mainArgs.includeIfaces.contains(nic.name)) {
      return true
    }
    return !nic.isVirtual
  }

  fun ensureVeth(name: String, peer: String) {
    val nics = getNetworkInterfaces(includeSystemNics = true)
    var nameExists = false
    var peerExists = false
    for (nic in nics) {
      if (nic.name == name) {
        nameExists = true
      } else if (nic.name == peer) {
        peerExists = true
      }
    }
    if (nameExists && peerExists) {
      return
    }
    if (nameExists) {
      throw Exception("nic $name exists but $peer doesn't")
    }
    if (peerExists) {
      throw Exception("nic $peer exists but $name doesn't")
    }
    // not found
    createVeth(name, peer)
  }

  fun createVeth(name: String, peer: String) {
    Utils.execute(
      """
      #!/bin/bash
      set -x
      set -e
      ip link add $name type veth peer name $peer
      ip link set $name up
      ip link set $peer up
      ethtool -K $name tx off
      set +e
      ethtool -K $name tso off
      ethtool -K $name ufo off
      ethtool -K $name gso off
      set -e
      ethtool -K $peer tx off
      set +e
      ethtool -K $peer tso off
      ethtool -K $peer ufo off
      ethtool -K $peer gso off
      set -e
      """.trimIndent()
    )
  }

  fun unmanageNic(name: String) {
    val iface = Global.getSwitch().ifaces.find { it.name() == "xdp:$name" }
    if (iface != null) {
      val xdp = iface as XDPIface
      xdp.xskMap.bpfObject.release(true)
    }
    try {
      NativeXDP.get().detachBPFProgramFromNic(name)
    } catch (ignore: Exception) {
    }
    try {
      Global.getSwitch().delIface("xdp:$name")
    } catch (ignore: NotFoundException) {
    }
    try {
      Global.getSwitch().delUMem(name)
    } catch (ignore: NotFoundException) {
    }
  }

  fun manageNic(
    name: String,
    vni: Int,
    checkMac: Boolean = false,
    tryDriverMode: Boolean = true,
    tryZC: Boolean = true,
    rxGenChecksum: Boolean = false,
    includeSystemNics: Boolean = false,
  ): XDPIface {
    unmanageNic(name)

    Utils.execute(
      """
      #!/bin/bash
      set -x
      set -e

      ip link set $name up
      ip link set $name promisc on
      set +e
      ethtool -L $name rx 0 tx 0 combined 1
      ethtool -K $name txvlan off
      ethtool -K $name rxvlan off
      ethtool -K $name rx-vlan-filter off
      exit 0
      """.trimIndent()
    )

    val nics = getNetworkInterfaces(includeSystemNics)
    var chosen: Nic? = null
    for (nic in nics) {
      if (nic.name == name) {
        chosen = nic
        break
      }
    }
    if (chosen == null) {
      throw Exception("nic $name not found")
    }
    val bytes = if (checkMac) {
      BPFObject.skipDstMacProgram(chosen.mac)
    } else {
      BPFObject.handleAllProgram()
    }
    val file = IOUtils.writeTemporaryFile("bpf-$name", "o", bytes.toJavaArray())
    val tup = try {
      if (!tryDriverMode) {
        manageNicSKB(file, chosen, vni, rxGenChecksum)
      } else if (tryZC) {
        manageNicTryDrv(file, chosen, vni, rxGenChecksum)
      } else {
        manageNicTryDrvNoZC(null, file, chosen, vni, rxGenChecksum)
      }
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to manage ${chosen.name}", e)
      throw e
    }
    return tup.right
  }

  fun getNicMac(nicname: String): MacAddress {
    val nics = getNetworkInterfaces(includeSystemNics = true)
    for (nic in nics) {
      if (nic.name == nicname) {
        return nic.mac
      }
    }
    throw Exception("$nicname not found")
  }

  private fun ensureUMem(name: String): UMem {
    var umem = Global.getUMem()
    if (umem != null) {
      return umem
    }
    umem = Global.getSwitch().uMems.find { it.alias == name }
    if (umem != null) {
      return umem
    }
    umem = Global.getSwitch().addUMem(name, Consts.ringSize * 2, Consts.ringSize, Consts.ringSize, 4096)
    return umem
  }

  private fun destroyUMem(name: String) {
    val umem = Global.getUMem()
    if (umem != null && umem.alias == name) {
      return
    }
    Global.getSwitch().delUMem(name)
  }

  @Suppress("DuplicatedCode")
  private fun manageNicTryDrv(file: String, nic: Nic, vni: Int, rxGenChecksum: Boolean): Tuple<BPFObject, XDPIface> {
    Logger.alert("manage ${nic.name}: try driver mode")
    val bpf = try {
      BPFObject.loadAndAttachToNic(file, BPFObject.DEFAULT_XDP_PROG_NAME, nic.name, BPFMode.DRIVER, true)
    } catch (e: Exception) {
      Logger.warn(LogType.ALERT, "unable to use driver mode ebpf on ${nic.name}, fallback to skb mode", e)
      return manageNicSKB(file, nic, vni, rxGenChecksum)
    }
    val map = getBPFMapOrReleaseAndThrow(bpf, BPFObject.DEFAULT_XSKS_MAP_NAME)
    val umem = try {
      ensureUMem(nic.name)
    } catch (e: Exception) {
      try {
        bpf.release(true)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to detach ebpf from ${nic.name} when ensuring umem failed", e2)
      }
      throw e
    }
    try {
      val xdp = Global.getSwitch().addXDP(
        nic.name,
        map, null,
        umem,
        0,
        Consts.ringSize,
        Consts.ringSize,
        BPFMode.DRIVER,
        true,
        0,
        rxGenChecksum,
        vni,
        BPFMapKeySelectors.useQueueId.keySelector.get(),
        false
      )
      Logger.alert("managing ${nic.name} using driver + zerocopy mode")
      return Tuple(bpf, xdp)
    } catch (e: Exception) {
      Logger.warn(LogType.ALERT, "unable to use driver + zerocopy on xsk ${nic.name}, fallback to driver + non-zerocopy", e)
      try {
        destroyUMem(umem.alias)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to destroy umem ${umem.alias} when driver + zerocopy failed", e2)
      }
      return manageNicTryDrvNoZC(bpf, file, nic, vni, rxGenChecksum)
    }
  }

  @Suppress("DuplicatedCode")
  private fun manageNicTryDrvNoZC(_bpf: BPFObject?, file: String, nic: Nic, vni: Int, rxGenChecksum: Boolean): Tuple<BPFObject, XDPIface> {
    var bpf = _bpf
    if (bpf == null) {
      bpf = try {
        BPFObject.loadAndAttachToNic(file, BPFObject.DEFAULT_XDP_PROG_NAME, nic.name, BPFMode.DRIVER, true)
      } catch (e: Exception) {
        Logger.warn(LogType.ALERT, "unable to use driver mode ebpf on ${nic.name}, fallback to skb mode", e)
        return manageNicSKB(file, nic, vni, rxGenChecksum)
      }
      bpf!!
    }
    val map = getBPFMapOrReleaseAndThrow(bpf, BPFObject.DEFAULT_XSKS_MAP_NAME)
    val umem = try {
      ensureUMem(nic.name)
    } catch (e: Exception) {
      try {
        bpf.release(true)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to detach ebpf from ${nic.name} when ensuring umem failed", e2)
      }
      throw e
    }
    try {
      val xdp = Global.getSwitch().addXDP(
        nic.name,
        map, null,
        umem,
        0,
        Consts.ringSize,
        Consts.ringSize,
        BPFMode.DRIVER,
        false,
        0,
        rxGenChecksum,
        vni,
        BPFMapKeySelectors.useQueueId.keySelector.get(),
        false
      )
      Logger.alert("managing ${nic.name} using driver + non-zerocopy mode")
      return Tuple(bpf, xdp)
    } catch (e2: Exception) {
      Logger.warn(LogType.ALERT, "unable to use driver + non-zerocopy on xsk ${nic.name}, fallback to skb mode", e2)
      try {
        destroyUMem(umem.alias)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to destroy umem ${umem.alias} when driver + non-zerocopy failed", e2)
      }
      try {
        bpf.release(true)
      } catch (e3: Exception) {
        Logger.error(LogType.SYS_ERROR, "detaching driver mode ebpf on ${nic.name} failed", e3)
        // ignore the error and continue to handle
      }
      return manageNicSKB(file, nic, vni, rxGenChecksum)
    }
  }

  private fun getBPFMapOrReleaseAndThrow(bpf: BPFObject, @Suppress("SameParameterValue") name: String): BPFMap {
    return try {
      bpf.getMap(name)
    } catch (e: Exception) {
      try {
        bpf.release(true)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to get map $name from bpf object, also failed when releasing the bpf object", e2)
      }
      throw e
    }
  }

  private fun manageNicSKB(file: String, nic: Nic, vni: Int, rxGenChecksum: Boolean): Tuple<BPFObject, XDPIface> {
    Logger.alert("manage ${nic.name}: skb mode")
    val umem = ensureUMem(nic.name)
    val bpf = try {
      BPFObject.loadAndAttachToNic(file, BPFObject.DEFAULT_XDP_PROG_NAME, nic.name, BPFMode.SKB, true)
    } catch (e: Exception) {
      try {
        destroyUMem(umem.alias)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to delete umem ${umem.alias} when loading bpf program on ${nic.name} failed", e2)
      }
      throw e
    }
    val map = try {
      getBPFMapOrReleaseAndThrow(bpf, BPFObject.DEFAULT_XSKS_MAP_NAME)
    } catch (e: Exception) {
      try {
        destroyUMem(umem.alias)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to delete umem ${umem.alias} when retrieving map from ebpf object failed", e2)
      }
      throw e
    }
    val xdp = try {
      Global.getSwitch().addXDP(
        nic.name,
        map, null,
        umem,
        0,
        Consts.ringSize,
        Consts.ringSize,
        BPFMode.SKB,
        false,
        0,
        rxGenChecksum,
        vni,
        BPFMapKeySelectors.useQueueId.keySelector.get(),
        false,
      )
    } catch (e: Exception) {
      try {
        bpf.release(true)
      } catch (e2: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to release the bpf object when failed to use SKB mode on ${nic.name}", e2)
      }
      throw e
    }
    Logger.alert("managing ${nic.name} using skb mode")
    return Tuple(bpf, xdp)
  }

  fun syncDockerNetworks() {
    val res = Utils.execute(" docker network ls | grep '${Consts.dockerNetworkNamePrefix}' | awk '{print $2}'", true)
    if (res.exitCode != 0) {
      throw Exception("failed to retrieve docker networks $res")
    }
    val output = res.stdout
    val dockerNetworks = HashMap<String, Int>()
    val lines = output.split("\n")
    for (line0 in lines) {
      val line = line0.trim()
      if (line.isEmpty()) {
        continue
      }
      if (line.length < Consts.dockerNetworkNamePrefix.length) {
        throw Exception("unexpected output of docker network list: ${res.stdout}")
      }
      val vniStr = line.substring(Consts.dockerNetworkNamePrefix.length)
      if (!Utils.isInteger(vniStr)) {
        throw Exception("unexpected output of docker network list: ${res.stdout}")
      }
      dockerNetworks[line] = Integer.parseInt(vniStr)
    }
    val expectedNetworks = HashMap<String, Int>()
    for (vni in Global.getSwitch().networks.keySet()) {
      expectedNetworks[Consts.dockerNetworkNamePrefix + "$vni"] = vni
    }
    // always ensures network 1
    expectedNetworks[Consts.dockerNetworkNamePrefix + "1"] = 1

    for (en in expectedNetworks) {
      if (!dockerNetworks.containsKey(en.key)) {
        createDockerNetwork(en.key, en.value)
        try {
          manageVethLinkForNetwork(en.value)
        } catch (e: Exception) {
          Logger.error(LogType.SYS_ERROR, "failed to manage veth link for docker network ${en.key}", e)
          try {
            deleteDockerNetwork(en.key)
          } catch (e2: Exception) {
            Logger.error(LogType.SSL_ERROR, "failed to delete docker network ${en.key} when managing veth link for network failed", e)
          }
          throw e
        }
      }
    }
    for (dn in dockerNetworks) {
      if (!expectedNetworks.containsKey(dn.key)) {
        unmanageVethLinkForNetwork(dn.value)
        deleteDockerNetwork(dn.key)
      }
    }
    for (en in expectedNetworks) {
      try {
        ensureVethLinkForNetworkManaged(en.value)
      } catch (e: Exception) {
        Logger.error(LogType.SYS_ERROR, "virtual nic for network ${en.value} exists but failed to manage it", e)
      }
    }
  }

  @Suppress("RemoveRedundantQualifierName")
  private fun createDockerNetwork(name: String, vni: Int) {
    val v4 = kotlin.ByteArray(4)
    val v6 = kotlin.ByteArray(16)
    if (vni >= (1.shl(16))) {
      val n = vni.shr(16).and(0xff).toByte()
      v4[0] = n
      v6[12] = n
    }
    if (vni >= (1.shl(8))) {
      val n = vni.shr(8).and(0xff).toByte()
      v4[1] = n
      v6[13] = n
    }
    val n = vni.and(0xff).toByte()
    v4[2] = n
    v6[14] = n

    v4[3] = 0
    v6[15] = 0
    val v4str = IP.from(v4).formatToIPString()
    var v6str = IP.from(v6).formatToIPString()
    if (v6str.startsWith("[")) {
      v6str = v6str.substring(1, v6str.length - 1)
    }
    Utils.execute(
      "docker network create " +
        "--driver=${Config.get().mainArgs.imagePrefix}/docker-plugin:latest " +
        "--ipv6 " +
        "--subnet=" + v4str + "/24 " +
        "--subnet=" + v6str + "/120 " +
        "--opt 'docker-plugin.vproxy.io/network-vni'='$vni' " +
        "--opt 'docker-plugin.vproxy.io/network-subnet-v4'='0.0.0.0/0' " +
        "--opt 'docker-plugin.vproxy.io/network-subnet-v6'='::/0' " +
        name
    )
  }

  private fun manageVethLinkForNetwork(vni: Int) {
    manageNic("vproxy$vni", vni, tryZC = false, includeSystemNics = true)
  }

  private fun ensureVethLinkForNetworkManaged(vni: Int) {
    val nic = "vproxy$vni"
    val iface = Global.getSwitch().ifaces.find { it.name() == "xdp:$nic" }
    if (iface == null) {
      manageVethLinkForNetwork(vni)
    }
  }

  private fun deleteDockerNetwork(name: String) {
    Utils.execute("docker network rm $name")
  }

  private fun unmanageVethLinkForNetwork(vni: Int) {
    unmanageNic("vproxy$vni")
  }

  fun getIface(name: String): Iface? {
    for (iface in Global.getSwitch().ifaces) {
      val ifaceName = iface.name()
      if (name.startsWith("vlan.")) { // full match
        if (ifaceName == name) {
          return iface
        }
      } else {
        if (ifaceName == "xdp:$name") { // match the actual name part
          return iface
        }
      }
    }
    return null
  }

  fun syncLocalIPs() {
    val ifacesMap = HashMap<String, Interface>()
    for (iface in Config.get().config.ifaces) {
      ifacesMap[iface.name!!] = iface
    }
    val ipsMap = HashMap<IP, NetworkInterface>()
    for (nic in NetworkInterface.getNetworkInterfaces()) {
      for (inet in nic.inetAddresses) {
        val ip = IP.from(inet.address)
        ipsMap[ip] = nic
      }
    }
    for (net in Global.getSwitch().networks.values()) {
      for (ipmac in ArrayList(net.ips.entries())) {
        if (!ipmac.annotations.raw.containsKey(Consts.IPAnnotationLoadedFromNic) || ipmac.annotations.raw[Consts.IPAnnotationLoadedFromNic] != "true") {
          continue
        }
        val ip = ipmac.ip
        val nic = ipsMap[ip]
        if (nic != null) {
          val iface = ifacesMap[nic.name]
          if (iface != null) {
            if (iface.vni == net.vni) {
              continue
            }
          }
        }
        Logger.warn(LogType.ALERT, "ip ${ip.formatToIPString()} removed from network ${net.vni}")
        net.ips.del(ip)
      }
    }
    for (entry in ipsMap) {
      val ip = entry.key
      if (ip.isLinkLocalAddress) {
        continue
      }
      val nic = entry.value
      val iface = ifacesMap[nic.name] ?: continue
      val net = try {
        Global.getSwitch().getNetwork(iface.vni)
      } catch (e: NotFoundException) {
        Logger.warn(LogType.ALERT, "skip handling ip ${ip.formatToIPString()} on ${iface.name} because network ${iface.vni} does not exist")
        continue
      }
      if (net.ips.lookup(ip) != null) {
        continue
      }
      val mac = MacAddress(nic.hardwareAddress)
      try {
        val addedip = net.ips.__add(ip, mac, true, Annotations(java.util.Map.of(Consts.IPAnnotationLoadedFromNic, "true")))
        addedip.routing = false
        Logger.alert("recorded ip from nic ${iface.name}: ${ip.formatToIPString()} $mac")
      } catch (e: Exception) {
        Logger.warn(LogType.ALERT, "failed to add ip ${ip.formatToIPString()} $mac, skipping ...", e)
      }
    }
  }

  fun syncSystemNetEntity(network: VirtualNetwork, net: Net) {
    net.ips.removeIf { it.system }
    net.routes.removeIf { it.system }

    net.v4net = network.v4network
    net.v6net = network.v6network

    for ((idx, ip) in network.ips.entries().withIndex()) {
      net.ips.add(idx, NetIP(ip = ip.ip, mac = ip.mac, routing = ip.routing, system = true))
    }
    for ((idx, r) in network.routeTable.rules.withIndex()) {
      val route = buildNetRouteEntity(network.vni, r)
      route.system = true
      net.routes.add(idx, route)
    }
  }

  fun buildNetRouteEntity(vni: Int, r: RouteTable.RouteRule): NetRoute {
    val type: NetRouteType
    val argument: String
    if (r.toVni == vni) {
      type = NetRouteType.local
      argument = ""
    } else if (r.toVni != 0) {
      type = NetRouteType.intervlan
      argument = "" + r.toVni
    } else if (r.ip != null) {
      type = NetRouteType.gateway
      argument = r.ip.formatToIPString()
    } else {
      type = NetRouteType.unknown
      argument = ""
    }
    return NetRoute(r.alias, r.rule, type = type, argument = argument)
  }

  fun buildRuleFromNetRoute(vni: Int, netRoute: NetRoute): RouteTable.RouteRule {
    return when (netRoute.type) {
      NetRouteType.local -> {
        if (netRoute.argument != null && netRoute.argument!!.isNotBlank()) {
          throw XException(ErrorCode.badArgsInvalidRouteArgumentTakeNoArgs)
        }
        RouteTable.RouteRule(netRoute.name, netRoute.target, vni)
      }
      NetRouteType.gateway -> {
        val gw = try {
          IP.from(netRoute.argument)
        } catch (e: RuntimeException) {
          throw XException(ErrorCode.badArgsInvalidRouteArgumentNotValidGatewayIp)
        }
        RouteTable.RouteRule(netRoute.name, netRoute.target, gw)
      }
      NetRouteType.intervlan -> {
        val targetVni = try {
          Integer.parseInt(netRoute.argument)
        } catch (e: RuntimeException) {
          throw XException(ErrorCode.badArgsInvalidRouteArgumentNotValidVni)
        }
        if (targetVni < 1) {
          throw XException(ErrorCode.badArgsInvalidRouteArgumentNotValidVni)
        }
        if (targetVni == vni) {
          throw XException(ErrorCode.badArgsInvalidRouteArgumentMustBeAnotherVni)
        }
        RouteTable.RouteRule(netRoute.name, netRoute.target, targetVni)
      }
      else -> {
        throw XException(ErrorCode.badArgsInvalidRouteType)
      }
    }
  }

  fun initStatisticsForIface(iface: Iface) {
    iface.putUserData(
      Consts.inputStatisticsKey, StatisticsRateLimiter(
        SimpleRateLimiter(-1, -1),
        Consts.recordingDuration, Consts.samplingRate
      )
    )
    iface.putUserData(
      Consts.outputStatisticsKey, StatisticsRateLimiter(
        SimpleRateLimiter(-1, -1),
        Consts.recordingDuration, Consts.samplingRate
      )
    )
  }

  fun formatIfaceName(iface: Iface): String = when (iface) {
    is XDPIface -> iface.nic
    is VLanAdaptorIface -> formatIfaceName(iface.parentIface) + "." + iface.remoteVLan
    else -> iface.name()
  }

  // kB
  data class MemInfo(val memTotal: Int, val memFree: Int)

  // kB
  fun getMemInfo(): MemInfo {
    val content = try {
      Files.readString(Path.of("/proc/meminfo"))
    } catch (e: Exception) {
      Logger.error(LogType.FILE_ERROR, "failed to read /proc/meminfo", e)
      ""
    }
    val split = content.split("\n")
    var memTotal = 0
    var memFree = 0
    for (line in split) {
      try {
        if (line.startsWith("MemTotal:")) {
          var x = line.substring("MemTotal:".length)
          if (x.contains("kB")) {
            x = x.substring(0, x.indexOf("kB"))
          }
          memTotal = Integer.parseInt(x.trim())
        } else if (line.startsWith("MemFree:")) {
          var x = line.substring("MemFree:".length)
          if (x.contains("kB")) {
            x = x.substring(0, x.indexOf("kB"))
          }
          memFree = Integer.parseInt(x.trim())
          // ZGC reports 3 times the memory it uses, so here we need to add 2 times the heap memory
          memFree += (Runtime.getRuntime().totalMemory() / 1024 * 2).toInt()
        }
      } catch (e: NumberFormatException) {
        Logger.error(LogType.INVALID_EXTERNAL_DATA, "failed to parse value for $line", e)
      }
    }
    return MemInfo(memTotal, memFree)
  }
}
