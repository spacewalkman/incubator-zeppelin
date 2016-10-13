/**
 * Created by tanxiangyuan on 2016/10/12.
 */
'use strict';
angular.module('zeppelinWebApp').service('noteRevisionJudgement', ['$location',function($location) {

  this.isHistory = function() {
    return !!$location.search().v;
  };

}]);
