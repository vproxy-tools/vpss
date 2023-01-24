vproxyss.currentPage = 'network';

vproxyss.subPages = [
  'network-iface',
  'network-vlan',
  'network-subnet',
  'network-ip',
  'network-route',
  'network-remote',
];

vproxyss.formatNetworksAndIfaces = function (data) {
  for (var i = 0; i < data.networks.length; ++i) {
    vproxyss.formatNetwork(data.networks[i]);
  }
  for (var i = 0; i < data.managed.length; ++i) {
    vproxyss.formatIface(data.managed[i]);
  }
  for (var i = 0; i < data.unmanaged.length; ++i) {
    vproxyss.formatIface(data.unmanaged[i]);
  }
};

vproxyss.formatNetwork = function (network) {
  network.new_ip = {
    ip: '',
    mac: '',
    routing: true,
  };
  network.new_route = {
    name: '',
    target: '',
    type: 'gateway',
    argument: '',
  };
  network.arp = [];
  network.new_arp = {
    iface: '',
    mac: '',
    ip: '',
  };
};

vproxyss.formatIface = function (iface) {
  iface.new_vlan = {
    remoteVLan: '',
    type: 'AUTO',
    localVni: '',
  };
};

vproxyss.run = function (config, cb) {
  var data = config.data;
  var methods = config.methods;
  data.network = {
    page: 'subnet',
    data: {
      managed: [],
      unmanaged: [],
      nicsTombstone: [],
      networks: [],
      new_net: {
        vni: '',
        v4net: '',
        v6net: '',
      },
      new_iface: {
        vni: '',
      },
    }
  };
  var lastPage = Cookies.get('vpss-last-network-page');
  if (!!lastPage) {
    data.network.page = lastPage;
  }
  methods.setPage = function (page) {
    data.network.page = page;
    Cookies.set('vpss-last-network-page', page);
  };
  methods.network_name = function (n) {
    return (n == 1 ? this.$t('network.default_network') + ' (' + n + ')' : '' + n);
  };
  methods.addNetwork = function () {
    if (data.network.data.new_net.vni == '') {
      vproxyss.alert(this, data, 'err.bad_args.missing_network_vni');
      return;
    }
    var vni = parseInt(data.network.data.new_net.vni);
    if (isNaN(vni)) {
      vproxyss.alert(this, data, 'err.bad_args.invalid_vni');
      return;
    }
    var promise = vproxyss.httpPost(this, '/api/networks/add', {
      vni: vni,
      v4net: vproxyss.formatString(data.network.data.new_net.v4net),
      v6net: vproxyss.formatString(data.network.data.new_net.v6net),
    });
    vproxyss.handleResponse(this, data, promise, (net) => {
      data.network.data.new_net.vni = '';
      data.network.data.new_net.v4net = '';
      data.network.data.new_net.v6net = '';
      vproxyss.formatNetwork(net);
      data.network.data.networks.push(net);
    });
  };
  methods.toggleAllowDisallowIpv6 = function (net) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + net.vni + '/toggle-allow-ipv6'), (o) => {
      net.allowIpv6 = o.allowIpv6;
      $('#network-allow-ipv6-' + net.vni).checkbox(o.allowIpv6 ? 'check' : 'uncheck');
    });
  };
  methods.delNetwork = function (vni) {
    vproxyss.confirm(this, data, 'confirm.network.del.header', 'confirm.network.del.content', () => {
      vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + vni + '/del'), () => {
        vproxyss.arrayRemove(data.network.data.networks, n => n.vni === vni);
      });
    });
  };
  methods.toggleEnableDisable = function (name) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/ifaces/' + name + '/toggle-enable'), (o) => {
      vproxyss.arrayOperate(data.network.data.managed, i => i.name == name, i => i.enable = o.enable);
      $('#managed-enable-' + name).checkbox(o.enable ? 'check' : 'uncheck');
    });
  };
  methods.toggleAllowDisallowDhcp = function (name) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/ifaces/' + name + '/toggle-allow-dhcp'), (o) => {
      vproxyss.arrayOperate(data.network.data.managed, i => i.name == name, i => i.allowDhcp = o.allowDhcp);
      $('#managed-dhcp-' + name).checkbox(o.allowDhcp ? 'check' : 'uncheck');
    });
  };
  methods.unmanageIface = function (iface) {
    vproxyss.confirm(this, data, 'confirm.iface.del.header', 'confirm.iface.del.content', () => {
      vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/ifaces/' + iface.name + '/del'), () => {
        vproxyss.arrayRemove(data.network.data.managed, i => i.name == iface.name);
        data.network.data.unmanaged.push({
          name: iface.name,
          speed: iface.speed,
        });
      });
    });
  };
  methods.manageIface = function (name) {
    if (data.network.data.new_iface.vni === '') {
      vproxyss.alert(this, data, 'err.bad_args.missing_iface_vni');
      return;
    }
    var vni = parseInt(data.network.data.new_iface.vni);
    if (isNaN(vni)) {
      vproxyss.alert(this, data, 'err.bad_args.invalid_iface_vni');
      return;
    }
    var promise = vproxyss.httpPost(this, '/api/ifaces/add', {
      name: name,
      vni: vni,
    });
    vproxyss.handleResponse(this, data, promise, (iface) => {
      vproxyss.formatIface(iface);
      data.network.data.managed.push(iface);
      vproxyss.arrayRemove(data.network.data.unmanaged, i => i.name == name);
    });
  };
  methods.addVLan = function (iface) {
    if (iface.new_vlan.remoteVLan === '') {
      vproxyss.alert(this, data, 'err.bad_args.missing_remote_vlan');
      return;
    }
    var remoteVLan = parseInt(iface.new_vlan.remoteVLan);
    if (isNaN(remoteVLan)) {
      vproxyss.alert(this, data, 'err.bad_args.invalid_remote_vlan');
      return;
    }
    if (iface.new_vlan.localVni === '') {
      vproxyss.alert(this, data, 'err.bad_args.missing_local_vni');
      return;
    }
    var localVni = parseInt(iface.new_vlan.localVni);
    if (isNaN(localVni)) {
      vproxyss.alert(this, data, 'err.bad_args.invalid_local_vni');
      return;
    }
    var promise = vproxyss.httpPost(this, '/api/vlan/' + iface.name + '/add', {
      remoteVLan: remoteVLan,
      localVni: localVni,
    });
    vproxyss.handleResponse(this, data, promise, (vlan) => {
      iface.new_vlan.remoteVLan = '';
      iface.new_vlan.localVni = '';
      iface.vlans.push(vlan);
    });
  };
  methods.delVLan = function (vif, vlan) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/vlan/' + vif.name + '/' + vlan.remoteVLan + '/del'), () => {
      vproxyss.arrayRemove(vif.vlans, v => v.remoteVLan == vlan.remoteVLan);
    });
  };
  methods.toggleEnableDisableVLan = function (vif, vlan) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/vlan/' + vif.name + '/' + vlan.remoteVLan + '/toggle-enable'), (o) => {
      vlan.enable = o.enable;
      $('#iface-' + vif.name + '-vlan-' + vlan.remoteVLan + '-enable').checkbox(o.enable ? 'check' : 'uncheck');
    });
  };
  methods.toggleAllowDisallowDhcpVLan = function (vif, vlan) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/vlan/' + vif.name + '/' + vlan.remoteVLan + '/toggle-allow-dhcp'), (o) => {
      vlan.allowDhcp = o.allowDhcp;
      $('#iface-' + vif.name + '-vlan-' + vlan.remoteVLan + '-allow-dhcp').checkbox(o.allowDhcp ? 'check' : 'uncheck');
    });
  };
  methods.addIp = function (net) {
    var promise = vproxyss.httpPost(this, '/api/networks/' + net.vni + '/ip/add', {
      ip: vproxyss.formatString(net.new_ip.ip),
      mac: vproxyss.formatString(net.new_ip.mac),
    });
    vproxyss.handleResponse(this, data, promise, (ip) => {
      net.new_ip.ip = '';
      net.new_ip.mac = '';
      net.ips.push(ip);
    });
  };
  methods.delIp = function (net, ip) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + net.vni + '/ip/' + ip.ip + '/del'), () => {
      vproxyss.arrayRemove(net.ips, i => i.ip == ip.ip);
    });
  };
  methods.formatIp = function (ip) {
    if (ip.indexOf('[') !== -1) {
      ip = ip.substring(1, ip.length - 1);
    }
    ip = ip.replaceAll(':', '-');
    ip = ip.replaceAll('.', '-');
    return ip;
  };
  methods.toggleIpRouting = function (net, ip) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + net.vni + '/ip/' + ip.ip + '/toggle-routing'), (o) => {
      ip.routing = o.routing;
      $('#net-' + net.vni + '-ip-routing-' + methods.formatIp(ip.ip)).checkbox(o.routing ? 'check' : 'uncheck');
    });
  };
  methods.addRoute = function (net) {
    var promise = vproxyss.httpPost(this, '/api/networks/' + net.vni + '/route/add', {
      name: vproxyss.formatString(net.new_route.name),
      target: vproxyss.formatString(net.new_route.target),
      type: vproxyss.formatString(net.new_route.type),
      argument: vproxyss.formatString(net.new_route.argument),
    });
    vproxyss.handleResponse(this, data, promise, (r) => {
      net.new_route.name = '';
      net.new_route.target = '';
      net.new_route.argument = '';
      net.routes.push(r);
    });
  };
  methods.delRoute = function (net, name) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + net.vni + '/route/' + name + '/del'), () => {
      vproxyss.arrayRemove(net.routes, r => r.name == name);
    });
  };
  methods.showNetworkArp = function (net) {
    if (net.show_arp) {
      net.show_arp = false;
      this.$forceUpdate();
      return;
    }
    net.show_arp = true;
    vproxyss.handleResponse(this, data, vproxyss.httpGet(this, '/api/networks/' + net.vni + '/arp'), (arp) => {
      net.arp = arp;
      this.$forceUpdate();
    });
  };
  methods.delArpEntry = function (net, arp) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + net.vni + '/arp/' + arp.mac + '/del'), () => {
      vproxyss.arrayRemove(net.arp, a => a.mac == arp.mac);
      this.$forceUpdate();
    });
  };
  methods.addArpEntry = function (net) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + net.vni + '/arp/add', {
      iface: vproxyss.formatString(net.new_arp.iface),
      mac: vproxyss.formatString(net.new_arp.mac),
      ip: vproxyss.formatString(net.new_arp.ip),
    }), () => {
      net.new_arp.iface = '';
      net.new_arp.mac = '';
      net.new_arp.ip = '';
      net.show_arp = false;
      methods.showNetworkArp.call(this, net);
    });
  };
  methods.showIfaceStatistics = function (iface) {
    if (iface.show_statistics) {
      iface.show_statistics = false;
      this.$forceUpdate();
      return;
    }
    iface.show_statistics = true;
    this.$forceUpdate();
    if (!iface.chart) {
      iface.chart = new vproxyss.FlowChart(this, data, 'iface-statistics-' + iface.name, '/api/statistics/ifaces/' + iface.name);
    }
    iface.chart.renderChart();
  };
  methods.setIfaceStatisticsDuration = function (iface, value) {
    if (iface.chart) {
      iface.statistics_duration = value;
      iface.chart.duration = value;
      iface.chart.renderChart();
      this.$forceUpdate();
    }
  };
  methods.showVLanStatistics = function (name, vlan) {
    if (vlan.show_statistics) {
      vlan.show_statistics = false;
      this.$forceUpdate();
      return;
    }
    vlan.show_statistics = true;
    this.$forceUpdate();
    if (!vlan.chart) {
      vlan.chart = new vproxyss.FlowChart(this, data, 'vlan-statistics-' + name + '-' + vlan.remoteVLan, '/api/statistics/vlan/' + name + '/' + vlan.remoteVLan);
    }
    vlan.chart.renderChart();
  };
  methods.setVLanStatisticsDuration = function (vlan, value) {
    if (vlan.chart) {
      vlan.statistics_duration = value;
      vlan.chart.duration = value;
      vlan.chart.renderChart();
      this.$forceUpdate();
    }
  };
  methods.toggleEnableDisableNetRemoteSw = function (net) {
    this.$nextTick(() => {
      $('#remote-sw-' + net.vni).checkbox(net.remote.enable ? 'check' : 'uncheck');
    });
    net.remote.modified = true;
  };
  methods.toggleAllowDisallowDhcpRemoteSw = function (net) {
    this.$nextTick(() => {
      $('#remote-sw-' + net.vni + '-dhcp').checkbox(net.remote.allowDhcp ? 'check' : 'uncheck');
    });
    net.remote.modified = true;
  };
  methods.netRemoteSwChanged = function (net) {
    net.remote.modified = true;
  };
  methods.updateRemoteSwitch = function (net) {
    if (net.remote.enable) {
      var ipport = vproxyss.formatString(net.remote.ipport);
      var username = vproxyss.formatString(net.remote.username);
      var password = vproxyss.formatString(net.remote.password);
      var promise = vproxyss.httpPost(this, '/api/networks/' + net.vni + '/remote/apply', {
        ipport: ipport,
        username: username,
        password: password,
        allowDhcp: !!net.remote.allowDhcp,
      });
      vproxyss.handleResponse(this, data, promise, (r) => {
        net.remote = r;
        net.remote.modified = false;
        this.$forceUpdate();
      });
    } else {
      vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/networks/' + net.vni + '/remote/disable'), () => {
        net.remote.enable = false;
        net.remote.ipport = '';
        net.remote.username = '';
        net.remote.password = '';
        net.remote.modified = false;
        this.$forceUpdate();
      });
    }
  };
  config.mounted = function () {
    $('#network-side_bar').sidebar({
      context: '#network-side_bar-context',
      dimPage: false,
      closable: false,
      transition: 'overlay',
      mobileTransition: 'overlay',
      onHide: function () {
        $('#network-side_bar-show').show();
      },
    });
    $('#network-side_bar-hide').click(function () {
      $('#network-side_bar').sidebar('hide');
    });
    $('#network-side_bar-show').click(function () {
      $('#network-side_bar').sidebar('show');
      $('#network-side_bar-show').hide();
    });
  };
  var app = new Vue(config);
  vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/networks"), res => {
    data.network.data.networks = res;
    vproxyss.formatNetworksAndIfaces(data.network.data);

    vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/ifaces"), res => {
      data.network.data.managed = res.managed;
      data.network.data.unmanaged = res.unmanaged;
      data.network.data.nicsTombstone = res.tombstone;
      vproxyss.formatNetworksAndIfaces(data.network.data);

      app.$nextTick(() => cb(app));
    });
  });
};
