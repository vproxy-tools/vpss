package io.vproxy.vpss.launch

import io.vproxy.vpss.config.Config
import io.vproxy.vpss.util.Global

object WBListLaunch {
  fun launch() {
    for (wblist in Config.get().config.wblists) {
      Global.wblistHolder.register(wblist)
    }
  }
}
