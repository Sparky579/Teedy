'use strict';

/**
 * User registration controller.
 */
angular.module('docs').controller('Register', function($scope, $state, Restangular, $translate) {
  $scope.register = {
    user: {
      username: '',
      email: '',
      password: '',
      passwordconfirm: ''
    },
    loading: false,
    error: false,
    success: false,
    errorMessage: '',
    
    /**
     * Submit the registration form.
     */
    submit: function() {
      $scope.register.loading = true;
      $scope.register.error = false;
      $scope.register.errorMessage = '';
      
      // 使用表单数据格式而不是JSON，将参数直接传递
      var formData = {
        username: $scope.register.user.username,
        email: $scope.register.user.email,
        password: $scope.register.user.password
      };
      
      Restangular.one('user/registration').customPUT(
        formData, // 表单数据
        '', // 不需要附加路径
        {}, // 不需要查询参数
        {'Content-Type': 'application/x-www-form-urlencoded'} // 指定正确的内容类型
      ).then(function() {
        $scope.register.loading = false;
        $scope.register.success = true;
      }, function(e) {
        $scope.register.loading = false;
        $scope.register.error = true;
        if (e.data.type === 'AlreadyExistingUsername') {
          $scope.register.errorMessage = $translate.instant('register.error_username_taken');
        } else {
          $scope.register.errorMessage = $translate.instant('register.error_server');
        }
      });
    }
  };
}); 