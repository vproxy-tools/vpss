vproxyss.subPages = [];

vproxyss.run = function (config, cb) {
  var data = config.data;
  var methods = config.methods;
  data.login = {
    username: '',
    password: '',
  };
  methods.doLogin = function () {
    var promise = vproxyss.httpPost(this, "/api/login", {
      username: data.login.username,
      password: data.login.password,
    });
    vproxyss.handleResponse(this, data, promise, res => {
      var value = res.cookieValue;
      Cookies.set('vpss-session', value);
      window.location.href = "/overview.html"
    });
  };
  config.mounted = function () {
    $('#forget_password').click(function () {
      $('#forget_password_modal').modal('show');
    });
  };
  var app = new Vue(config);
  cb(app);
};

function loginInit() {
  var sessionId = Cookies.get('vpss-session');
  if (sessionId) { // do not reuse the old session cookie
    Cookies.remove('vpss-session');
  }
  // normal
  $(document).ready(function () {
    vproxyss._app();
  });
}
