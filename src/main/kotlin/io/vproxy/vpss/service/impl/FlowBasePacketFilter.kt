package io.vproxy.vpss.service.impl

import io.vproxy.app.plugin.impl.BasePacketFilter
import io.vproxy.vswitch.iface.Iface

open class FlowBasePacketFilter : BasePacketFilter() {
  override fun handleIface(iface: Iface): Boolean {
    return false;
  }
}
