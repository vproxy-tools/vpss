vproxyss.currentPage = 'overview';

vproxyss.subPages = [
];

vproxyss.run = function (config, cb) {
  var data = config.data;
  data.overview = {
    data: {
      info: {},
      memInfo: {},
      networks: [],
      ifaces: {
        managed: [],
      },
    },
  };
  var methods = config.methods;
  methods.getTotalIpsCount = function () {
    var ips = {};
    var cnt = 0;
    for (var i = 0; i < data.overview.data.networks.length; ++i) {
      var net = data.overview.data.networks[i];
      if (!net.arp) {
        continue;
      }
      for (var j = 0; j < net.arp.length; ++j) {
        var arp = net.arp[j];
        if (!arp.ip) {
          continue;
        }
        if (ips[arp.ip]) {
          continue;
        }
        ips[arp.ip] = true;
        ++cnt;
      }
    }
    return cnt;
  };
  methods.getTotalMacCount = function () {
    var mac = {};
    var cnt = 0;
    for (var i = 0; i < data.overview.data.networks.length; ++i) {
      var net = data.overview.data.networks[i];
      if (!net.arp) {
        continue;
      }
      for (var j = 0; j < net.arp.length; ++j) {
        var arp = net.arp[j];
        if (!arp.mac) {
          continue;
        }
        if (mac[arp.mac]) {
          continue;
        }
        mac[arp.mac] = true;
        ++cnt;
      }
    }
    return cnt;
  };
  methods.getTotalFlow = function () {
    var total = 0;
    for (var i = 0; i < data.overview.data.ifaces.managed.length; ++i) {
      var iface = data.overview.data.ifaces.managed[i];
      if (!iface.statistics) {
        continue;
      }
      total += iface.statistics.historyTotalOutput;
    }
    var n = parseInt(total / 1024 / 1024 / 1024 * 100);
    if (n < 10) {
      return "0.0" + n;
    }
    if (n < 100) {
      return "0." + n;
    }
    var sn = "" + n;
    return sn.substring(0, sn.length - 2) + "." + sn.substring(sn.length - 2);
  };
  methods.goToNetwork = function () {
    Cookies.set('vpss-last-network-page', 'subnet');
    window.location.href = '/network.html';
  };
  methods.goToIface = function () {
    Cookies.set('vpss-last-network-page', 'iface');
    window.location.href = '/network.html';
  };

  var app = new Vue(config);

  function formatInfo(info) {
    if (info.currentTimeMillis) {
      info.currentTimeMillis = new Date(info.currentTimeMillis);
    }
    if (info.startTimeMillis) {
      info.startTimeMillis = new Date(info.startTimeMillis);
    }
    if (info.memTotal) {
      data.overview.data.memInfo.memTotal = info.memTotal;
      info.memTotal = info.memTotal + ' kB';
    }
    if (info.memFree) {
      data.overview.data.memInfo.memFree = info.memFree;
      info.memFree = info.memFree + ' kB'
    }
  }

  function recursiveGetArp(index, cb) {
    if (index === data.overview.data.networks.length) {
      return cb();
    }
    var net = data.overview.data.networks[index];
    vproxyss.handleResponse(app, data, vproxyss.httpGet(app, '/api/networks/' + net.vni + '/arp'), arp => {
      net.arp = arp;
      recursiveGetArp(index + 1, cb);
    });
  }

  function recursiveGetIfaceData(index, cb) {
    if (index === data.overview.data.ifaces.managed.length) {
      return cb();
    }
    var iface = data.overview.data.ifaces.managed[index];
    var endTs = Date.now();
    var beginTs = endTs - 6 * 3600 * 1000;
    var period = 5 * 60 * 1000;
    vproxyss.handleResponse(app, data, vproxyss.httpGet(app, '/api/statistics/ifaces/' + iface.name +
      '?beginTs=' + beginTs + '&endTs=' + endTs + '&period=' + period), data => {
        iface.statistics = data;
        recursiveGetIfaceData(index + 1, cb);
      });
  }

  function initIfacesCanvas() {
    // validate
    function getData(iface) {
      return iface.statistics.output;
    }
    var minBeginTs;
    var maxEndTs;
    var period;
    var tmpData;
    for (var i = 0; i < data.overview.data.ifaces.managed.length; ++i) {
      var iface = data.overview.data.ifaces.managed[i];
      var beginTs = getData(iface).beginTs;
      var endTs = getData(iface).endTs;
      period = (getData(iface).endTs - getData(iface).beginTs) / (getData(iface).data.length - 1);
      if (i === 0) {
        minBeginTs = beginTs;
        maxEndTs = endTs;
        tmpData = getData(iface);
        continue;
      }
      if (minBeginTs > beginTs) {
        minBeginTs = beginTs;
      }
      if (maxEndTs < endTs) {
        maxEndTs = endTs;
      }
      if (!vproxyss.checkStatisticsDataForDisplayingOnTheSameChart(tmpData, getData(iface))) {
        return;
      }
    }
    // pre format data
    var offsets = [];
    var endOffsets = [];
    for (var i = 0; i < data.overview.data.ifaces.managed.length; ++i) {
      var iface = data.overview.data.ifaces.managed[i];
      offsets.push((getData(iface).beginTs - minBeginTs) / period);
      endOffsets.push((maxEndTs - getData(iface).beginTs) / period);
    }
    // prepare data
    var labels = [];
    var datasets = [];
    for (var i = 0; i < data.overview.data.ifaces.managed.length; ++i) {
      var iface = data.overview.data.ifaces.managed[i];
      datasets.push({
        label: iface.name + ' (Mbit/s)',
        backgroundColor: vproxyss.color(i) + '55',
        borderColor: vproxyss.color(i),
        data: [],
        tension: 0.4,
        pointRadius: 0,
        fill: true,
      });
    }
    outer:
    for (var i = 0; ; ++i) {
      for (var j = 0; j < data.overview.data.ifaces.managed.length; ++j) {
        var iface = data.overview.data.ifaces.managed[j];
        var index = i + offsets[j];
        var endOff = endOffsets[j];
        if (index >= endOff) {
          break outer;
        } if (index < 0 || index >= getData(iface).data.length) {
          datasets[j].data.push(null);
        } else {
          datasets[j].data.push(getData(iface).data[index] * 1000 / period / 1024 / 1024);
        }
      }
      labels.push(vproxyss.hhmmss(minBeginTs + period * i));
    }

    var ctx = document.getElementById('overview-ifaces-chart').getContext('2d');
    this.chart = new Chart(ctx, {
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
          x: {
            ticks: {
              callback: function (val, index) {
                return index % 2 === 0 ? this.getLabelForValue(val) : '';
              },
            },
            grid: {
              display: false,
            },
          },
          y: {
            ticks: {
              beginAtZero: true
            },
            suggestedMin: 0,
            suggestedMax: 20,
          },
        }
      }
    });
  }

  vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/sys/info"), info => {
    formatInfo(info);
    data.overview.data.info = info;
    vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/networks"), res => {
      data.overview.data.networks = res;
      vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/ifaces"), res => {
        data.overview.data.ifaces.managed = res.managed;
        recursiveGetArp(0, () => {
          recursiveGetIfaceData(0, () => {
            initIfacesCanvas();
            cb(app);
          });
        });
      });
    });
  });
};
