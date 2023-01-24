package io.vproxy.vpss.controller

import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.service.UpgradeService
import io.vproxy.vpss.service.impl.UpgradeServiceImpl
import io.vproxy.vpss.util.Global
import io.vproxy.vpss.util.VPSSUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Suppress("UNUSED_PARAMETER")
class SysController(app: CoroutineHttp1Server) : BaseController(app) {
  private val upgradeService: UpgradeService = UpgradeServiceImpl

  init {
    app.post("/api/sys/persist", handle(::persist))
    app.get("/api/sys/config.json", handle(::getConfig))
    app.post("/api/sys/shutdown", handle(::shutdown))
    app.post("/api/sys/reboot", handle(::reboot))
    app.get("/api/sys/info", handle(::getInfo))

    app.get("/api/sys/upgrade/versions", handle(::getUpgradeVersions))
    app.post("/api/sys/upgrade/check", handle(::checkForUpgrade))
    app.post("/api/sys/upgrade", handle(::checkAndUpgrade))
  }

  // return null
  private fun persist(ctx: RoutingContext): JSON.Instance<*>? {
    Config.get().save()
    Config.get().nicTombstone.clear()
    return null
  }

  // return { text: '', json: {...} }
  private fun getConfig(ctx: RoutingContext): JSON.Instance<*> {
    val json = Config.get().config.toJson()
    val text = json.pretty()
    return ObjectBuilder()
      .put("text", text)
      .putInst("json", json)
      .build()
  }

  // return null
  private fun shutdown(ctx: RoutingContext): JSON.Instance<*>? {
    Utils.execute(
      """
      #!/bin/bash
      docker run --name vpss-poweroff \
          --privileged --rm -d \
          -v /bin/systemctl:/bin/systemctl \
          -v /run/systemd/system:/run/systemd/system \
          -v /var/run/dbus/system_bus_socket:/var/run/dbus/system_bus_socket \
          -v /sys/fs/cgroup:/sys/fs/cgroup \
          --entrypoint /bin/bash ubuntu:20.04 -c 'sleep 2s && /bin/systemctl poweroff'
      """.trimIndent()
    )
    return null
  }

  // return null
  private fun reboot(ctx: RoutingContext): JSON.Instance<*>? {
    Utils.execute(
      """
      #!/bin/bash
      docker run --name vpss-reboot \
          --privileged --rm -d \
          -v /bin/systemctl:/bin/systemctl \
          -v /run/systemd/system:/run/systemd/system \
          -v /var/run/dbus/system_bus_socket:/var/run/dbus/system_bus_socket \
          -v /sys/fs/cgroup:/sys/fs/cgroup \
          --entrypoint /bin/bash ubuntu:20.04 -c 'sleep 2s && /bin/systemctl reboot'
      """.trimIndent()
    )
    return null
  }

  // return k:v
  private fun getInfo(ctx: RoutingContext): JSON.Instance<*> {
    val ret = ObjectBuilder()
    ret.put("currentTimeMillis", System.currentTimeMillis())
    ret.put("startTimeMillis", Global.startTimeMillis)
    kotlin.run {
      val content = try {
        Files.readString(Path.of("/proc/cpuinfo"))
      } catch (e: Exception) {
        Logger.error(LogType.FILE_ERROR, "failed to read /proc/cpuinfo", e)
        ""
      }
      val split = content.split("\n")
      var modelName = "unknown"
      var count = 0
      for (line in split) {
        if (line.startsWith("model name") && line.contains(":")) {
          modelName = line.substring(line.indexOf(":") + 1).trim()
          ++count
        }
      }
      ret.put("cpuModel", modelName)
      ret.put("cpuCount", count)
    }
    val memInfo = getMemInfo()
    for (k in memInfo.keySet()) {
      ret.putInst(k, memInfo[k])
    }
    kotlin.run {
      var env = "unknown"
      var kernel = "unknown"
      val res = Utils.execute("uname -r", true)
      if (res.exitCode == 0) {
        kernel = res.stdout.trim()
      }
      if (File("/.dockerenv").exists()) {
        env = "docker"
      } else {
        try {
          env = Files.readString(Path.of("/etc/issue")).trim()
        } catch (e: Exception) {
          Logger.error(LogType.FILE_ERROR, "failed to read /etc/issue", e)
        }
      }
      ret.put("runEnv", env)
      ret.put("kernelVersion", kernel)
    }
    return ret.build()
  }

  private fun getMemInfo(): JSON.Object {
    val memInfo = VPSSUtils.getMemInfo()
    return ObjectBuilder()
      .put("memTotal", memInfo.memTotal)
      .put("memFree", memInfo.memFree)
      .build()
  }

  // return {"upgrade": bool, "vpss": {"current": ..., "latest": ...}, "vproxy": {"current": ..., "latest": ...}}
  private fun getUpgradeVersions(ctx: RoutingContext): JSON.Instance<*> {
    return ObjectBuilder()
      .put("upgrade", upgradeService.requireUpgrading())
      .putObject("vpss") {
        put("current", io.vproxy.vpss.util.Consts.VERSION)
        put("latest", upgradeService.peekLatestVersion())
      }
      .putObject("vproxy") {
        put("current", io.vproxy.base.util.Version.VERSION)
        put("latest", upgradeService.peekLatestVProxyVersion())
      }
      .build()
  }

  // return {"vpss": "...", "vproxy": "...", "upgrade": bool}
  private suspend fun checkForUpgrade(ctx: RoutingContext): JSON.Instance<*> {
    upgradeService.checkForUpdates()
    return ObjectBuilder()
      .put("vpss", upgradeService.getLatestVersion())
      .put("vproxy", upgradeService.getLatestVProxyVersion())
      .put("upgrade", upgradeService.requireUpgrading())
      .build()
  }

  // return {"upgrade": bool}
  private suspend fun checkAndUpgrade(ctx: RoutingContext): JSON.Instance<*> {
    upgradeService.checkForUpdates()
    val res = upgradeService.upgrade()
    return ObjectBuilder()
      .put("upgrade", res)
      .build()
  }
}
