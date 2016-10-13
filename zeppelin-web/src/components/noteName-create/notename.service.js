/**
 * Created by tanxiangyuan on 2016/10/12.
 */
/**
 * 计算note的页面权限
 * Created by tanxiangyuan on 2016/10/12.
 */
'use strict';
angular.module('zeppelinWebApp').service('noteNameService', [function() {

  this.validate = function(name) {
    if (!name) {
      return '请输入算法名称';
    }
    if (/[\/|\\]/.test(name)) {
      return '算法名称不能包含\\、/。';
    }

    return '';
  };
}]);
