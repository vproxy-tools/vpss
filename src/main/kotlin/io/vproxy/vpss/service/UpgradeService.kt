package io.vproxy.vpss.service

interface UpgradeService {
  fun peekLatestVersion(): String
  fun peekLatestVProxyVersion(): String
  suspend fun getLatestVersion(): String
  suspend fun getLatestVProxyVersion(): String
  fun requireUpgrading(): Boolean
  suspend fun checkForUpdates()
  suspend fun upgrade(): Boolean
}
