package io.vproxy.vpss.service.impl

import io.vproxy.base.util.Network
import io.vproxy.base.util.exception.XException
import io.vproxy.vfd.IP
import io.vproxy.vfd.MacAddress
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.entity.WBList
import io.vproxy.vpss.service.WBListService
import io.vproxy.vpss.util.ErrorCode
import io.vproxy.vpss.util.Global

object WBListServiceImpl : WBListService {
  override fun getWBLists(): List<WBList> {
    return ArrayList(Config.get().config.wblists)
  }

  override fun addRule(wblist: WBList): WBList {
    val toAdd = WBList()
    toAdd.name = wblist.name
    toAdd.sourceMac = if (wblist.sourceMac == "*") {
      "*"
    } else {
      try {
        MacAddress(wblist.sourceMac).toString()
      } catch (e: RuntimeException) {
        throw XException(ErrorCode.badArgsInvalidWBListMac)
      }
    }
    toAdd.target = if (wblist.target == "*") {
      "*"
    } else try {
      IP.from(wblist.target).formatToIPString()
    } catch (e: RuntimeException) {
      try {
        Network.from(wblist.target).toString()
      } catch (e: RuntimeException) {
        throw XException(ErrorCode.badArgsInvalidWBListIp)
      }
    }
    toAdd.type = wblist.type

    if (toAdd.sourceMac == "*" && toAdd.target == "*") {
      throw XException(ErrorCode.badArgsInvalidWBListNeitherMacNorIpProvided)
    }

    Config.update {
      for (e in config.wblists) {
        if (e.name == toAdd.name) {
          throw XException(ErrorCode.conflictWBListName)
        }
      }
      Global.wblistHolder.register(toAdd)
      config.wblists.add(toAdd)
    }
    return wblist
  }

  override fun delRule(name: String) {
    Config.update {
      for (e in config.wblists) {
        if (e.name == name) {
          Global.wblistHolder.deregister(name)
          config.wblists.remove(e)
          return@update
        }
      }
      throw XException(ErrorCode.notFoundWBList)
    }
  }
}
