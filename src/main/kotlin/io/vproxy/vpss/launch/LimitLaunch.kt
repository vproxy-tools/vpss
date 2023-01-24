package io.vproxy.vpss.launch

import io.vproxy.vpss.config.Config
import io.vproxy.vpss.util.Global

object LimitLaunch {
  fun launch() {
    for (limit in Config.get().config.limits) {
      Global.ratelimitHolder.register(limit)
    }
  }
}
