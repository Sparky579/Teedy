'use strict';

/**
 * Tag controller.
 */
App.controller('Tag', function($scope, $dialog, $state, Tag, Restangular) {
  $scope.tag = { name: '', color: '#3a87ad' };
  
  // Retrieve tags
  Tag.tags().then(function(data) {
    $scope.tags = data.tags;
  });
  
  // Retrieve tag stats
  Restangular.one('tag/stats').get().then(function(data) {
    $scope.stats = data.stats;
  })
  
  /**
   * Returns total number of document from tag stats.
   */
  $scope.getStatCount = function() {
    return _.reduce($scope.stats, function(memo, stat) {
      return memo + stat.count
    }, 0);
  };
  
  /**
   * Validate a tag name for duplicate.
   */
  $scope.validateDuplicate = function(name) {
    return !_.find($scope.tags, function(tag) {
      return tag.name == name;
    });
  };
  
  /**
   * Add a tag.
   */
  $scope.addTag = function() {
    // TODO Check if the tag don't already exists
    Restangular.one('tag').put($scope.tag).then(function(data) {
      $scope.tags.push({ id: data.id, name: $scope.tag.name, color: $scope.tag.color });
      $scope.tag = { name: '', color: '#3a87ad' };
    });
  };
  
  /**
   * Delete a tag.
   */
  $scope.deleteTag = function(tag) {
    var title = 'Delete tag';
    var msg = 'Do you really want to delete this tag?';
    var btns = [
      {result: 'cancel', label: 'Cancel'},
      {result: 'ok', label: 'OK', cssClass: 'btn-primary'}
    ];

    $dialog.messageBox(title, msg, btns)
    .open()
    .then(function(result) {
      if (result == 'ok') {
        Restangular.one('tag', tag.id).remove().then(function() {
          $scope.tags = _.reject($scope.tags, function(t) {
            return tag.id == t.id;
          });
        });
      }
    });
  };
  
  /**
   * Update a tag.
   */
  $scope.updateTag = function(tag) {
    // Update the server
    return Restangular.one('tag', tag.id).post('', tag).then(function () {
      // Update the stat object
      var stat = _.find($scope.stats, function (t) {
        return tag.id == t.id;
      });

      if (stat) {
        _.extend(stat, tag);
      }
    });
  };
});