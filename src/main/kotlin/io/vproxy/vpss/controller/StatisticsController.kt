package io.vproxy.vpss.controller

import io.vproxy.dep.vjson.JSON
import io.vproxy.dep.vjson.util.ObjectBuilder
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.base.util.coll.Tuple3
import io.vproxy.base.util.exception.XException
import io.vproxy.base.util.ratelimit.StatisticsRateLimiter
import io.vproxy.lib.http.RoutingContext
import io.vproxy.lib.http1.CoroutineHttp1Server
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.ErrorCode
import io.vproxy.vpss.util.Global
import io.vproxy.vswitch.iface.Iface
import io.vproxy.vswitch.iface.VLanAdaptorIface

// statistics data format: { data: [], beginTs: Long, endTs: Long }
class StatisticsController(app: CoroutineHttp1Server) : BaseController(app) {
  companion object {
    private const val DEFAULT_DURATION = 900_000
  }

  init {
    app.get("/api/statistics/ifaces/:iface", handle(::getIfaceStatistics))
    app.get("/api/statistics/vlan/:iface/:vlan", handle(::getVLanStatistics))
    app.get("/api/statistics/limits/:limit", handle(::getLimitStatistics))
  }

  private fun getBeginEndPeriod(ctx: RoutingContext): Tuple3<Long, Long, Int> {
    var endTs = System.currentTimeMillis() - Consts.samplingRate
    var beginTs: Long = -1
    var period: Long = -1

    val url = ctx.req.uri()
    if (url.contains("?")) {
      val params = url.substring(url.indexOf("?") + 1)
      val split = params.split("&")
      for (kv in split) {
        if (!kv.contains("=")) {
          continue
        }
        val kvx = kv.split("=")
        if (kvx.size != 2) {
          continue
        }
        val key = kvx[0]
        val value = kvx[1]
        if (key == "beginTs") {
          beginTs = try {
            java.lang.Long.parseLong(value)
          } catch (e: RuntimeException) {
            throw XException(ErrorCode.badArgsInvalidBeginTs)
          }
        } else if (key == "endTs") {
          endTs = try {
            java.lang.Long.parseLong(value)
          } catch (e: RuntimeException) {
            throw XException(ErrorCode.badArgsInvalidEndTs)
          }
        } else if (key == "period") {
          period = try {
            java.lang.Long.parseLong(value)
          } catch (e: RuntimeException) {
            throw XException(ErrorCode.badArgsInvalidPeriod)
          }
        }
      }
    }

    if (beginTs >= endTs) {
      throw XException(ErrorCode.badArgsBeginTsGEEndTs)
    }

    if (beginTs == (-1).toLong()) {
      beginTs = endTs - DEFAULT_DURATION
    }

    if (period == (-1).toLong()) {
      period = if (endTs - beginTs <= 900_000) {
        60_000
      } else if (endTs - beginTs <= 3600_000) {
        180_000
      } else if (endTs - beginTs <= 4 * 3600_000) {
        720_000
      } else if (endTs - beginTs <= 6 * 3600_000) {
        1200_000
      } else {
        3600_000
      }
    }
    if (period >= endTs - beginTs) {
      throw XException(ErrorCode.badArgsPeriodGEDuration)
    }

    return Tuple3(beginTs, endTs, (period / Consts.samplingRate).toInt())
  }

  private fun formatData(tup: Tuple3<Array<Long?>, Long, Long>): JSON.Object {
    val rt = ObjectBuilder()
    rt.put("beginTs", tup._2)
      .put("endTs", tup._3)
      .putArray("data") {
        for (l in tup._1) {
          if (l == null) {
            add(null)
          } else {
            add(l)
          }
        }
      }
    return rt.build()
  }

  private fun putStatisticsFromIface(obj: ObjectBuilder, iface: Iface, beginTs: Long, endTs: Long, step: Int) {
    val inputStatisticsObj = iface.getUserData(Consts.inputStatisticsKey)
    if (inputStatisticsObj == null) {
      obj.put("input", null)
    } else {
      val inputStatistics = inputStatisticsObj as StatisticsRateLimiter
      val tup = inputStatistics.getStatistics(beginTs, endTs, step)
      obj.putInst("input", formatData(tup))
    }
    val outputStatisticsObj = iface.getUserData(Consts.outputStatisticsKey)
    if (outputStatisticsObj == null) {
      obj.put("output", null)
    } else {
      val outputStatistics = outputStatisticsObj as StatisticsRateLimiter
      val tup = outputStatistics.getStatistics(beginTs, endTs, step)
      obj.putInst("output", formatData(tup))
    }
    obj.put("historyTotalInput", iface.statistics.rxBytes)
    obj.put("historyTotalOutput", iface.statistics.txBytes)
  }

  // return { name: String, input: {}, output: {}, historyTotalInput, historyTotalOutput }
  private fun getIfaceStatistics(ctx: RoutingContext): JSON.Instance<*> {
    val (beginTs, endTs, step) = getBeginEndPeriod(ctx)
    val name = ctx.param("iface")

    val iface = Global.getSwitch().ifaces.find { it.name().equals("xdp:$name") } ?: throw XException(ErrorCode.notFoundManagedIface)
    val obj = ObjectBuilder()
    obj.put("name", name)
    putStatisticsFromIface(obj, iface, beginTs, endTs, step)
    return obj.build()
  }

  // return { remoteVLan: Int, input: {}, output: {}, historyTotalInput, historyTotalOutput }
  private fun getVLanStatistics(ctx: RoutingContext): JSON.Instance<*> {
    val (beginTs, endTs, step) = getBeginEndPeriod(ctx)

    val ifaceName = ctx.param("iface")
    val vlanStr = ctx.param("vlan")
    if (!Utils.isInteger(vlanStr)) {
      throw XException(ErrorCode.badArgsInvalidRemoteVLan)
    }
    Global.getSwitch().ifaces.find { it.name().equals("xdp:$ifaceName") } ?: throw XException(ErrorCode.notFoundManagedIface)
    val vlanIface = Global.getSwitch().ifaces.find { it.name().equals("vlan.$vlanStr@xdp:$ifaceName") }
      ?: throw XException(ErrorCode.notFoundVLanIface)
    if (vlanIface !is VLanAdaptorIface) {
      Logger.shouldNotHappen("vlan.$vlanStr@xdp:$ifaceName is not a VLanAdaptorIface")
      throw XException(ErrorCode.notFoundVLanIface) // should not happen
    }
    val obj = ObjectBuilder()
    obj.put("remoteVLan", vlanIface.remoteVLan)
    putStatisticsFromIface(obj, vlanIface, beginTs, endTs, step)
    return obj.build()
  }

  // return { name: String, data: {} }
  private fun getLimitStatistics(ctx: RoutingContext): JSON.Instance<*> {
    val (beginTs, endTs, step) = getBeginEndPeriod(ctx)

    val name = ctx.param("limit")
    val rl = Global.ratelimitHolder.find(name)
    val tup = rl.getStatistics(beginTs, endTs, step)

    val ret = ObjectBuilder()
    ret.put("name", name)
    ret.putInst("data", formatData(tup))
    return ret.build()
  }
}
