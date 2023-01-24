package io.vproxy.vpss.service

import io.vproxy.base.util.coll.Tuple
import io.vproxy.vpss.entity.Interface
import io.vproxy.vpss.entity.InterfaceVLan

interface InterfaceService {
  fun getIfaces(): Tuple<List<Interface>, List<Interface>>
  fun manageIface(i: Interface): Interface
  fun unmanageIface(name: String)
  fun toggleIfaceEnableDisable(name: String): Boolean
  fun toggleVLanEnableDisable(name: String, vlan: Int): Boolean
  fun toggleIfaceAllowDisallowDhcp(name: String): Boolean
  fun toggleVLanAllowDisallowDhcp(name: String, vlan: Int): Boolean
  fun joinVLan(name: String, vlan: InterfaceVLan): InterfaceVLan
  fun leaveVLan(name: String, vlan: Int)
}
