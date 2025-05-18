'use strict';

/**
 * User registration management controller.
 */
angular.module('docs')
  .controller('SettingsUserRegistration', ['$scope', 'Restangular', '$translate', '$uibModal',
  function($scope, Restangular, $translate, $uibModal) {
    // Current date format
    $scope.dateFormat = $translate.instant('format.date');

    /**
     * Load registrations from server.
     */
    $scope.loadRegistrations = function() {
      $scope.loading = true;
      Restangular.one('user/registration').get().then(function(data) {
        $scope.userregistrations = data.registrations;
        $scope.loading = false;
      });
    };
    
    $scope.loadRegistrations();
    
    /**
     * Approve user registration.
     */
    $scope.approve = function(registration) {
      var modalInstance = $uibModal.open({
        templateUrl: 'approveModalContent.html',
        controller: 'ApproveModalCtrl',
        resolve: {
          registration: function() {
            return registration;
          }
        }
      });
      
      modalInstance.result.then(function(approveData) {
        Restangular.one('user/registration', registration.id)
          .one('approve')
          .post('', { storage_quota: approveData.storage_quota * 1000000 })
          .then(function() {
            // Re-load the registrations
            $scope.loadRegistrations();
          });
      });
    };
    
    /**
     * Reject user registration.
     */
    $scope.reject = function(registration) {
      var modalInstance = $uibModal.open({
        templateUrl: 'rejectModalContent.html',
        controller: 'RejectModalCtrl',
        resolve: {
          registration: function() {
            return registration;
          }
        }
      });
      
      modalInstance.result.then(function() {
        Restangular.one('user/registration', registration.id)
          .one('reject')
          .post('')
          .then(function() {
            // Re-load the registrations
            $scope.loadRegistrations();
          });
      });
    };
  }])
  
  .controller('ApproveModalCtrl', ['$scope', '$uibModalInstance', 'registration', 
  function($scope, $uibModalInstance, registration) {
    $scope.registration = registration;
    $scope.approveData = {
      storage_quota: 1000  // Default 1000 MB
    };
    
    $scope.ok = function() {
      $uibModalInstance.close($scope.approveData);
    };
    
    $scope.cancel = function() {
      $uibModalInstance.dismiss('cancel');
    };
  }])
  
  .controller('RejectModalCtrl', ['$scope', '$uibModalInstance', 'registration',
  function($scope, $uibModalInstance, registration) {
    $scope.registration = registration;
    
    $scope.ok = function() {
      $uibModalInstance.close();
    };
    
    $scope.cancel = function() {
      $uibModalInstance.dismiss('cancel');
    };
  }]); 