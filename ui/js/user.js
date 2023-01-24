vproxyss.currentPage = 'user';

vproxyss.subPages = [
  'user-password',
];

vproxyss.run = function (config, cb) {
  var data = config.data;
  data.user = {
    page: 'password',
    data: {
      changePass: {
        oldPass: '',
        newPass: '',
        repeat: '',
      }
    },
  };
  var lastPage = Cookies.get('vpss-last-user-page');
  if (!!lastPage) {
    data.user.page = lastPage;
  }
  var methods = config.methods;
  methods.setPage = function (page) {
    data.user.page = page;
    Cookies.set('vpss-last-user-page', page);
  };
  methods.changePassword = function () {
    if (data.user.data.changePass.newPass != data.user.data.changePass.repeat) {
      vproxyss.alert(this, data, 'user.repeat_password_not_the_same');
      return;
    }
    var promise = vproxyss.httpPost(this, '/api/password/change', {
      'old': vproxyss.formatString(data.user.data.changePass.oldPass),
      'new': vproxyss.formatString(data.user.data.changePass.newPass),
    });
    vproxyss.handleResponse(this, data, promise, () => {
      data.user.data.changePass.oldPass = '';
      data.user.data.changePass.newPass = '';
      data.user.data.changePass.repeat = '';
      vproxyss.alert(this, data, 'user.password_changed', () => {
        Cookies.remove('vpss-session');
        window.location.href = '/';
      });
    });
  };
  config.mounted = function () {
    $('#user-side_bar').sidebar({
      context: '#user-side_bar-context',
      dimPage: false,
      closable: false,
      transition: 'overlay',
      mobileTransition: 'overlay',
      onHide: function () {
        $('#user-side_bar-show').show();
      },
    });
    $('#user-side_bar-hide').click(function () {
      $('#user-side_bar').sidebar('hide');
    });
    $('#user-side_bar-show').click(function () {
      $('#user-side_bar').sidebar('show');
      $('#user-side_bar-show').hide();
    });
  };
  var app = new Vue(config);
  cb(app);
};
