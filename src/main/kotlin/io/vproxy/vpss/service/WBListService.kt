package io.vproxy.vpss.service

import io.vproxy.vpss.entity.WBList

interface WBListService {
  fun getWBLists(): List<WBList>
  fun addRule(wblist: WBList): WBList
  fun delRule(name: String)
}
