vproxyss.currentPage = 'command';

vproxyss.subPages = [
];

vproxyss.run = function (config, cb) {
  var data = config.data;
  data.command = {
    data: {
      script: '',
      timeout: '',
    },
    results: [],
  };
  var methods = config.methods;
  methods.execute = function () {
    var promise = vproxyss.httpPost(this, '/api/command/execute', {
      'script': vproxyss.formatString(data.command.data.script),
      'timeout': parseInt(data.command.data.timeout) * 1000 || undefined,
    });
    vproxyss.handleResponse(this, data, promise, res => {
      if (data.command.results.length >= 20) {
        data.command.results.shift();
      }
      data.command.results.unshift(res);
    });
  };
  var app = new Vue(config);
  cb(app);
};
