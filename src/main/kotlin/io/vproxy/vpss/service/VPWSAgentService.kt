package io.vproxy.vpss.service

interface VPWSAgentService {
  suspend fun getStatus(): VPWSAgentStatus
  suspend fun updateConfigAndRestart(conf: String)
}
