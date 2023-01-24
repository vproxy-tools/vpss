package io.vproxy.vpss.service

import io.vproxy.vpss.entity.Limit

interface LimitService {
  fun getLimits(): List<Limit>
  fun addLimit(limit: Limit): Limit
  fun delLimit(name: String)
  fun updateValue(name: String, value: Double)
}
