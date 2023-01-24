package io.vproxy.vpss.launcher.util

import io.vproxy.base.util.Utils

object VPSSLauncherUtils {
  fun getImages(): List<ImageInfo> {
    val res = Utils.execute("docker image ls", true)
    if (res.exitCode != 0) {
      throw Exception("failed to retrieve docker images: $res")
    }
    val stdout = res.stdout
    val lines = stdout.split("\n")
    var isFirstLine = true
    val ret = ArrayList<ImageInfo>()
    for (line0 in lines) {
      val line = line0.trim()
      if (line == "") {
        continue
      }
      if (isFirstLine) {
        isFirstLine = false
        continue
      }
      val split = line.split(" ")
      val ls = ArrayList<String>()
      for (s in split) {
        val x = s.trim()
        if (x == "") {
          continue
        }
        ls.add(x)
      }
      if (ls.size < 3) {
        throw Exception("invalid output of docker image ls:\n$stdout")
      }
      val repository = ls[0]
      val tag = ls[1]
      val imageId = ls[2]
      ret.add(ImageInfo(repository = repository, tag = tag, imageId = imageId))
    }
    return ret
  }
}
