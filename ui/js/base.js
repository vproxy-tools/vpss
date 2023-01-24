var vproxyss = {};

vproxyss.load = function (name, cb) {
  var ok = function (data) {
    var parser = new DOMParser();
    var doc = parser.parseFromString(data, "text/html");
    var node = doc.getElementById('app-' + name);
    $("#" + name).append(node);
    cb();
  };
  var reject = function () {
    cb();
  };
  Vue.http.get(name + '.html?rand=' + parseInt(Math.random() * 1000)).then(response => {
    var status = response.status;
    var data = response.body;
    if (status != 200) {
      var msg = 'requesting ' + name + '.html failed: ' + status;
      console.error(msg);
      reject(msg);
    } else {
      ok(data);
    }
  }, err => {
    if (err.body && err.body.startsWith('<!-- ok -->')) {
      ok(err.body);
    } else {
      console.error('requesting ' + name + '.html failed:', err);
      reject(err);
    }
  });
};

vproxyss.loadAll = function (array, cb) {
  function loadAll0(idx) {
    if (idx >= array.length) {
      return cb();
    }
    vproxyss.load(array[idx], () => loadAll0(idx + 1));
  }

  loadAll0(0);
};

vproxyss.availableColors = {
  'red': '#B03060',
  'orange': '#FE9A76',
  'yellow': '#FFD700',
  'olive': '#32CD32',
  'green': '#016936',
  'teal': '#008080',
  'blue': '#0E6EB8',
  'violet': '#EE82EE',
  'purple': '#B413EC',
  'pink': '#FF1493',
  'brown': '#A52A2A',
  'grey': '#A0A0A0',
  'black': '#000000',
};

vproxyss.availableColorNames = [
  'teal', 'orange', 'red', 'yellow', 'olive', 'green', 'blue', 'violet', 'purple', 'pink', 'brown', 'grey', 'black',
];

vproxyss.color = function (name) {
  if (typeof name === 'number') {
    var names = vproxyss.availableColorNames;
    var color = names[parseInt(name) % names.length];
    return vproxyss.availableColors[color];
  }
  var ret = vproxyss.availableColors[name];
  if (ret) {
    return ret;
  }
  return vproxyss.availableColors['teal'];
};

vproxyss.app = function () {
  $(document).ready(function () {
    vproxyss._app();
  });
};

vproxyss._app = function () {
  var pages = [
    'alert', 'confirm', 'header-menu'
  ];
  for (var i = 0; i < vproxyss.subPages.length; ++i) {
    pages.push(vproxyss.subPages[i]);
  }
  vproxyss.loadAll(pages, () => {
    var data = {
      general: {
        alert_header: '',
        alert: '',
        page: vproxyss.currentPage,
      },
      theme: {
        'primary': 'teal',
        'secondary': 'orange',
        'danger': 'red',
        'alert': 'yellow',
      },
      session: {
        username: '???',
      },
      upgrade: {
        upgrade: false,
        vpss: {
          current: '',
          latest: 'unknown'
        },
        vproxy: {
          current: '',
          latest: 'unknown'
        },
      },
    };
    var methods = {
      vueForceUpdate: function (f) {
        f();
        this.$forceUpdate();
      },
      logout: function () {
        vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/logout'), () => {
          Cookies.remove('vpss-session');
          window.location.href = "/login.html";
        });
      },
      persist: function () {
        vproxyss.handleResponse(this, data, vproxyss.httpGet(this, '/api/sys/config.json'), (o) => {
          console.log('-------- BEGIN text version --------')
          console.log(o.text);
          console.log('-------- END text version --------')
          console.log('-------- BEGIN json version --------')
          console.log(o.json);
          console.log('-------- END json version --------')
          vproxyss.confirm(this, data, 'menu.system.persist_current_config.header', 'menu.system.persist_current_config.content', () => {
            vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/sys/persist'), () => {
              vproxyss.alert(this, data, 'menu.system.persist_current_config.success.content');
            });
          });
        });
      },
      reboot: function () {
        vproxyss.confirm(this, data, 'confirm.reboot.header', 'confirm.reboot.content',
            () => vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/sys/reboot'),
                () => vproxyss.alert(this, data, 'alert.will_reboot')));
      },
      shutdown: function () {
        vproxyss.confirm(this, data, 'confirm.shutdown.header', 'confirm.shutdown.content',
            () => vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/sys/shutdown'),
                () => vproxyss.alert(this, data, 'alert.will_shutdown')));
      },
      checkForUpdates: function () {
        vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/sys/upgrade/check'),
            (res) => {
              data.upgrade.upgrade = res.upgrade;
              data.upgrade.vpss.latest = res.vpss;
              data.upgrade.vproxy.latest = res.vproxy;
              if (!res.upgrade) {
                vproxyss.alert(this, data, 'alert.check_upgrade.no_upgrade')
              }
            });
      },
      doUpgrade: function () {
        vproxyss.confirm(this, data, 'confirm.upgrade.header',
            'vpss: ' + data.upgrade.vpss.current + " => " + data.upgrade.vpss.latest +
            ', vproxy: ' + data.upgrade.vproxy.current + " => " + data.upgrade.vproxy.latest,
            () => vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/sys/upgrade'),
                () => vproxyss.alert(this, data, 'alert.will_upgrade')));
      },
    };
    var config = {
      el: '#app',
      data: data,
      methods: methods,
      updated: function () {
        this.$nextTick(function () {
          vproxyss._refreshJQUI(this);
        });
      },
      i18n: vproxyss.initI18n(),
    };
    vproxyss.run(config, vue => vproxyss.postInit(vue, data, methods));
  });
};

vproxyss.postInit = function (vue, data) {
  if (window.location.pathname !== '/login.html') {
    vproxyss.handleResponse(vue, data, vproxyss.httpGet(vue, '/api/sys/upgrade/versions'), res => {
      data.upgrade.upgrade = res.upgrade;
      data.upgrade.vpss.current = res.vpss.current;
      data.upgrade.vpss.latest = res.vpss.latest;
      data.upgrade.vproxy.current = res.vproxy.current;
      data.upgrade.vproxy.latest = res.vproxy.latest;
    });
  }
  var sessionId = Cookies.get('vpss-session');
  if (sessionId) {
    vproxyss.handleResponse(vue, data, vproxyss.httpGet(vue, '/api/whoami'), res => {
      data.session.username = res.username;
    });
  }
};

vproxyss.confirm = function (vue, data, header, content, okfunc) {
  vue.$nextTick(function () {
    data.general.alert_header = header;
    data.general.alert = content;
    $('#show-confirm').modal({
      onApprove: function () {
        $('#show-confirm').modal({
          onApprove: () => {
          }
        });
        okfunc();
      },
    })
        .modal('show');
  });
};

vproxyss.alert = function (vue, data, content, okfunc) {
  vue.$nextTick(function () {
    data.general.alert = content;
    $('#show-alert').modal({
      onApprove: function () {
        $('#show-alert').modal({
          onApprove: () => {
          }
        });
        if (okfunc) {
          okfunc();
        }
      },
    }).modal('show');
  });
};

vproxyss._refreshJQUI = function (vue) {
  $('.ui.dropdown').dropdown();
  $('.ui.checkbox').checkbox();
  vproxyss.refreshJQUI(vue);
};

vproxyss.refreshJQUI = function () {
};

vproxyss.handleResponse = function (vue, vueData, promise, successCb, failCb) {
  function callFail(response, reason) {
    console.log('[ERR ] request ' + response.url + " failed: " + reason);
    var showAlert = true;
    if (failCb) {
      showAlert = failCb(response, reason);
    }
    if (showAlert === true) {
      vue.$nextTick(() => {
        vueData.general.alert = reason;
        vue.$nextTick(() => {
          $('#show-alert').modal({
            onApprove: function () {
              $('#show-alert').modal({
                onApprove: () => {
                }
              });
              if (reason === 'err.forbidden') {
                Cookies.remove('vpss-session');
                if (window.location.pathname !== '/login.html') {
                  window.location.href = '/login.html';
                }
              }
            },
          }).modal('show');
        });
      });
    }
  }

  promise.then(response => {
    if (response.status !== 200) {
      return callFail(response, 'response is not 200');
    }
    response.text().then(body => {
      var body;
      if (response.body) {
        try {
          body = JSON.parse(body);
        } catch (e) {
          return callFail(response, 'invalid body: not json: ' + body);
        }
      } else {
        return callFail(response, 'requesting ' + response.url + ' failed, missing response body');
      }
      if (!body.ok) {
        return callFail(response, body.error);
      }
      vue.$nextTick(() => successCb(body.result));
    }, err => callFail(response, "failed to retrieve body: " + err));
  }, response => {
    callFail(response, 'err.network');
  });
};

vproxyss.httpGet = function (vue, url) {
  return vue.$http.get(url, {
    timeout: 5000,
  });
};

vproxyss.httpPost = function (vue, url, body) {
  return vue.$http.post(url, body, {
    timeout: 5000,
  });
};

vproxyss.formatString = function (s) {
  if (s === '') {
    return undefined;
  } else {
    return s;
  }
};

vproxyss.arrayRemove = function (array, predicate) {
  for (var i = 0; i < array.length; ++i) {
    if (predicate(array[i])) {
      array.splice(i, 1);
      --i;
    }
  }
};

vproxyss.arrayOperate = function (array, predicate, op) {
  for (var i = 0; i < array.length; ++i) {
    if (predicate(array[i])) {
      op(array[i]);
    }
  }
};

vproxyss.hhmmss = function (ts) {
  var date = new Date(ts);
  var hour = date.getHours();
  if (hour < 10) {
    hour = "0" + hour;
  }
  var min = date.getMinutes();
  if (min < 10) {
    min = "0" + min;
  }
  var sec = date.getSeconds();
  if (sec < 10) {
    sec = "0" + sec;
  }
  return hour + ':' + min + ':' + sec;
};

vproxyss.checkStatisticsDataForDisplayingOnTheSameChart = function (a, b) {
  if ((a.endTs - a.beginTs) / a.data.length !== (b.endTs - b.beginTs) / b.data.length) {
    vproxyss.alert(vue, data, 'err.invalid_statistics_result');
    return false;
  }
  var period = (a.endTs - a.beginTs) / (a.data.length - 1);
  if (period != parseInt(period)) {
    vproxyss.alert(vue, data, 'err.invalid_statistics_result');
    return false;
  }
  if (period !== (b.endTs - b.beginTs) / (b.data.length - 1)) {
    vproxyss.alert(vue, data, 'err.invalid_statistics_result');
    return false;
  }
  if ((a.endTs - b.endTs) % period !== 0) {
    vproxyss.alert(vue, data, 'err.invalid_statistics_result');
    return false;
  }
  if ((a.beginTs - b.beginTs) % period !== 0) {
    vproxyss.alert(vue, data, 'err.invalid_statistics_result');
    return false;
  }
  return true;
};

vproxyss.initI18n = function () {
  var messages = {
    zh_cn: {
      'alert': '提示',
      'ok': '好',
      'cancel': '取消',

      'confirm.reboot.header': '是否重新启动设备',
      'confirm.reboot.content': '重新启动过程中，网络将无法正常使用',
      'alert.will_reboot': '设备即将重新启动',
      'confirm.shutdown.header': '是否关闭设备',
      'confirm.shutdown.content': '关机后将无法访问网络',
      'alert.will_shutdown': '设备即将关闭',
      'alert.check_upgrade.no_upgrade': '已经是最新版本',
      'confirm.upgrade.header': '是否执行更新',
      'confirm.upgrade.content': '',
      'alert.will_upgrade': '正在更新，稍后系统将自动重启',

      'confirm.network.del.header': '确认删除网络？',
      'confirm.network.del.content': '删除网络后，该网络中的IP、路由表均会被删除',
      'confirm.iface.del.header': '确认不再管理该网口？',
      'confirm.iface.del.content': '移出管控后，其对应的VLan子接口也会被删除',

      "err.network": "网络异常，请求失败",
      'err.forbidden': '认证信息错误',
      "err.bad_args.pass_length_too_short": "密码过短",
      "err.bad_args.missing_iface_name": "未指定网口名",
      "err.bad_args.missing_iface_vni": "未指定网口所在的虚拟网络标识",
      "err.bad_args.invalid_iface_vni": "网口的虚拟网络标识错误",
      "err.conflict.iface": "网口重复",
      "err.not_found.unmanaged_iface": "找不到该未管控的网口",
      "err.not_found.managed_iface": "找不到该受管控的网口",
      "err.precondition_failed.too_few_managed_ifaces": "至少要有一个受管控的网口",
      "err.bad_args.missing_remote_vlan": "未指定远端VLan号",
      "err.bad_args.missing_local_vni": "未指定本地虚拟网络标识",
      "err.bad_args.invalid_remote_vlan": "远端VLan号错误",
      "err.bad_args.invalid_local_vni": "本地虚拟网络标识错误",
      "err.not_found.vlan_iface": "找不到该VLan子接口",
      "err.conflict.vlan_iface": "VLan子接口冲突",
      "err.conflict.network": "网络号重复",
      "err.not_found.network": "找不到该网络",
      "err.forbidden.del_system_network": "不能删除系统网络",
      "err.bad_args.missing_ip_ip": "没有指定IP地址",
      "err.bad_args.missing_ip_mac": "没有指定Mac地址",
      "err.conflict.ip": "IP冲突",
      "err.not_found.ip": "找不到该IP",
      "err.bad_args.invalid_ip": "IP格式错误",
      "err.forbidden.del_system_ip": "不能删除系统IP",
      "err.forbidden.update_system_ip": "不能更新系统IP",
      "err.bad_args.missing_network_vni": "没有指定虚拟网络标识",
      "err.bad_args.missing_network_v4net": "没有指定IPv4网段",
      "err.bad_args.invalid_vni": "虚拟网络标识错误",
      "err.bad_args.missing_route_name": "没有指定路由名称",
      "err.bad_args.missing_route_type": "没有指定路由类型",
      "err.conflict.route": "路由重复",
      "err.bad_args.invalid_route_argument.take_no_args": "该路由参数应当为空",
      "err.bad_args.invalid_route_argument.not_valid_gateway_ip": "参数中的网关IP格式错误",
      "err.bad_args.invalid_route_argument.not_valid_vni": "参数中的虚拟网络标识错误",
      "err.bad_args.invalid_route_argument.must_be_another_vni": "必须指定本网络以外的另一个虚拟网络标识",
      "err.bad_args.invalid_route_type": "错误的路由类型",
      "err.not_found.route": "找不到该路由",
      "err.forbidden.del_system_route": "不能删除系统路由",
      "err.bad_args.missing_limit_name": "没有指定限流策略名称",
      "err.bad_args.missing_limit_source_mac": "没有指定源Mac地址",
      "err.bad_args.missing_limit_target": "没有指定目标IP地址",
      "err.bad_args.missing_limit_type": "没有指定限流类型",
      "err.bad_args.missing_limit_value": "没有指定限流限制",
      "err.bad_args.invalid_limit_value": "限流限制不正确",
      "err.bad_args.invalid_limit_neither_mac_nor_ip_provided": "源Mac地址和目标IP地址均未明确",
      "err.conflict.limit_name": "限流名称冲突",
      "err.conflict.limit_mac": "基于源Mac地址的限流策略冲突",
      "err.conflict.limit_ip": "基于目标IP地址的限流策略冲突",
      "err.conflict.limit_mac_and_ip": "基于源Mac地址和目标IP地址的限流策略冲突",
      "err.not_found.limit": "找不到该限流策略",
      "err.bad_args.invalid_limit_mac": "Mac地址格式错误",
      "err.bad_args.invalid_limit_ip": "IP地址格式错误",
      "err.bad_args.invalid_mac_in_arp": "Mac地址格式错误",
      "err.bad_args.invalid_username_length": "用户名长度错误",
      "err.bad_args.invalid_username_char": "用户名包含不可用的字符",
      "err.bad_args.missing_ipport": "没有指定地址:端口",
      "err.bad_args.missing_username": "没有指定用户名",
      "err.bad_args.missing_password": "没有指定密码",
      "err.bad_args.missing_wblist_name": "没有指定黑/白名单策略名称",
      "err.bad_args.missing_wblist_source_mac": "没有指定源Mac地址",
      "err.bad_args.missing_wblist_target": "没有指定目标IP地址",
      "err.bad_args.missing_wblist_type": "没有指定黑/白名单类型",
      "err.bad_args.invalid_wblist_neither_mac_nor_ip_provided": "源Mac地址和目标IP地址均未明确",
      "err.conflict.wblist_name": "黑/白名单名称冲突",
      "err.conflict.wblist_mac": "基于源Mac地址的黑/白名单策略冲突",
      "err.conflict.wblist_ip": "基于目标IP地址的黑/白名单策略冲突",
      "err.not_found.wblist": "找不到该黑/白名单策略",
      "err.bad_args.invalid_wblist_mac": "Mac地址格式错误",
      "err.bad_args.invalid_wblist_ip": "IP地址格式错误",

      "err.bad_args.invalid_begin_ts": "起始时间戳错误",
      "err.bad_args.invalid_end_ts": "结束时间戳错误",
      "err.bad_args.invalid_period": "统计周期错误",
      "err.bad_args.begin_ts_ge_end_ts": "起始时间戳 >= 结束时间戳",
      "err.bad_args.period_ge_duration": "统计周期 >= 总统计时间",
      "err.invalid_statistics_result": "服务端返回的统计结果格式错误",

      "err.bad_args.missing_old_password": "未指定原密码",
      "err.bad_args.missing_new_password": "未指定新密码",
      "err.forbidden.old_password_wrong": "原密码错误",

      "err.bad_args.missing_vpws_agent_config": "没有指定配置文件",

      'ipv6 not allowed': '该网络不支持IPv6',

      'title.login': '登录 VProxy Soft Switch',
      'title.overview': '总览',
      'title.network': 'VPSS 网络配置',
      'title.user': '用户选项',
      'title.vpws_agent': 'VPWS Agent',
      'title.advance': 'VPSS 高级配置',

      'operation': '操作',
      'optional': '可选',
      'login.login_header': 'VProxy Soft Switch 控制台',
      'login.username': '用户名',
      'login.password': '密码',
      'login.login_button': '登录',
      'login.forget_password': '忘记密码？第一次登录？',
      'login.background_image_source': '背景图片来源',
      'login.forget_password_title': '忘记密码怎么办？',
      'login.forget_password_content': 'VProxy Soft Switch 交互界面不提供密码恢复功能，但您可以通过SSH等方式登录机器，删除 /etc/vpss/passwd 以清除密码。',
      'login.first_time_login_title': '第一次登录？',
      'login.first_time_login_content': '首次登录时请使用用户名“admin”，首次登录时输入的密码将会被设置为该用户的密码（不得少于8个字符），后续也可以在管理页面修改。',
      'menu.overview': '总览',
      'menu.network_config': '网络配置',
      'menu.advance_config': '高级配置',
      'menu.vpws_agent': 'VPWS Agent',
      'menu.system': '系统',
      'menu.system.persist_current_config': '持久化当前配置',
      'menu.system.persist_current_config.header': '确定持久化当前配置？',
      'menu.system.persist_current_config.content': '配置已在控制台打印。持久化的配置在重启后也会生效，请确认当前配置正确无误',
      'menu.system.persist_current_config.success.content': '配置持久化成功',
      'menu.system.reboot': '重新启动',
      'menu.system.shutdown': '关机',
      'menu.system.checkForUpdates': '检查更新',
      'menu.system.upgrade': '更新',
      'menu.user.settings': '账户设置',
      'menu.user.logout': '退出账号',
      'network.iface_config': '网口配置',
      'network.vlan_config': 'VLAN配置',
      'network.subnet_management': '虚拟网络管理',
      'network.ip_management': 'IP管理',
      'network.routing_tables': '路由表',
      'network.dhcp_management': 'DHCP管理',
      'advance.netflow_limit': '流量限制',
      'network.managed_ifaces': '管控中的网口',
      'network.unmanaged_ifaces': '未管控的网口',
      'network.tombstone_ifaces': '已配置但没有找到的网口',
      'network.tombstone_ifaces.hint': '持久化配置后，这些网口均会被清除',
      'network.default_network': '默认网络',
      'network.iface.ifname': '网口名',
      'network.iface.ifenabled': '启用',
      'network.iface.allow_dhcp': '允许DHCP',
      'network.iface.ifspeed': '速度',
      'network.iface.remove': '不再管理',
      'network.iface.no_unmanaged_ifaces': '没有未管控的网口',
      'network.iface.add': '管理',
      'network.iface.add_iface_vni': '待添加网卡的VNI',
      'network.vlan': 'VLAN号',
      'network.vni': '虚拟网络标识',
      'network.remote_vlan': '远端VLAN号',
      'network.local_vni': '本地虚拟网络标识',
      'network.vlan.no_vlans': '没有VLAN配置',
      'network.vlan.add': '加入VLAN',
      'network.vlan.type': '类型',
      'network.vlan.auto_type': '自动',
      'network.vlan.remove': '移出VLAN',
      'network.subnet.v4net': 'IPv4网段',
      'network.subnet.v6net': 'IPv6网段',
      'network.subnet.allow_ipv6': '允许IPv6',
      'network.subnet.add': '添加虚拟网络',
      'network.subnet.remove': '删除',
      'network.ip': 'IP地址',
      'network.mac': 'Mac地址',
      'network.ip.no_ip': '没有虚拟IP',
      'network.ip.add': '添加IP',
      'network.ip.routing': '可作路由使用',
      'network.ip.remove': '移除',
      'network.route.name': '名称',
      'network.route.target': '目标网段',
      'network.route.type': '路由转发类型',
      'network.route.argument': '路由参数',
      'network.route.type.local': '本地路由',
      'network.route.type.gateway': '网关路由',
      'network.route.type.intervlan': 'VLAN间路由',
      'network.route.add': '添加路由',
      'network.route.remove': '移除',
      'network.dhcp.allow-server': '允许DHCP Server',
      'advance.limit.head': '限流配置',
      'advance.limit.no_limits': '没有限流配置',
      'advance.limit.value': '数值',
      'advance.limit.name': '名称',
      'advance.limit.source_mac': '源Mac',
      'advance.limit.target': '目标地址',
      'advance.limit.source_mac.placeholder': '源Mac，可填写*',
      'advance.limit.target.placeholder': '目标地址，可填写*',
      'advance.limit.type': '限速类型',
      'advance.limit.type.upstream': '上行',
      'advance.limit.type.downstream': '下行',
      'advance.limit.add': '添加限流',
      'advance.limit.remove': '移除',
      'network.arp.iface': '网口',
      'network.arp.mac': 'Mac',
      'network.arp.ip': 'IP',
      'network.arp.mac_ttl': 'MAC-TTL',
      'network.arp.arp_ttl': 'ARP-TTL',
      'network.arp.remove': '根据Mac移除',
      'network.arp.no_arp_entries': '没有Arp/Mac表记录',
      'network.arp.add': '添加邻居记录',

      'overview.active_ips': '活跃IP数量',
      'overview.active_devices': '活跃设备数量',
      'overview.total_netflow': '总流量（出向）',
      'overview.mem_free': '内存余量',
      'overview.info': '系统信息',
      'overview.info.currentTimeMillis': '当前时间',
      'overview.info.startTimeMillis': '启动时间',
      'overview.info.cpuModel': 'CPU型号',
      'overview.info.cpuCount': 'CPU核心数',
      'overview.info.memTotal': '内存总量',
      'overview.info.memFree': '内存余量',
      'overview.info.runEnv': '运行环境',
      'overview.info.kernelVersion': '内核版本',

      'network.remote_switch': '远程交换机',
      'network.remote.link_to_remote': '连接远程交换机',
      'network.remote.config_key': '配置项',
      'network.remote.config_value': '参数',
      'network.remote.remote_is_disabled': '远程交换机已禁用',
      'network.remote.ip_and_port': '地址:端口',
      'network.remote.username': '用户名',
      'network.remote.password': '密码',
      'network.remote.allow_dhcp': '允许DHCP',
      'network.remote.apply': '更新配置',

      'advance.flow_table': '流表',
      'advance.flow_table.title': '流表',
      'advance.flow_table.enable': '启用自定义流表',
      'advance.flow_table.flow': '流表内容',
      'advance.flow_table.apply': '更改配置',

      'advance.wblist': '黑/白名单',
      'advance.wblist.head': '黑/白名单配置',
      'advance.wblist.no_wblists': '没有黑/白名单配置',
      'advance.wblist.name': '名称',
      'advance.wblist.source_mac': '源/目Mac',
      'advance.wblist.target': '目/源地址',
      'advance.wblist.source_mac.placeholder': '可填写Mac或*',
      'advance.wblist.target.placeholder': '可填写IP、*或CIDR',
      'advance.wblist.type': '黑/白名单类型',
      'advance.wblist.type.white': '白名单',
      'advance.wblist.type.black': '黑名单',
      'advance.wblist.add': '添加黑/白名单',
      'advance.wblist.remove': '移除',

      'statistics.last15minutes': '最近15分钟',
      'statistics.last1hour': '最近1小时',
      'statistics.last3hours': '最近3小时',
      'statistics.last6hours': '最近6小时',
      'statistics.last1day': '最近1天',

      'user.change_password': '修改密码',
      'user.old_password': '原密码',
      'user.new_password': '新密码',
      'user.confirm_password': '新密码确认',
      'user.submit_password': '提交',
      'user.repeat_password_not_the_same': '“新密码”和“新密码确认”不一致',
      'user.password_changed': '密码修改成功',

      'vpwsagent.status': '当前状态',
      'vpwsagent.status.running': '运行中',
      'vpwsagent.status.starting': '启动中',
      'vpwsagent.status.pending': '等待启动',
      'vpwsagent.status.stopped': '已停止',
      'vpwsagent.status.unknown': '未知',
      'vpwsagent.config': '配置文件',
      'vpwsagent.submit_config': '提交',
      'vpwsagent.config_submitted': '配置已提交，新配置加载可能还需一段时间',
      'vpwsagent.confighint': '注意：1. 请在配置中开启direct-relay，监听127.0.0.1:8888，处理100.96.0.0/12网段。2. 请在配置中开启DNS，监听53端口',
    },
  };
  var i18n = new VueI18n({
    locale: 'zh_cn',
    messages: messages,
  });
  return i18n;
};

vproxyss.FlowChart = function (vue, data, name, url) {
  if (!this) {
    return new vproxyss.FlowChart(url);
  }
  this.node = document.getElementById(name);
  this.ctx = this.node.getContext('2d');
  this.duration = 900000;
  this.getInput = function (o) {
    return o.input;
  };
  this.getOutput = function (o) {
    return o.output;
  };
  this.hasInput = function () {
    return true;
  };
  this.hasOutput = function () {
    return true;
  };
  this.inputName = 'input';
  this.outputName = 'output';
  var draw = (labels, input, output) => {
    if (this.chart) {
      this.chart.destroy();
      this.chart = null;
    }
    var datasets = [];
    if (this.hasInput()) {
      datasets.push({
        label: this.inputName + ' (Mbit/s)',
        backgroundColor: 'rgba(0,0,0,0)',
        borderColor: vproxyss.color(data.theme.primary),
        data: input,
        tension: 0.4
      });
    }
    if (this.hasOutput()) {
      datasets.push({
        label: this.outputName + ' (Mbit/s)',
        backgroundColor: 'rgba(0,0,0,0)',
        borderColor: vproxyss.color(data.theme.secondary),
        data: output,
        tension: 0.4
      });
    }
    this.chart = new Chart(this.ctx, {
      height: 100,
      type: 'line',
      data: {
        labels: labels,
        datasets: datasets,
      },
      options: {
        responsive: true,
        interaction: {
          intersect: false,
        },
        scales: {
          y: {
            ticks: {
              beginAtZero: true
            },
            suggestedMin: 0,
            suggestedMax: 10,
          }
        }
      }
    });
  };
  this.renderChart = function () {
    var now = Date.now();
    var start = now - this.duration;
    vproxyss.handleResponse(vue, data, vproxyss.httpGet(vue, url + '?beginTs=' + start + '&endTs=' + now), (o) => {
      var labels = [];
      var input = [];
      var output = [];
      var oInput = this.getInput(o);
      var oOutput = this.getOutput(o);
      var beginTs = Math.min(oInput.beginTs, oOutput.beginTs);
      if (!vproxyss.checkStatisticsDataForDisplayingOnTheSameChart(oInput, oOutput)) {
        return;
      }
      var period = (oInput.endTs - oInput.beginTs) / (oInput.data.length - 1);
      var inputOff = 0;
      var outputOff = 0;
      if (oInput.beginTs < oOutput.beginTs) {
        outputOff = -(oOutput.beginTs - oInput.beginTs) / period;
      } else if (oInput.beginTs > oOutput.beginTs) {
        inputOff = -(oInput.beginTs - oOutput.beginTs) / period;
      }
      for (var i = 0; ; ++i) {
        // skip the last record because it's not full
        if (i + inputOff >= oInput.data.length - 1 && i + outputOff >= oOutput.data.length - 1) {
          break;
        }
        labels.push(vproxyss.hhmmss(beginTs + period * i));
        if (i + inputOff >= 0 && i + inputOff < oInput.data.length) {
          input.push(oInput.data[i + inputOff] * 1000 / period / 1024 / 1024);
        } else {
          input.push(null);
        }
        if (i + outputOff >= 0 && i + outputOff < oOutput.data.length) {
          output.push(oOutput.data[i + outputOff] * 1000 / period / 1024 / 1024);
        } else {
          output.push(null);
        }
      }
      draw(labels, input, output);
    });
  };
};
