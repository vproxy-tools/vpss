package io.vproxy.vpss.service.impl

import io.vproxy.app.plugin.impl.BasePacketFilter
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.lib.common.await
import io.vproxy.lib.common.execute
import io.vproxy.vproxyx.pktfiltergen.flow.Flows
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.network.VPSSPacketFilter
import io.vproxy.vpss.service.FlowService
import io.vproxy.vpss.util.Global
import io.vproxy.vswitch.plugin.PacketFilter
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files

object FlowServiceImpl : FlowService {
  private var currentFilter: PacketFilter? = null

  fun getCurrentFilter(): PacketFilter? {
    val enable = Config.get().config.flow.enable
    if (!enable) {
      return null
    }
    return currentFilter
  }

  override suspend fun toggleFlowEnableDisable(): Boolean {
    val ret = !Config.get().config.flow.enable
    Config.update {
      config.flow.enable = ret
    }
    return ret
  }

  fun launch() {
    val flow = Config.get().config.flow.flow
    if (flow.isNotBlank()) {
      loadFlow(flow)
    }
  }

  override suspend fun updateFlow(flow: String): Boolean {
    Global.getExecutorLoop().execute {
      loadFlow(flow)
    }.await()
    Config.update {
      config.flow.enable = true
      config.flow.flow = flow
    }
    return true
  }

  private fun loadFlow(flow: String) {
    val javaFilePath = generateFlowJavaFile(flow)
    val classFilePath = generateFlowClass(javaFilePath)
    clearAllJavaAndClasses(javaFilePath, classFilePath)
    Logger.alert("custom packet filter class file generated: $classFilePath")
    val cls = loadGeneratedClass(classFilePath)
    val o = cls.getConstructor().newInstance()
    val filter = o as PacketFilter
    if (filter is BasePacketFilter) {
      for (iface in Global.getSwitch().ifaces) {
        filter.ifaceAdded(iface)
      }
    }
    val old = this.currentFilter
    this.currentFilter = filter
    VPSSPacketFilter.clearIngressCache()
    Logger.alert("filter replaced, old=$old, current=${this.currentFilter}")
  }

  private const val classPrefix = "VPSSGeneratedCustomFlow"

  private fun clearAllJavaAndClasses(javaFilePath: String, classFilePath: String) {
    val files = File(classFilePath).parentFile.listFiles()
    for (f in files!!) {
      if (!f.isFile) continue
      if (!f.name.startsWith(classPrefix)) continue
      if (!f.name.endsWith(".java") && !f.name.endsWith(".class")) continue
      if (f.absolutePath == javaFilePath) continue
      if (f.absolutePath == classFilePath) continue
      f.delete()
    }
  }

  // return .java filename
  private fun generateFlowJavaFile(flow: String): String {
    val f = File.createTempFile(classPrefix, ".java")
    f.deleteOnExit()
    val flows = Flows()
    flows.add(flow)
    var clsName = f.name
    clsName = clsName.substring(0, clsName.length - ".java".length)
    val content = flows.gen(clsName, FlowBasePacketFilter::class.qualifiedName)
    Files.writeString(f.toPath(), content)
    return f.absolutePath
  }

  // return .class filename
  private fun generateFlowClass(javafile: String): String {
    val parent = File(javafile).parentFile.absolutePath
    val name = File(javafile).name
    Utils.execute(
      """
      cd $parent
      javac -cp /vproxy/vpss/vpss.jar $name
      """.trimIndent()
    )
    return javafile.substring(0, javafile.length - ".java".length) + ".class"
  }

  private fun loadGeneratedClass(classFilePath: String): Class<*> {
    val classFile = File(classFilePath)
    val loader = URLClassLoader(arrayOf(classFile.parentFile.toURI().toURL()), Thread.currentThread().contextClassLoader)
    var name = classFile.name
    name = name.substring(0, name.length - ".class".length)
    return loader.loadClass(name)
  }
}
