package io.vproxy.vpss

import io.vproxy.app.app.Application
import io.vproxy.app.app.cmd.SystemCommand
import io.vproxy.app.app.cmd.handle.resource.SwitchHandle
import io.vproxy.app.plugin.PluginInitParams
import io.vproxy.base.component.elgroup.EventLoopGroup
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.*
import io.vproxy.base.util.thread.VProxyThread
import io.vproxy.component.secure.SecurityGroup
import io.vproxy.vfd.IPPort
import io.vproxy.vfd.UDSPath
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.launch.*
import io.vproxy.vpss.network.VPSSPacketFilter
import io.vproxy.vpss.network.VPSSPreHandler
import io.vproxy.vpss.service.impl.FlowServiceImpl
import io.vproxy.vpss.service.impl.UpgradeServiceImpl
import io.vproxy.vpss.service.impl.VPWSAgentServiceImpl
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global
import io.vproxy.vpss.util.VPSSUtils
import io.vproxy.xdp.NativeXDP
import java.io.File
import java.util.*
import kotlin.system.exitProcess

object Main {
  private val HELP_STR = """
    Usage:
      help|--help|-help|-h                        show this message
      version|--version|-version|-v               retrieve vpss and vproxy version
      --skip-dhcp=<true|false>                    skip dhcp step and launch with a default network
      --no-session-timeout=<true|false>           disable http login session timeout
      --include-network-interfaces=<n[,n]>        handle extra virtual interfaces specified in this argument
      --ignore-network-interfaces=<n[,n]>         ignore network interfaces, split by ','
      --image-prefix=<>                           image prefix used when creating containers
  """.trimIndent()

  @JvmStatic
  fun main(args0: Array<String>) {
    val args = MainUtils.checkFlagDeployInArguments(args0)
    System.setProperty("vfd", "posix")
    for (arg in args) {
      if (arg == "version" || arg == "--version" || arg == "-version" || arg == "-v") {
        @Suppress("RemoveRedundantQualifierName")
        println("vpss: " + io.vproxy.vpss.util.Consts.VERSION)
        @Suppress("RemoveRedundantQualifierName")
        println("vproxy: " + io.vproxy.base.util.Version.VERSION)
        return
      } else if (arg.startsWith("help") || arg.startsWith("--help") || arg.startsWith("-help") || arg.startsWith("-h")) {
        println(HELP_STR)
        return
      } else if (arg.startsWith("--skip-dhcp=")) {
        val v = arg.substring("--skip-dhcp=".length)
        Config.get().mainArgs.skipDhcp = when (v) {
          "true" -> true
          "false" -> false
          else -> throw Exception("unexpected value for skip-dhcp: $v")
        }
        Logger.alert("skip-dhcp: ${Config.get().mainArgs.skipDhcp}")
      } else if (arg.startsWith("--no-session-timeout=")) {
        val v = arg.substring("--no-session-timeout=".length)
        Config.get().mainArgs.noSessionTimeout = when (v) {
          "true" -> true
          "false" -> false
          else -> throw Exception("unexpected value for no-session-timeout: $v")
        }
        Logger.alert("no-session-timeout: ${Config.get().mainArgs.noSessionTimeout}")
      } else if (arg.startsWith("--include-network-interfaces=")) {
        val v = arg.substring("--include-network-interfaces=".length).split(",")
          .filter { it.isNotBlank() }.map { it.trim() }
        Config.get().mainArgs.includeIfaces = v
        Logger.alert("include-network-interfaces: ${Config.get().mainArgs.includeIfaces}")
      } else if (arg.startsWith("--ignore-network-interfaces=")) {
        val v = arg.substring("--ignore-network-interfaces=".length).split(",")
          .filter { it.isNotBlank() }.map { it.trim() }
        Config.get().mainArgs.ignoreIfaces = v
        Logger.alert("ignored-network-interfaces: ${Config.get().mainArgs.ignoreIfaces}")
      } else if (arg.startsWith("--image-prefix=")) {
        var v = arg.substring("--image-prefix=".length).trim()
        if (v.endsWith("/")) {
          v = v.substring(0, v.length - 1)
        }
        if (v != "") {
          Config.get().mainArgs.imagePrefix = v
        }
        Logger.alert("image-prefix: ${Config.get().mainArgs.imagePrefix}")
      } else {
        throw Exception("unknown argument: $arg")
      }
    }

    if (!OS.isLinux()) {
      Logger.warn(LogType.ALERT, "Please use a Linux distribution with kernel version >= 5.10, e.g. debian 11")
      exitProcess(1)
    }
    if ("root" != System.getProperty("user.name")) {
      Logger.warn(LogType.ALERT, "Please run with root")
      exitProcess(1)
    }
    val version = OS.version()
    val major = OS.major()
    val minor = OS.minor()
    if (major == -1) {
      Logger.error(LogType.ALERT, "Unable to parse kernel version: $version")
      exitProcess(1)
    }
    if (major < 5 || (major == 5 && minor < 4)) {
      Logger.warn(LogType.ALERT, "Please upgrade your kernel to at least 5.4, 5.10 is recommended")
      exitProcess(1)
    }

    try {
      launch()
    } catch (e: Exception) {
      e.printStackTrace()
      SignalTerminate.terminate()
    }
  }

  private fun launch() {
    SystemCommand.allowNonStdIOController = true
    Global.startTimeMillis // access the field to ensure it's loaded

    Application.createMinimum()
    val vpssSockFile = File("/var/run/vpss.sock")
    if (vpssSockFile.exists()) {
      vpssSockFile.delete()
    }
    Application.get().respControllerHolder.add(
      "resp-controller",
      UDSPath("/var/run/vpss.sock"),
      "vpss".toByteArray(),
    )
    vpssSockFile.deleteOnExit()

    val cores = Runtime.getRuntime().availableProcessors()

    val elg = EventLoopGroup("vpss-event-loop-group")
    val el = elg.add(
      "vpss-event-loop", Annotations(
        Collections.singletonMap(
          "vproxy/event-loop-core-affinity", (if (cores > 2) 0b100 else if (cores > 1) 0b010 else 0b001).toString()
        )
      )
    )
    val loop = el.selectorEventLoop
    Global.setLoop(loop)

    val executorLoop = SelectorEventLoop.open()
    Global.setExecutorLoop(executorLoop)
    executorLoop.loop { VProxyThread.create(it, "executor-loop") }

    val sw = Application.get().switchHolder.add(
      "vpss", IPPort("255.255.255.255", 1), elg,
      SwitchHandle.MAC_TABLE_TIMEOUT, SwitchHandle.ARP_TABLE_TIMEOUT,
      SecurityGroup.denyAll()
    )
    sw.start()
    Global.setSwitch(sw)

    val freeMem: Long = VPSSUtils.getMemInfo().memFree.toLong() * 1024
    var chunkSize = 0
    var frameSize = 0
    for (entry in Consts.chunkSizeMemMap) {
      if (freeMem > entry.key) {
        chunkSize = entry.value.left
        frameSize = entry.value.right
        break
      }
    }
    if (chunkSize == 0) {
      throw OutOfMemoryError("no enough memory: $freeMem")
    }
    if (NativeXDP.supportUMemReuse) {
      Global.setUMem(sw.addUMem("umem0", chunkSize, Consts.ringSize, Consts.ringSize, frameSize))
      // create a xsk to hold the umem
      VPSSUtils.ensureVeth("vpss0", "vpss0sw")
      VPSSUtils.manageNic("vpss0sw", Consts.systemInternalVni, tryZC = false, includeSystemNics = true)
    }
    sw.addNetwork(Consts.systemInternalVni, Network.from("0.0.0.0/0"), Network.from("::/0"), Annotations())

    // init config
    Config.get()
    SignalTerminate.launch()
    try {
      DHCPLaunch.launch(Config.get().mainArgs.skipDhcp)
      NetworkLaunch.launch()
      IfacesLaunch.launch()
      SSHProxyLaunch.launch()
      LimitLaunch.launch()
      WBListLaunch.launch()
      FlowServiceImpl.launch()

      VPSSUtils.syncDockerNetworks()
      // run vpws-agent after syncing docker networks
      VPWSAgentServiceImpl.launch()
      // enable remote switch before other critical init operations because it sleeps for a while
      RemoteSwitchLaunch.launch()

      UpgradeServiceImpl.launch()
      ControllerLaunch.launch()
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "launching failed", e)
      SignalTerminate.terminate()
    }

    Global.getSwitch().addIfaceWatcher(VPSSPreHandler())
    val packetFilterPlugin = VPSSPacketFilter
    packetFilterPlugin.init(PluginInitParams(arrayOf("switch=vpss")))
    val pluginWrapper = Application.get().pluginHolder.register("vpss", packetFilterPlugin)
    pluginWrapper.enable()

    Logger.alert("################################")
    Logger.alert("** vpss launched successfully **")
    Logger.alert("################################")
  }
}
