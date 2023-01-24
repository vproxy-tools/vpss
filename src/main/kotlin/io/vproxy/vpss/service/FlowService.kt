package io.vproxy.vpss.service

interface FlowService {
  suspend fun toggleFlowEnableDisable(): Boolean
  suspend fun updateFlow(flow: String): Boolean
}
