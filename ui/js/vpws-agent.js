vproxyss.currentPage = 'vpws-agent';

vproxyss.subPages = [
];

vproxyss.run = function (config, cb) {
  var data = config.data;
  data.vpwsagent = {
    data: {
      status: 'unknown',
      config: '',
    },
  };
  var methods = config.methods;
  methods.applyConfig = function () {
    var promise = vproxyss.httpPost(this, '/api/vpws-agent/config/update', {
      config: vproxyss.formatString(data.vpwsagent.data.config)
    });
    vproxyss.handleResponse(this, data, promise, (o) => {
      data.vpwsagent.data.status = o.status;
      vproxyss.alert(this, data, 'vpwsagent.config_submitted');
    });
  };
  methods.currentStatusColor = function () {
    if (data.vpwsagent.data.status === 'running') {
      return "green";
    } else if (data.vpwsagent.data.status === 'stopped') {
      return "grey";
    } else if (data.vpwsagent.data.status === 'unknown') {
      return "red";
    } else {
      return "orange";
    }
  };
  var app = new Vue(config);
  vproxyss.handleResponse(app, data, vproxyss.httpGet(app, '/api/vpws-agent/status'), (o) => {
    data.vpwsagent.data.status = o.status;
    vproxyss.handleResponse(app, data, vproxyss.httpGet(app, '/api/vpws-agent/config'), (o) => {
      data.vpwsagent.data.config = o.config;
      cb(app);
    });
  });
};
