package io.vproxy.vpss.util

import io.vproxy.base.util.Logger
import io.vproxy.base.util.exception.XException
import io.vproxy.base.util.ratelimit.RateLimiter
import io.vproxy.base.util.ratelimit.SimpleRateLimiter
import io.vproxy.base.util.ratelimit.StatisticsRateLimiter
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress
import io.vproxy.vpacket.AbstractIpPacket
import io.vproxy.vpacket.EthernetPacket
import io.vproxy.vpss.entity.Limit
import io.vproxy.vpss.entity.LimitType

class RatelimitHolder {
  private val upstreamMacOnlyRateLimit = HashMap<MacAddress, RateLimit>()
  private val upstreamIpOnlyRateLimit = HashMap<IP, RateLimit>()
  private val upstreamMacIpRateLimit = HashMap<MacIp, RateLimit>()

  private val downstreamMacOnlyRateLimit = HashMap<MacAddress, RateLimit>()
  private val downstreamIpOnlyRateLimit = HashMap<IP, RateLimit>()
  private val downstreamMacIpRateLimit = HashMap<MacIp, RateLimit>()

  private val nameMap = HashMap<String, RateLimit>()

  fun lookup(pkt: EthernetPacket): RateLimiter? {
    if (pkt.packet is AbstractIpPacket) {
      // check mac+ip
      // check downstream
      val downMacIp = MacIp(pkt.dst, (pkt.packet as AbstractIpPacket).src)
      var rl = downstreamMacIpRateLimit[downMacIp]
      if (rl != null) {
        return rl.rateLimiter
      }
      // check upstream
      val upMacIp = MacIp(pkt.src, (pkt.packet as AbstractIpPacket).dst)
      rl = upstreamMacIpRateLimit[upMacIp]
      if (rl != null) {
        return rl.rateLimiter
      }
      // check ip
      // check downstream
      var ip = (pkt.packet as AbstractIpPacket).src
      rl = downstreamIpOnlyRateLimit[ip]
      if (rl != null) {
        return rl.rateLimiter
      }
      // check upstream
      ip = (pkt.packet as AbstractIpPacket).dst
      rl = upstreamIpOnlyRateLimit[ip]
      if (rl != null) {
        return rl.rateLimiter
      }
    }
    // check mac
    // check downstream
    var mac = pkt.dst
    var rl = downstreamMacOnlyRateLimit[mac]
    if (rl != null) {
      return rl.rateLimiter
    }
    // check upstream
    mac = pkt.src
    rl = upstreamMacOnlyRateLimit[mac]
    if (rl != null) {
      return rl.rateLimiter
    }
    return null
  }

  fun register(limit: Limit) {
    if (nameMap.containsKey(limit.name)) {
      throw XException(ErrorCode.conflictLimitName)
    }
    if (limit.type == LimitType.upstream) {
      register(limit, upstreamMacOnlyRateLimit, upstreamIpOnlyRateLimit, upstreamMacIpRateLimit)
    } else {
      register(limit, downstreamMacOnlyRateLimit, downstreamIpOnlyRateLimit, downstreamMacIpRateLimit)
    }
  }

  fun deregister(name: String) {
    if (!nameMap.containsKey(name)) {
      throw XException(ErrorCode.notFoundLimit)
    }
    val rl = nameMap[name]!!
    if (rl.type == LimitType.upstream) {
      deregister(rl, upstreamMacOnlyRateLimit, upstreamIpOnlyRateLimit, upstreamMacIpRateLimit)
    } else {
      deregister(rl, downstreamMacOnlyRateLimit, downstreamIpOnlyRateLimit, downstreamMacIpRateLimit)
    }
  }

  fun update(name: String, value: Double) {
    if (!nameMap.containsKey(name)) {
      throw XException(ErrorCode.notFoundLimit)
    }
    val rl = nameMap[name]!!
    rl.rateLimiter = buildStatisticsRateLimiter(value)
  }

  private fun register(
    limit: Limit,
    macOnlyMap: MutableMap<MacAddress, RateLimit>,
    ipOnlyMap: MutableMap<IP, RateLimit>,
    macipMap: MutableMap<MacIp, RateLimit>
  ) {
    val ratelimit = buildRateLimit(limit)
    if (limit.sourceMac != "*" && limit.target != "*") {
      val mac = MacAddress(limit.sourceMac)
      val ip = IP.from(limit.target)
      val macip = MacIp(mac, ip)
      if (macipMap.containsKey(macip)) {
        throw XException(ErrorCode.conflictLimitMacAndIp)
      }
      macipMap[macip] = ratelimit
    } else if (limit.sourceMac != "*") {
      val mac = MacAddress(limit.sourceMac)
      if (macOnlyMap.containsKey(mac)) {
        throw XException(ErrorCode.conflictLimitMac)
      }
      macOnlyMap[mac] = ratelimit
    } else if (limit.target != "*") {
      val ip = IP.from(limit.target)
      if (ipOnlyMap.containsKey(ip)) {
        throw XException(ErrorCode.conflictLimitIp)
      }
      ipOnlyMap[ip] = ratelimit
    } else {
      throw XException(ErrorCode.badArgsInvalidLimitNeitherMacNorIpProvided)
    }
    nameMap[limit.name!!] = ratelimit
  }

  private fun deregister(
    rl: RateLimit,
    macOnlyMap: MutableMap<MacAddress, RateLimit>,
    ipOnlyMap: MutableMap<IP, RateLimit>,
    macipMap: MutableMap<MacIp, RateLimit>
  ) {
    if (rl.mac == null && rl.ip == null) {
      Logger.shouldNotHappen("invalid ratelimit ${rl.name} has neither mac nor ip")
      return
    }
    if (rl.mac == null) {
      ipOnlyMap.remove(rl.ip!!)
    } else if (rl.ip == null) {
      macOnlyMap.remove(rl.mac)
    } else {
      macipMap.remove(MacIp(rl.mac, rl.ip))
    }
  }

  private fun buildRateLimit(limit: Limit): RateLimit {
    return RateLimit(
      limit.name!!,
      if (limit.sourceMac == "*") {
        null
      } else {
        MacAddress(limit.sourceMac)
      },
      if (limit.target == "*") {
        null
      } else {
        IP.from(limit.target)
      },
      limit.type!!,
      buildStatisticsRateLimiter(limit.value)
    )
  }

  private fun buildStatisticsRateLimiter(value: Double): StatisticsRateLimiter {
    val bps = (value * 1024 * 1024).toLong()
    return StatisticsRateLimiter(
      SimpleRateLimiter(bps, (bps / 1000 + if (bps % 1000 == 0L) 0 else 1)),
      Consts.recordingDuration,
      Consts.samplingRate,
    )
  }

  fun find(name: String): StatisticsRateLimiter {
    val ret = nameMap[name] ?: throw XException(ErrorCode.notFoundLimit)
    return ret.rateLimiter
  }

  private data class MacIp(val mac: MacAddress, val ip: IP)
  private class RateLimit(
    val name: String,
    val mac: MacAddress?,
    val ip: IP?,
    val type: LimitType,
    var rateLimiter: StatisticsRateLimiter,
  )
}
