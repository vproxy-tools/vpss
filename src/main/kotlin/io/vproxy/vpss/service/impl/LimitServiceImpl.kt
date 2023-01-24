package io.vproxy.vpss.service.impl

import io.vproxy.base.util.exception.XException
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.Limit
import io.vproxy.vpss.service.LimitService
import io.vproxy.vpss.util.ErrorCode
import io.vproxy.vpss.util.Global

object LimitServiceImpl : LimitService {
  override fun getLimits(): List<Limit> {
    return Config.get().config.limits
  }

  override fun addLimit(limit: Limit): Limit {
    val toAdd = Limit()
    toAdd.name = limit.name
    toAdd.sourceMac = if (limit.sourceMac == "*") {
      "*"
    } else {
      try {
        MacAddress(limit.sourceMac).toString()
      } catch (e: RuntimeException) {
        throw XException(ErrorCode.badArgsInvalidLimitMac)
      }
    }
    toAdd.target = if (limit.target == "*") {
      "*"
    } else {
      try {
        IP.from(limit.target).formatToIPString()
      } catch (e: RuntimeException) {
        throw XException(ErrorCode.badArgsInvalidLimitIp)
      }
    }
    toAdd.type = limit.type
    if (limit.value < 0) {
      throw XException(ErrorCode.badArgsInvalidLimitValue)
    }
    toAdd.value = limit.value

    Global.ratelimitHolder.register(toAdd)
    Config.update {
      config.limits.add(toAdd)
    }
    return toAdd
  }

  override fun delLimit(name: String) {
    Global.ratelimitHolder.deregister(name)
    Config.update {
      config.limits.removeIf { it.name == name }
    }
  }

  override fun updateValue(name: String, value: Double) {
    if (value < 0) {
      throw XException(ErrorCode.badArgsInvalidLimitValue)
    }
    val configLimit = Config.get().config.limits.find { it.name == name } ?: throw XException(ErrorCode.notFoundLimit)
    Global.ratelimitHolder.update(name, value)
    Config.update {
      configLimit.value = value
    }
  }
}
