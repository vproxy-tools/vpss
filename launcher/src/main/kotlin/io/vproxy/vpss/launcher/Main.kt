package io.vproxy.vpss.launcher

import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.dep.vjson.JSON
import io.vproxy.vpss.launcher.util.ImageInfo
import io.vproxy.vpss.launcher.util.VPSSLauncherUtils
import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

object Main {
  private val HELP_STR = """
    Usage:
      help|--help|-help|-h                        show this message
      version|--version|-version|-v               retrieve vpss-launcher and vproxy version
      --image-prefix=<>                           image prefix used when pulling/creating containers
  """.trimIndent()
  private var imageTags: Map<String, String> = mapOf()
  private var launchArgs: String = ""
  private var imagePrefix: String = "vproxyio"

  @JvmStatic
  fun main(args0: Array<String>) {
    val args = io.vproxy.app.app.Main.checkFlagDeployInArguments(args0)
    for (arg in args) {
      if (arg == "version" || arg == "--version" || arg == "-version" || arg == "-v") {
        println("vpss-launcher: " + io.vproxy.vpss.launcher.util.Consts.VERSION)
        @Suppress("RemoveRedundantQualifierName")
        println("vproxy: " + io.vproxy.base.util.Version.VERSION)
        return
      } else if (arg.startsWith("help") || arg.startsWith("--help") || arg.startsWith("-help") || arg.startsWith("-h")) {
        println(HELP_STR)
        return
      } else if (arg.startsWith("--image-prefix=")) {
        var v = arg.substring("--image-prefix=".length).trim()
        if (v.endsWith("/")) {
          v = v.substring(0, v.length - 1)
        }
        if (v != "") {
          imagePrefix = v
        }
        Logger.alert("image-prefix: $imagePrefix")
      } else {
        throw Exception("unknown argument: $arg")
      }
    }

    readTags()
    ensureNetPlugin()
    val images = getImagesToUpdate()
    Logger.alert("check and pull images: $images")
    val existingImages = VPSSLauncherUtils.getImages()
    var allPullOk = true
    for (image in images) {
      if (!pullImage(existingImages, image)) {
        allPullOk = false
      }
    }
    if (!allPullOk) {
      Logger.error(LogType.ALERT, "failed pulling images, exiting ...")
      Thread.sleep(5_000)
      exitProcess(1)
      @Suppress("UNREACHABLE_CODE")
      return
    }
    stopContainers()
    removeOldImages(images)
    removeUpgradeImageFile()
    getLaunchArgs()
    launch()
    Thread {
      while (true) {
        try {
          Thread.sleep(10_000)
        } catch (e: InterruptedException) {
          Logger.warn(LogType.ALERT, "sleep interrupted, but ignore the exception", e)
        }
        try {
          checkAndLaunch()
        } catch (e: Exception) {
          Logger.warn(LogType.ALERT, "failed to launch vpss in periodic event", e)
        }
      }
    }.start()
  }

  private fun readTags() {
    val file = File("/etc/vpss/image-tags.json")
    if (!file.exists()) {
      Logger.alert("file image-tags.json does not exist")
      return
    }
    val str = Files.readString(file.toPath())
    @Suppress("UNCHECKED_CAST")
    imageTags = JSON.parseToJavaObject(str) as Map<String, String>
  }

  private fun getTagOf(name0: String): String {
    var name = name0
    if (name.contains(":")) {
      name = name.substring(0, name.indexOf(":"))
    }
    if (name.contains("/")) {
      name = name.substring(name.lastIndexOf("/") + 1)
    }
    return imageTags[name]
      ?: imageTags[name]
      ?: "latest"
  }

  private fun ensureNetPlugin() {
    val res = "\$res"
    Utils.execute(
      """
      #!/bin/bash
      set -x
      set +e
      res=`docker plugin ls | grep 'vproxyio/docker-plugin' | grep 'latest' | wc -l`
      set -e
      if [ "$res" == "0" ]
      then
          docker plugin install --grant-all-permissions $imagePrefix/docker-plugin:${getTagOf("docker-plugin")}
      fi
      set +e
      res=`docker plugin ls | grep 'vproxyio/docker-plugin' | grep 'true'`
      set -e
      if [ "$res" == "0" ]
      then
          docker plugin enable $imagePrefix/docker-plugin:${getTagOf("docker-plugin")}
      fi
      """.trimIndent(), 180_000
    )
  }

  private fun getImagesToUpdate(): Set<String> {
    val ls = LinkedHashSet<String>()
    getImagesToUpdateFromFile(ls)
    getMissingImagesToPull(ls)
    return ls
  }

  private fun getImagesToUpdateFromFile(ls: MutableSet<String>) {
    val file = File("/etc/vpss/upgrade-images")
    if (!file.exists()) {
      return
    }
    val content = Files.readString(file.toPath())
    val lines = content.split("\n")
    for (line0 in lines) {
      if (line0.isBlank()) {
        continue
      }
      val line = line0.trim()
      if (line.startsWith("#")) {
        continue
      }

      var imageKey = line
      if (!imageKey.contains(":")) imageKey = "$imageKey:${getTagOf(imageKey)}"
      if (imageKey.startsWith("/")) imageKey = "$imagePrefix$imageKey"
      ls.add(imageKey)
    }
  }

  private fun getMissingImagesToPull(ls: MutableSet<String>) {
    val file = File("/etc/vpss/require-images")
    if (!file.exists()) {
      return
    }

    val images = VPSSLauncherUtils.getImages()
    val keys = HashSet<String>()
    for (image in images) {
      keys.add(image.repository + ":" + image.tag)
    }

    val content = Files.readString(file.toPath())
    val lines = content.split("\n")
    for (line0 in lines) {
      if (line0.isBlank()) {
        continue
      }
      val line = line0.trim()
      if (line.startsWith("#")) {
        continue
      }

      var imageKey = line
      if (!imageKey.contains(":")) imageKey = "$imageKey:${getTagOf(imageKey)}"
      if (imageKey.startsWith("/")) imageKey = "$imagePrefix$imageKey"
      if (keys.contains(imageKey)) {
        continue
      }
      ls.add(imageKey)
    }
  }

  private fun pullImage(existingImages: List<ImageInfo>, image0: String): Boolean {
    var image = image0
    if (!image.contains(":")) image = "$image:${getTagOf(image)}"
    if (image.startsWith("/")) image = "$imagePrefix$image"
    var oldImageId: String? = null
    for (existingImage in existingImages) {
      if (existingImage.repository + ":" + existingImage.tag == image) {
        oldImageId = existingImage.imageId
        break
      }
    }
    if (oldImageId == null) {
      Logger.alert("pull new image $image")
    } else {
      Logger.alert("pull image $image with existing image id $oldImageId")
    }
    try {
      Utils.execute("docker image pull $image", 180_000)
    } catch (e: Exception) {
      if (oldImageId == null) {
        throw e
      }
      Logger.error(LogType.SYS_ERROR, "failed to pull image, use the old image instead")
      return true
    }
    if (oldImageId != null) {
      val newExistingImages = VPSSLauncherUtils.getImages()
      var needToRemove = false
      for (newExistingImage in newExistingImages) {
        if (newExistingImage.repository + ":" + newExistingImage.tag == image) {
          if (newExistingImage.imageId != oldImageId) {
            needToRemove = true
            break
          }
        }
      }
      if (needToRemove) {
        Logger.alert("image $image is replaced, old imageId is $oldImageId")
      } else {
        Logger.warn(LogType.ALERT, "image $image is up to date")
      }
    }
    return true
  }

  private fun stopContainers() {
    try {
      Utils.execute("docker stop vpss")
    } catch (e: Exception) {
      Logger.warn(LogType.ALERT, "unable to stop container vpss, maybe it's not running", e)
    }
  }

  private fun removeOldImages(updatedImages: Set<String>) {
    val repositoryNames = HashSet<String>()
    for (image in updatedImages) {
      var repo = if (image.contains(":")) {
        image.substring(0, image.indexOf(":"))
      } else {
        image
      }
      if (repo.startsWith("/")) {
        repo = "$imagePrefix$repo"
      }
      repositoryNames.add(repo)
    }
    val images = VPSSLauncherUtils.getImages()
    for (image in images) {
      if (image.tag != "<none>") continue
      if (!repositoryNames.contains(image.repository)) continue
      Logger.warn(LogType.ALERT, "will remove image ${image.repository}:${image.tag}, imageId: ${image.imageId}")
      try {
        Utils.execute("docker image rm ${image.imageId}")
      } catch (e: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to remove image ${image.repository}:${image.tag}, imageId: ${image.imageId}", e)
      }
    }
  }

  private fun removeUpgradeImageFile() {
    val file = File("/etc/vpss/upgrade-images")
    if (!file.exists()) {
      return
    }
    file.delete()
  }

  private fun getLaunchArgs() {
    val file = File("/etc/vpss/launch-vpss")
    if (!file.exists()) {
      throw Exception("unable to get launching command")
    }
    var cmdTemplate = Files.readString(file.toPath()).trim()
    cmdTemplate = cmdTemplate.replace("\$image", "$imagePrefix/vpss:${getTagOf("vpss")}")
    if (imagePrefix != "vproxyio") {
      cmdTemplate += " --image-prefix=$imagePrefix"
    }
    launchArgs = cmdTemplate
  }

  private fun launch() {
    Utils.execute(
      "docker run -d --rm " +
        "--net=host " +
        "--privileged " +
        "-v /root:/root " +
        "-v /etc/vpss:/etc/vpss " +
        "-v /var/run:/var/run " +
        "--name=vpss " +
        launchArgs
    )
  }

  private fun checkAndLaunch() {
    val res = Utils.execute("docker container inspect vpss", true)
    if (res.exitCode != 0 && res.stderr.trim() == "Error: No such container: vpss") {
      Logger.warn(LogType.ALERT, "vpss container does not exist")
      try {
        ensureNetPlugin()
        launch()
        Logger.alert("vpss container launched")
      } catch (e: Exception) {
        Logger.error(LogType.SYS_ERROR, "failed to launch vpss", e)
        throw e
      }
    } else if (res.exitCode == 0) {
      Logger.alert("vpss container is running")
    } else {
      throw Exception("failed to retrieve info about container vpss")
    }
  }
}
