package io.vproxy.vpss.config

import io.vproxy.commons.util.IOUtils
import io.vproxy.dep.vjson.JSON
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.entity.Interface
import io.vproxy.vpss.util.MainArgs
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ThreadLocalRandom
import kotlin.experimental.and
import kotlin.experimental.or

class Config {
  companion object {
    private var config: Config? = null
    fun get(): Config {
      if (config == null) {
        synchronized(Config::class.java) {
          if (config == null) {
            config = Config()
          }
        }
      }
      return config!!
    }

    inline fun update(function: Config.() -> Unit) {
      synchronized(this) {
        function(get())
      }
    }

    inline fun updatePwd(function: PwdListJson.() -> Unit) {
      synchronized(this) {
        function(get().pwd)
        get().savePwd()
      }
    }
  }

  val config: ConfigJson
  val pwd: PwdListJson
  private val imageTags: Map<String, String>
  val mainArgs: MainArgs = MainArgs()

  // interfaces configured but not found when launching
  val nicTombstone: MutableList<Interface> = ArrayList()

  init {
    val file = File("/etc/vpss/config.json")
    if (!file.exists()) {
      config = ConfigJson()
    } else {
      val content = Files.readString(file.toPath())
      config = JSON.deserialize(content, ConfigJson.rule)
    }
    var requireSaving = initDefault(config)

    val passFile = File("/etc/vpss/passwd")
    if (!passFile.exists()) {
      pwd = initDefaultPass()
      requireSaving = true
    } else {
      val content = Files.readString(passFile.toPath())
      pwd = JSON.deserialize(content, PwdListJson.rule)
    }

    if (requireSaving) {
      save()
    }

    val tagFile = File("/etc/vpss/image-tags.json")
    val imageTags = if (tagFile.exists()) {
      val content = Files.readString(tagFile.toPath()).trim()
      @Suppress("UNCHECKED_CAST")
      JSON.parseToJavaObject(content) as Map<String, String>
    } else {
      mapOf()
    }
    this.imageTags = imageTags
  }

  fun save() {
    IOUtils.writeFileWithBackup("/etc/vpss/config.json", config.toJson().pretty() + "\n")
    savePwd()
  }

  fun getTagOf(name0: String): String {
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

  @Suppress("RemoveRedundantQualifierName")
  private fun initDefault(config: ConfigJson): Boolean { // return true when modified
    var modified = false

    if (config.virtualMac == null) {
      val virtualMacBytes = kotlin.ByteArray(6)
      ThreadLocalRandom.current().nextBytes(virtualMacBytes)
      virtualMacBytes[0] = (virtualMacBytes[0].and(0xf0.toByte())).or(0x0a.toByte())
      virtualMacBytes[1] = (virtualMacBytes[1].and(0xf0.toByte())).or(0x01.toByte())
      config.virtualMac = MacAddress(virtualMacBytes)
      modified = true
    }

    if (config.virtualSSHMac == null) {
      val virtualSSHMacBytes = kotlin.ByteArray(6)
      ThreadLocalRandom.current().nextBytes(virtualSSHMacBytes)
      virtualSSHMacBytes[0] = (virtualSSHMacBytes[0].and(0xf0.toByte())).or(0x0a.toByte())
      virtualSSHMacBytes[1] = (virtualSSHMacBytes[1].and(0xf0.toByte())).or(0x02.toByte())
      config.virtualSSHMac = MacAddress(virtualSSHMacBytes)
      modified = true
    }

    if (config.vpwsAgentMac == null) {
      val vpwsAgentMacBytes = kotlin.ByteArray(6)
      ThreadLocalRandom.current().nextBytes(vpwsAgentMacBytes)
      vpwsAgentMacBytes[0] = (vpwsAgentMacBytes[0].and(0xf0.toByte())).or(0x0a.toByte())
      vpwsAgentMacBytes[1] = (vpwsAgentMacBytes[1].and(0xf0.toByte())).or(0x03.toByte())
      config.vpwsAgentMac = MacAddress(vpwsAgentMacBytes)
      modified = true
    }

    return modified
  }

  private fun initDefaultPass(): PwdListJson {
    val ret = PwdListJson()
    ret.initiated = false
    return ret
  }

  private fun initDefaultFlow(): Flow {
    val flow = Flow()
    flow.enable = false
    flow.flow = ""
    return flow
  }

  fun savePwd() {
    IOUtils.writeFileWithBackup("/etc/vpss/passwd", pwd.toJson().pretty() + "\n")
  }
}
