package io.vproxy.vpss.service.impl

import io.vproxy.app.app.Application
import io.vproxy.base.dns.Resolver
import io.vproxy.base.dns.VResolver
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.commons.util.IOUtils
import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.cs.UTF8ByteArrayCharStream
import io.vproxy.lib.common.await
import io.vproxy.lib.common.awaitCallback
import io.vproxy.lib.common.execute
import io.vproxy.lib.common.vplib
import io.vproxy.lib.http1.CoroutineHttp1ClientConnection
import io.vproxy.vpacket.dns.DNSClass
import io.vproxy.vpacket.dns.DNSResource
import io.vproxy.vpacket.dns.DNSType
import io.vproxy.vpacket.dns.rdata.TXT
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.service.UpgradeService
import io.vproxy.vpss.util.Consts
import java.io.IOException
import java.net.UnknownHostException

@Suppress("BlockingMethodInNonBlockingContext")
object UpgradeServiceImpl : UpgradeService {
  private var lastNewVersionCheckTime: Long = 0
  private const val newVersionCheckInterval = 60 * 60 * 1000
  private var latestVersion = UpgradeVersion()

  fun launch() {
    Application.get().controlEventLoop.selectorEventLoop.execute { tryCheckForUpdates() }
    Application.get().controlEventLoop.selectorEventLoop.period(newVersionCheckInterval / 2) { vplib.coroutine.launch { tryCheckForUpdates() } }
  }

  override fun peekLatestVersion(): String {
    return latestVersion.version
  }

  override fun peekLatestVProxyVersion(): String {
    return latestVersion.vproxyVersion
  }

  override suspend fun getLatestVersion(): String {
    if (System.currentTimeMillis() - lastNewVersionCheckTime > newVersionCheckInterval) {
      checkForUpdates()
    }
    return latestVersion.version
  }

  override suspend fun getLatestVProxyVersion(): String {
    if (System.currentTimeMillis() - lastNewVersionCheckTime > newVersionCheckInterval) {
      checkForUpdates()
    }
    return latestVersion.vproxyVersion
  }

  @Suppress("RemoveRedundantQualifierName")
  override fun requireUpgrading(): Boolean {
    if (latestVersion.version == "" || latestVersion.vproxyVersion == "") {
      return false
    }
    if (Utils.compareVProxyVersions(io.vproxy.vpss.util.Consts.VERSION, latestVersion.version) < 0) {
      return true
    }
    if (Utils.compareVProxyVersions(io.vproxy.base.util.Version.VERSION, latestVersion.vproxyVersion) < 0) {
      return true
    }
    return false
  }

  private suspend fun queryTXT(domain: String): String {
    val dnsClient = (Resolver.getDefault() as VResolver).client
    val answers = awaitCallback<List<DNSResource>, IOException> { dnsClient.request(domain, DNSType.TXT, DNSClass.IN, it) }
    for (answer in answers) {
      if (answer.type != DNSType.TXT) {
        assert(Logger.lowLevelDebug("answer type not TXT $answer"))
        continue
      }
      if (answer.rdata !is TXT) {
        assert(Logger.lowLevelDebug("answer rdata is not TXT $answer"))
        continue
      }
      val txt = answer.rdata as TXT
      if (txt.texts.isEmpty()) {
        assert(Logger.lowLevelDebug("txt contains no data $answer"))
        continue
      }
      assert(Logger.lowLevelDebug("got txt in $answer"))
      return txt.texts[0]
    }
    throw UnknownHostException(domain)
  }

  private suspend fun tryCheckForUpdates() {
    try {
      checkForUpdates()
    } catch (e: Exception) {
      Logger.error(LogType.ALERT, "failed to retrieve update info", e)
    }
  }

  override suspend fun checkForUpdates() {
    Logger.alert("begin to check for updates ...")
    val link = queryTXT("link.version.vpss.txt.vproxy.io.")
    Logger.alert("will request $link to get versions")
    val res = CoroutineHttp1ClientConnection.simpleGet(link).await()
    val ver = JSON.deserialize(UTF8ByteArrayCharStream(res.toJavaArray()), UpgradeVersion.rule)

    latestVersion = ver
    lastNewVersionCheckTime = System.currentTimeMillis()
    Logger.alert("latest version: $latestVersion")
  }

  override suspend fun upgrade(): Boolean {
    if (!requireUpgrading()) {
      return false
    }
    val images = getImageList()
    val requiresUpgradingImages = checkUpgradingImagesList(images)
    writeFile(Consts.requireImageFile, images)
    writeFile(Consts.upgradeImageFile, requiresUpgradingImages)
    Utils.execute(
      "docker run -d --rm " +
        "-v /var/run/docker.sock:/var/run/docker.sock " +
        Config.get().mainArgs.imagePrefix + "/vpss-base:${Config.get().getTagOf("vpss-base")} " +
        "docker restart vpss-launcher"
    )
    return true
  }

  private fun writeFile(file: String, images: List<String>) {
    val sb = StringBuilder()
    for (image in images) {
      sb.append(image).append("\n")
    }
    IOUtils.writeFileWithBackup(file, sb.toString())
  }

  private fun getImageList(): List<String> {
    val result = ArrayList<String>()
    for (image in latestVersion.images) {
      result.add(image.name)
    }
    Logger.alert("retrieved images list: $result")
    return result
  }

  private fun checkUpgradingImagesList(images: List<String>): List<String> {
    val result = ArrayList<String>()
    for ((index, image0) in images.withIndex()) {
      var image = image0
      if (image.startsWith("/")) {
        image = "vproxyio$image"
      }
      if (!image.contains(":")) {
        image += ":" + Config.get().getTagOf(image)
      }
      val res = Utils.execute("docker image inspect $image", true)
      if (res.exitCode != 0) {
        if (res.stderr.trim() == "Error: No such image: $image") {
          Logger.warn(LogType.ALERT, "image $image not found, it should be upgraded")
          continue // missing images will always be pulled, so no need to add to upgrading list
        } else {
          val err = "failed retrieving image info for $image: ${res.stderr}"
          Logger.error(LogType.SYS_ERROR, err)
          throw Exception(err)
        }
      }
      val output = res.stdout
      val sha256 = try {
        val str = (JSON.parse(output) as JSON.Array).getObject(0).getArray("RepoDigests").getString(0)
        str.substring(str.indexOf("sha256:") + "sha256:".length)
      } catch (e: Exception) {
        val err = "failed to parse image info for $image: $output"
        Logger.error(LogType.SYS_ERROR, err)
        throw Exception(err)
      }
      Logger.alert("local image $image sha256: $sha256")

      val expectedSha256 = latestVersion.images[index].sha256
      Logger.alert("expected image $image sha256: $expectedSha256")
      if (expectedSha256 == sha256) {
        Logger.warn(LogType.ALERT, "no need to upgrade image $image")
        continue
      }
      Logger.warn(LogType.ALERT, "image $image requires upgrading")
      result.add(image0)
    }
    Logger.alert("upgrading images list: $result")
    return result
  }
}
