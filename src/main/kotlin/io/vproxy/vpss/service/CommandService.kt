package io.vproxy.vpss.service

import io.vproxy.base.util.Utils

interface CommandService {
  suspend fun execute(script: String, timeout: Int): Utils.ExecuteResult
}
