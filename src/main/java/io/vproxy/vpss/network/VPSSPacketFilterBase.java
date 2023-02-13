package io.vproxy.vpss.network;

import io.vproxy.app.plugin.impl.BasePacketFilter;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.bitwise.BitwiseMatcher;
import io.vproxy.base.util.misc.IntMatcher;
import io.vproxy.base.util.net.PortPool;
import io.vproxy.vpacket.AbstractIpPacket;
import io.vproxy.vpacket.ArpPacket;
import io.vproxy.vpacket.Ipv4Packet;
import io.vproxy.vpacket.Ipv6Packet;
import io.vproxy.vpacket.TcpPacket;
import io.vproxy.vpacket.UdpPacket;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;
import io.vproxy.vswitch.plugin.FilterResult;

public class VPSSPacketFilterBase extends BasePacketFilter {
    private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_0 = BitwiseMatcher.from(ByteArray.fromHexString("64766777"), ByteArray.fromHexString("ffffffff"));
    private static final BitwiseMatcher BITWISE_MATCHER_HOLDER_1 = BitwiseMatcher.from(ByteArray.fromHexString("64600000"), ByteArray.fromHexString("fff00000"));
    private static final IntMatcher BITWISE_INT_MATCHER_HOLDER_0 = new PortPool("53");
    private static final IntMatcher BITWISE_INT_MATCHER_HOLDER_1 = new PortPool("22");
    private static final IntMatcher BITWISE_INT_MATCHER_HOLDER_2 = new PortPool("67");
    private static final IntMatcher BITWISE_INT_MATCHER_HOLDER_3 = new PortPool("68");
    private static final IntMatcher BITWISE_INT_MATCHER_HOLDER_4 = new PortPool("547");
    private static final IntMatcher BITWISE_INT_MATCHER_HOLDER_5 = new PortPool("546");

    public VPSSPacketFilterBase() {
        super();
    }

    @Override
    protected FilterResult handleIngress(PacketFilterHelper helper, PacketBuffer pkb) {
        return table0(helper, pkb);
    }

    private FilterResult table0(PacketFilterHelper helper, PacketBuffer pkb) {
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && predicate_drop_ipv6(helper, pkb)) {
            return execute(helper, pkb, this::action0);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()) && pkb.network != null && pkb.network.vni == 1 && predicate_dl_src_is_vpws_agent(helper, pkb)) {
            return table3(helper, pkb);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()) && pkb.network != null && pkb.network.vni == 1 && predicate_dl_src_is_vpws_agent(helper, pkb)) {
            return table3(helper, pkb);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()) && pkb.network != null && pkb.network.vni == 1) {
            return execute(helper, pkb, this::action1);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_0.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()) && pkb.network != null && pkb.network.vni == 1) {
            return execute(helper, pkb, this::action2);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_0.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst()) && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6 && BITWISE_INT_MATCHER_HOLDER_1.match(((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {
            return execute(helper, pkb, this::action3);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_1.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst())) {
            return execute(helper, pkb, this::action4);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && BITWISE_MATCHER_HOLDER_0.match(((AbstractIpPacket) pkb.pkt.getPacket()).getDst())) {
            return execute(helper, pkb, this::action5);
        }
        if (pkb.pkt.getType() == 2054 && ((ArpPacket) pkb.pkt.getPacket()).getOpcode() == 1 && BITWISE_MATCHER_HOLDER_0.match(((ArpPacket) pkb.pkt.getPacket()).getTargetIp())) {
            return execute(helper, pkb, this::action6);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_2.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_3.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()) && predicate_disallow_dhcp(helper, pkb)) {
            return execute(helper, pkb, this::action7);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_4.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_5.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort()) && predicate_disallow_dhcp(helper, pkb)) {
            return execute(helper, pkb, this::action8);
        }
        if (pkb.pkt.getType() == 2054) {
            return table3(helper, pkb);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 58) {
            return table3(helper, pkb);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_2.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_3.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {
            return table3(helper, pkb);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_3.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_2.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {
            return table3(helper, pkb);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_4.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_5.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {
            return table3(helper, pkb);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 17 && BITWISE_INT_MATCHER_HOLDER_5.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getSrcPort()) && BITWISE_INT_MATCHER_HOLDER_4.match(((UdpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {
            return table3(helper, pkb);
        }
        return table1(helper, pkb);
    }

    private FilterResult table1(PacketFilterHelper helper, PacketBuffer pkb) {
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && predicate_whitelist(helper, pkb)) {
            return table2(helper, pkb);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && predicate_blacklist(helper, pkb)) {
            return execute(helper, pkb, this::action9);
        }
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6 && BITWISE_INT_MATCHER_HOLDER_0.match(((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {
            return execute(helper, pkb, this::action10);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && ((AbstractIpPacket) pkb.pkt.getPacket()).getProtocol() == 6 && BITWISE_INT_MATCHER_HOLDER_0.match(((TcpPacket) ((AbstractIpPacket) pkb.pkt.getPacket()).getPacket()).getDstPort())) {
            return execute(helper, pkb, this::action11);
        }
        return table2(helper, pkb);
    }

    private FilterResult table2(PacketFilterHelper helper, PacketBuffer pkb) {
        if (predicate_requires_ratelimit(helper, pkb)) {
            return execute(helper, pkb, this::action12);
        }
        return table3(helper, pkb);
    }

    private FilterResult table3(PacketFilterHelper helper, PacketBuffer pkb) {
        if (pkb.pkt.getType() == 2048 && pkb.pkt.getPacket() instanceof Ipv4Packet && predicate_match_non_local_route(helper, pkb)) {
            return execute(helper, pkb, this::action13);
        }
        if (pkb.pkt.getType() == 34525 && pkb.pkt.getPacket() instanceof Ipv6Packet && predicate_match_non_local_route(helper, pkb)) {
            return execute(helper, pkb, this::action14);
        }
        return table4(helper, pkb);
    }

    private FilterResult table4(PacketFilterHelper helper, PacketBuffer pkb) {
        return execute(helper, pkb, this::action15);
    }

    private FilterResult action0(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }

    private FilterResult action1(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_vpws_agent(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action2(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_vpws_agent(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action3(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_virtual_ssh(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action4(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_vpws_agent(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action5(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_virtual(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action6(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_virtual(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action7(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }

    private FilterResult action8(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }

    private FilterResult action9(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }

    private FilterResult action10(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }

    private FilterResult action11(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }

    private FilterResult action12(PacketFilterHelper helper, PacketBuffer pkb) {
        return invoke_ratelimit(helper, pkb);
    }

    private FilterResult action13(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_synthetic_ip(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action14(PacketFilterHelper helper, PacketBuffer pkb) {
        run_mod_dl_dst_to_synthetic_ip(helper, pkb);
        return FilterResult.PASS;
    }

    private FilterResult action15(PacketFilterHelper helper, PacketBuffer pkb) {
        return invoke_run_custom_flow(helper, pkb);
    }

    protected boolean predicate_drop_ipv6(PacketFilterHelper helper, PacketBuffer pkb) {
        return false;
    }

    protected boolean predicate_dl_src_is_vpws_agent(PacketFilterHelper helper, PacketBuffer pkb) {
        return false;
    }

    protected boolean predicate_disallow_dhcp(PacketFilterHelper helper, PacketBuffer pkb) {
        return false;
    }

    protected boolean predicate_whitelist(PacketFilterHelper helper, PacketBuffer pkb) {
        return false;
    }

    protected boolean predicate_blacklist(PacketFilterHelper helper, PacketBuffer pkb) {
        return false;
    }

    protected boolean predicate_requires_ratelimit(PacketFilterHelper helper, PacketBuffer pkb) {
        return false;
    }

    protected boolean predicate_match_non_local_route(PacketFilterHelper helper, PacketBuffer pkb) {
        return false;
    }

    protected void run_mod_dl_dst_to_vpws_agent(PacketFilterHelper helper, PacketBuffer pkb) {
    }

    protected void run_mod_dl_dst_to_virtual_ssh(PacketFilterHelper helper, PacketBuffer pkb) {
    }

    protected void run_mod_dl_dst_to_virtual(PacketFilterHelper helper, PacketBuffer pkb) {
    }

    protected void run_mod_dl_dst_to_synthetic_ip(PacketFilterHelper helper, PacketBuffer pkb) {
    }

    protected FilterResult invoke_ratelimit(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }

    protected FilterResult invoke_run_custom_flow(PacketFilterHelper helper, PacketBuffer pkb) {
        return FilterResult.DROP;
    }
}
