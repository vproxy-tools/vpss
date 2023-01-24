package io.vproxy.vpss.launch

import io.vproxy.base.component.check.CheckProtocol
import io.vproxy.base.component.check.HealthCheckConfig
import io.vproxy.base.component.elgroup.EventLoopGroup
import io.vproxy.base.component.svrgroup.Method
import io.vproxy.base.component.svrgroup.ServerGroup
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.component.app.TcpLB
import io.vproxy.component.secure.SecurityGroup
import io.vproxy.component.svrgroup.Upstream
import io.vproxy.vfd.IPPort
import io.vproxy.vfd.UDSPath
import io.vproxy.vpss.config.Config
import io.vproxy.vpss.util.Consts
import io.vproxy.vpss.util.Global
import java.io.File

object SSHProxyLaunch {
  fun launch() {
    Logger.alert("launching ssh proxy ...")

    val file = File(Consts.sshProxyDomainSocket)
    if (file.exists()) {
      file.delete()
    }

    val elg = EventLoopGroup("ssh-proxy-event-loop-group")
    elg.add("ssh-proxy-event-loop")
    val ups = Upstream("ssh-upstream")
    val sg = ServerGroup("ssh-server-group", elg, HealthCheckConfig(2000, 5000, 1, 5, CheckProtocol.none), Method.wrr)
    ups.add(sg, 10)

    sg.add("ssh-server", IPPort("127.0.0.1:22"), 10)

    val tcpLB = TcpLB("ssh-proxy", elg, elg, UDSPath(Consts.sshProxyDomainSocket), ups, 600_000, 16384, 16384, SecurityGroup.allowAll())
    tcpLB.start()
    file.deleteOnExit()
    Logger.alert("ssh proxy started")

    val res = Utils.execute("docker container inspect ${Consts.sshProxyContainerName}", true)
    if (res.exitCode != 0 && res.stderr.trim() == "Error: No such container: ${Consts.sshProxyContainerName}") {
      Logger.alert("need to start container ${Consts.sshProxyContainerName} ...")
      Utils.execute(
        "docker run --rm -d --privileged --name ${Consts.sshProxyContainerName} " +
          "-v ${Consts.sshProxyDomainSocket}:${Consts.sshProxyDomainSocket}:ro " +
          "--mac-address ${Config.get().config.virtualSSHMac} " +
          "--net=vpss-net1 " +
          Config.get().mainArgs.imagePrefix + "/tools-socat-with-dhclient:${Config.get().getTagOf("tools-socat-with-dhclient")} " +
          "TCP-LISTEN:22,reuseaddr,fork UNIX-CLIENT:${Consts.sshProxyDomainSocket}"
      )
      Utils.execute(
        "docker exec ${Consts.sshProxyContainerName} iptables -t nat -I PREROUTING " +
          "-p tcp --destination ${Consts.vgwIp.formatToIPString()} " +
          "--dport 22 " +
          "-j REDIRECT --to-port 22"
      )
      Utils.execute(
        "docker exec -d ${Consts.sshProxyContainerName} /usr/bin/socat " +
          "TCP-LISTEN:80,reuseaddr,fork TCP:${Consts.vgwIp.formatToIPString()}:80"
      )
    } else if (res.exitCode == 0) {
      Logger.alert("container ${Consts.sshProxyContainerName} is already started")
    } else {
      val err = "failed to retrieve info about container ${Consts.sshProxyContainerName}"
      Logger.error(LogType.SYS_ERROR, err)
      throw Exception(err)
    }
  }
}
