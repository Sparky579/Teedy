.directive('settings', function() {
  return {
    restrict: 'E',
    templateUrl: 'partial/settings/settings.html',
    replace: true,
    scope: {
      user: '=user'
    },
    controller: function($scope, Restangular, $translate, toaster, $rootScope, Upload) {
      // ... existing code ...
      
      // Load configurations
      Restangular.one('app').get().then(function (data) {
        // ... existing code ...
        $scope.app.llm_api_key = data.llm_api_key;
        $scope.app.llm_model_name = data.llm_model_name; 
        $scope.app.llm_api_base_url = data.llm_api_base_url;
        // ... existing code ...
      });
      
      // ... existing code ...
      
      // Save the configuration
      $scope.saveConfiguration = function () {
        // ... existing code ...
        var config = angular.copy($scope.app);
        // ... existing code ...
        Restangular.one('app').post('', config);
        // ... existing code ...
      };
      
      // ... existing code ...
    }
  }
}); 