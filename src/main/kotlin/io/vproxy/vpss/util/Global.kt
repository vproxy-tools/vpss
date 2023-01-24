package io.vproxy.vpss.util

import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.vpss.config.Session
import io.vproxy.vswitch.Switch
import io.vproxy.xdp.UMem
import java.util.concurrent.ConcurrentHashMap

object Global {
  private var loop: SelectorEventLoop? = null
  private var executorLoop: SelectorEventLoop? = null
  private var sw: Switch? = null
  private var umem: UMem? = null
  val sessions: MutableMap<String, Session> = ConcurrentHashMap()
  val wblistHolder = WBListHolder()
  val ratelimitHolder = RatelimitHolder()
  val startTimeMillis = System.currentTimeMillis()

  fun getLoop(): SelectorEventLoop {
    return loop!!
  }

  fun setLoop(loop: SelectorEventLoop) {
    Global.loop = loop
  }

  fun getExecutorLoop(): SelectorEventLoop {
    return executorLoop!!
  }

  fun setExecutorLoop(loop: SelectorEventLoop) {
    this.executorLoop = loop
  }

  fun getSwitch(): Switch {
    return sw!!
  }

  fun setSwitch(sw: Switch) {
    Global.sw = sw
  }

  fun getUMem(): UMem? {
    return umem
  }

  fun setUMem(umem: UMem) {
    Global.umem = umem
  }
}
