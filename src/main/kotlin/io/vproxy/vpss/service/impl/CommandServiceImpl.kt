package io.vproxy.vpss.service.impl

import io.vproxy.base.util.Utils
import io.vproxy.lib.common.await
import io.vproxy.lib.common.execute
import io.vproxy.vpss.service.CommandService
import io.vproxy.vpss.util.Global

object CommandServiceImpl : CommandService {
  override suspend fun execute(script: String, timeout: Int): Utils.ExecuteResult {
    return Global.getExecutorLoop().execute {
      Utils.execute(script, timeout, true)
    }.await()
  }
}
