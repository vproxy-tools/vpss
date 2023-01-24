vproxyss.currentPage = 'advance';

vproxyss.subPages = [
  'advance-limit',
  'advance-flow',
  'advance-wblist',
];

vproxyss.run = function (config, cb) {
  var data = config.data;
  var methods = config.methods;
  data.advance = {
    page: 'limit',
    data: {
      limits: [],
      flow: {
        modified: false,
        enable: false,
        flow: '',
      },
      wblists: [],
      new_limit: {
        name: '',
        sourceMac: '',
        target: '',
        type: 'downstream',
        value: '',
      },
      new_wblist: {
        name: '',
        sourceMac: '',
        target: '',
        type: 'black',
      },
    }
  };
  var lastPage = Cookies.get('vpss-last-advance-page');
  if (!!lastPage) {
    data.advance.page = lastPage;
  }
  methods.setPage = function (page) {
    data.advance.page = page;
    Cookies.set('vpss-last-advance-page', page);
  };
  methods.addLimit = function () {
    if (data.advance.data.new_limit.value === '') {
      vproxyss.alert(this, data, 'err.bad_args.missing_limit_value');
      return;
    }
    var limValue = parseInt(data.advance.data.new_limit.value);
    if (isNaN(limValue)) {
      vproxyss.alert(this, data, 'err.bad_args.invalid_limit_value');
      return;
    }
    var promise = vproxyss.httpPost(this, '/api/limits/add', {
      name: data.advance.data.new_limit.name,
      sourceMac: data.advance.data.new_limit.sourceMac,
      target: data.advance.data.new_limit.target,
      type: data.advance.data.new_limit.type,
      value: limValue,
    });
    vproxyss.handleResponse(this, data, promise, (lim) => {
      data.advance.data.new_limit.name = '';
      data.advance.data.new_limit.sourceMac = '';
      data.advance.data.new_limit.target = '';
      data.advance.data.new_limit.value = '';
      data.advance.data.limits.push(lim);
    });
  };
  methods.delLimit = function (name) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/limits/' + name + '/del'), () => {
      vproxyss.arrayRemove(data.advance.data.limits, lim => lim.name == name);
    });
  };
  methods.showLimitStatistics = function (limit) {
    if (limit.show_statistics) {
      limit.show_statistics = false;
      this.$forceUpdate();
      return;
    }
    limit.show_statistics = true;
    this.$forceUpdate();
    if (!limit.chart) {
      limit.chart = new vproxyss.FlowChart(this, data, 'limit-statistics-' + limit.name, '/api/statistics/limits/' + limit.name);
      limit.chart.getInput = (o) => o.data;
      limit.chart.getOutput = (o) => o.data;
      if (limit.type === 'upstream') {
        limit.chart.hasInput = () => false;
        limit.chart.outputName = 'upstream';
      } else {
        limit.chart.hasOutput = () => false;
        limit.chart.inputName = 'downstream';
      }
    }
    limit.chart.renderChart();
  };
  methods.setLimitStatisticsDuration = function (limit, value) {
    if (limit.chart) {
      limit.statistics_duration = value;
      limit.chart.duration = value;
      limit.chart.renderChart();
      this.$forceUpdate();
    }
  };
  methods.toggleEnableDisableFlow = function () {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/flow/toggle-enable'), (o) => {
      data.advance.data.flow.enable = o.enable;
      $('#flow-enable').checkbox(o.enable ? 'check' : 'uncheck');
    });
  };
  methods.updateFlow = function () {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/flow/update', {flow: data.advance.data.flow.flow}), (o) => {
      data.advance.data.flow.enable = o.enable;
      $('#flow-enable').checkbox(o.enable ? 'check' : 'uncheck');
      data.advance.data.flow.modified = false;
    });
  };
  methods.flowChanged = function () {
    console.log(data.advance.data.flow);
    data.advance.data.flow.modified = true;
  };
  methods.addWBList = function () {
    var promise = vproxyss.httpPost(this, '/api/wblists/add', {
      name: data.advance.data.new_wblist.name,
      sourceMac: data.advance.data.new_wblist.sourceMac,
      target: data.advance.data.new_wblist.target,
      type: data.advance.data.new_wblist.type,
    });
    vproxyss.handleResponse(this, data, promise, (wblist) => {
      data.advance.data.new_wblist.name = '';
      data.advance.data.new_wblist.sourceMac = '';
      data.advance.data.new_wblist.target = '';
      data.advance.data.new_wblist.value = '';
      data.advance.data.wblists.push(wblist);
    });
  };
  methods.delWBList = function (name) {
    vproxyss.handleResponse(this, data, vproxyss.httpPost(this, '/api/wblists/' + name + '/del'), () => {
      vproxyss.arrayRemove(data.advance.data.wblists, wblist => wblist.name === name);
    });
  };
  config.mounted = function () {
    $('#advance-side_bar').sidebar({
      context: '#advance-side_bar-context',
      dimPage: false,
      closable: false,
      transition: 'overlay',
      mobileTransition: 'overlay',
      onHide: function () {
        $('#advance-side_bar-show').show();
      },
    });
    $('#advance-side_bar-hide').click(function () {
      $('#advance-side_bar').sidebar('hide');
    });
    $('#advance-side_bar-show').click(function () {
      $('#advance-side_bar').sidebar('show');
      $('#advance-side_bar-show').hide();
    });
  };
  var app = new Vue(config);
  vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/limits"), res => {
    data.advance.data.limits = res;

    vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/flow/config"), res => {
      data.advance.data.flow.enable = res.enable;
      data.advance.data.flow.flow = res.flow;

      vproxyss.handleResponse(app, data, vproxyss.httpGet(app, "/api/wblists"), res => {
        data.advance.data.wblists = res;
      });
      app.$nextTick(() => cb(app));
    });
  });
};
