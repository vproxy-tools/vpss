package io.vproxy.vpss.util

import io.vproxy.base.util.Network
import io.vproxy.base.util.coll.Tuple
import io.vproxy.vfd.IP
import io.vproxy.vfd.IPv4
import java.util.*

object Consts {
  const val VERSION = "0.0.1" // _THE_VERSION_
  const val staticFileDirectory = "/vproxy/vpss/ui"
  const val sshProxyDomainSocket = "/var/run/vpss-ssh-proxy.sock"
  const val requireImageFile = "/etc/vpss/require-images"
  const val upgradeImageFile = "/etc/vpss/upgrade-images"
  const val systemInternalVni = 99998
  const val ringSize = 2048
  const val minPassLength = 8

  const val recordingDuration = 1 * 24 * 60 * 60 * 1000
  const val samplingRate = 60_000

  //                  free-mem, total-chunks frame-size
  val chunkSizeMemMap: Map<Int, Tuple<Int, Int>> = Collections.unmodifiableMap(object : LinkedHashMap<Int, Tuple<Int, Int>>() {
    init {
      this[81920 * 4096 * 4] = Tuple(81920, 4096)
      this[40960 * 4096 * 2] = Tuple(40960, 4096)
      this[20480 * 4096 * 2] = Tuple(20480, 4096)
      this[(20480 * 2048 * 2.5).toInt()] = Tuple(20480, 2048)
    }
  })
  const val dockerNetworkNamePrefix = "vpss-net"
  val vgwIp: IPv4 = IP.from("100.118.103.119") as IPv4
  val vgwAdminNet = Network.from("100.118.103.118/31")
  const val defaultNetwork = 1
  const val sessionCookieKey = "vpss-session"
  const val adminUsername = "admin"
  const val vpwsAgentContainerName = "vpws-agent"
  const val vpwsNetwork: String = "100.96.0.0/12"
  const val sshProxyContainerName = "vpss-ssh-proxy"

  val inputStatisticsKey = Object()
  val outputStatisticsKey = Object()
  val allowDhcpKey = Object()
  val ratelimitKey = Object()
  val runRouteKey = Object()
  val ifaceMacAddress = Object()
  val allowIpv6Key = Object()

  const val IPAnnotationLoadedFromNic = "loaded-from-nic"
}
