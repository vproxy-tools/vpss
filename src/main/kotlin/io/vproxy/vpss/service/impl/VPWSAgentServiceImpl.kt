package io.vproxy.vpss.service.impl

import io.vproxy.base.selector.TimerEvent
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.base.util.anno.Blocking
import io.vproxy.lib.common.await
import io.vproxy.lib.common.execute
import io.vproxy.lib.common.launch
import io.vproxy.vproxyx.websocks.ConfigLoader
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.service.VPWSAgentService
import io.vproxy.vpss.service.VPWSAgentStatus
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global
import java.io.File
import java.nio.file.Files

@Suppress("BlockingMethodInNonBlockingContext")
object VPWSAgentServiceImpl : VPWSAgentService {
  private const val maxRetryTimes = 10
  private const val initialRetryDelay = 2_000
  private const val maxRetryDelay = 30_000
  private const val launchingPeriod = 15_000

  private var currentRetryCount = 0
  private var currentRetryDelay = initialRetryDelay
  private var launchTs: Long = -1

  private var delayTimer: TimerEvent? = null

  private fun resetRetryFields() {
    currentRetryCount = 0
    currentRetryDelay = initialRetryDelay
  }

  fun launch() {
    val res = Utils.execute("docker container inspect ${Consts.vpwsAgentContainerName}", true)
    if (res.exitCode != 0 && res.stderr.trim() == "Error: No such container: ${Consts.vpwsAgentContainerName}") {
      // do nothing
    } else if (res.exitCode == 0) {
      Logger.alert("container ${Consts.vpwsAgentContainerName} already exists")
    } else {
      val err = "failed to retrieve info about container ${Consts.vpwsAgentContainerName}"
      Logger.error(LogType.SYS_ERROR, err)
      throw Exception(err)
    }

    Global.getExecutorLoop().execute { ensureContainer(false) }.block()
  }

  private suspend fun executeEnsureContainer(restart: Boolean): VPWSAgentStatus {
    return Global.getExecutorLoop().execute {
      ensureContainer(restart)
    }.await()
  }

  private fun setDelayTimer() {
    Global.getExecutorLoop().nextTick {
      clearDelayTimer()
      val delayLong = if (launchTs == (-1).toLong()) Integer.MAX_VALUE.toLong() else System.currentTimeMillis() - launchTs
      var delay = Integer.MAX_VALUE
      if (delayLong < Integer.MAX_VALUE) {
        delay = delayLong.toInt()
      }
      delay = if (delay > currentRetryDelay) {
        currentRetryDelay
      } else {
        currentRetryDelay - delay
      }
      Logger.warn(LogType.ALERT, "set delay timer for launching vpws-agent: ${delay}ms")
      delayTimer = Global.getExecutorLoop().delay(delay) { ensureContainer(false) }
    }
  }

  private fun clearDelayTimer() {
    if (delayTimer != null) {
      delayTimer!!.cancel()
      delayTimer = null
    }
  }

  @Blocking
  private fun ensureContainer(restart: Boolean): VPWSAgentStatus {
    clearDelayTimer()
    val res = Utils.execute("docker container inspect ${Consts.vpwsAgentContainerName}", true)
    if (res.exitCode != 0 && res.stderr.trim() == "Error: No such container: ${Consts.vpwsAgentContainerName}") {
      // fall through
    } else if (res.exitCode == 0) {
      // already exists
      if (restart) {
        Logger.warn(LogType.ALERT, "terminating container ${Consts.vpwsAgentContainerName}")
        try {
          Utils.execute("docker kill ${Consts.vpwsAgentContainerName}")
        } catch (e: Exception) {
          Logger.error(LogType.SYS_ERROR, "failed to kill vpws-agent", e)
          throw e
        }
        // fall through
      } else {
        val time = System.currentTimeMillis() - launchTs
        return if (time > launchingPeriod) {
          resetRetryFields()
          VPWSAgentStatus.running
        } else {
          Logger.trace(LogType.ALERT, "container exists for ${time}ms, check again later")
          setDelayTimer()
          VPWSAgentStatus.starting
        }
      }
    } else {
      Logger.error(LogType.SYS_ERROR, "failed to retrieve info about container ${Consts.vpwsAgentContainerName}: ${res.stderr}")
      setDelayTimer()
      return VPWSAgentStatus.unknown
    }
    val current = System.currentTimeMillis()
    if (current - launchTs < currentRetryDelay) {
      setDelayTimer()
      return VPWSAgentStatus.pending
    }
    if (currentRetryCount >= maxRetryTimes) {
      return VPWSAgentStatus.stopped
    }
    setDelayTimer()
    ++currentRetryCount
    currentRetryDelay *= 2
    if (currentRetryDelay > maxRetryDelay) {
      currentRetryDelay = maxRetryDelay
    }
    Logger.warn(LogType.ALERT, "container ${Consts.vpwsAgentContainerName} does not exist, need to create")

    val vpwsAgentConf = File("/etc/vpss/vpws-agent.conf")
    if (!vpwsAgentConf.exists()) {
      Files.writeString(vpwsAgentConf.toPath(), DEFAULT_VPWS_AGENT_CONF)
    }
    try {
      Utils.execute(
        "docker run --rm -d --privileged --name ${Consts.vpwsAgentContainerName} " +
          "-v /etc/vpss/vpws-agent.conf:/vpws-agent.conf:ro " +
          "-v /lib/modules:/lib/modules:ro " +
          "-v /root:/root " +
          "--mac-address ${Config.get().config.vpwsAgentMac} " +
          "--net=vpss-net1 " +
          Config.get().mainArgs.imagePrefix + "/vpws-agent-with-dhclient:${Config.get().getTagOf("vpws-agent-with-dhclient")} " +
          "/vpws-agent.conf"
      )
      try {
        Utils.execute(
          "docker exec ${Consts.vpwsAgentContainerName} /bin/bash -c '" +
            "ip route add local ${Consts.vpwsNetwork} dev lo src 127.0.0.1 && " +
            "iptables -t mangle -I PREROUTING -d ${Consts.vpwsNetwork} -p tcp -j TPROXY --on-port=8888 --on-ip=127.0.0.1 && " +
            " iptables -t nat -I PREROUTING -p udp --dport 53 -j REDIRECT --to-port 53 && " +
            "ip6tables -t nat -I PREROUTING -p udp --dport 53 -j REDIRECT --to-port 53" +
            "'"
        )
      } catch (e: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to add route and iptables rules", e)
        try {
          Utils.execute("docker stop ${Consts.vpwsAgentContainerName}")
        } catch (e: Exception) {
          Logger.error(
            LogType.SYS_ERROR,
            "failed to delete container ${Consts.vpwsAgentContainerName} when adding route and iptables rules failed",
            e
          )
        }
        throw e
      }
      Logger.info(LogType.ALERT, "vpws-agent launched")
      launchTs = System.currentTimeMillis()
    } catch (e: Exception) {
      Logger.error(LogType.SYS_ERROR, "failed to launch vpws-agent", e)
      throw e
    }
    return VPWSAgentStatus.starting
  }

  override suspend fun getStatus(): VPWSAgentStatus {
    return executeEnsureContainer(false)
  }

  override suspend fun updateConfigAndRestart(conf: String) {
    val loader = ConfigLoader()
    Global.getExecutorLoop().execute {
      resetRetryFields()
      val f = Utils.writeTemporaryFile("vpws-agent", ".conf", conf.toByteArray())
      loader.load(f)
      val errLs = loader.validate()
      if (errLs.isNotEmpty()) {
        throw Exception(errLs.toString())
      }
      Utils.writeFileWithBackup("/etc/vpss/vpws-agent.conf", conf)
    }.await()
    Global.getExecutorLoop().launch { // do not wait for the result here
      executeEnsureContainer(true)
    }
  }

  private val DEFAULT_VPWS_AGENT_CONF = """
  # example config
  # you may use https://vproxy-tools.github.io/vpwsui/index.html to generate the config

  {
    agent {
      dns.listen = 53

      direct-relay {
        enabled = true
        ip-range = 100.96.0.0/12
        listen = 127.0.0.1:8888
        ip-bond-timeout = 0
      }
    }

    proxy {
      auth = user:password
    }

    groups = [
      {
        servers = [
          websockss://127.0.0.1:55555
        ]
        domains = [
          /.*\.google\..*/
          youtube.com
          github.com
        ]
      }
    ]
  }
  """.trimIndent().trim() + "\n"
}
