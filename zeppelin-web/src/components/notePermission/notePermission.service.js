/**
 * 计算note的页面权限
 * Created by tanxiangyuan on 2016/10/12.
 */
'use strict';
angular.module('zeppelinWebApp').service('notePermission', [function() {

  this.countPermission = function(note) {

    return {
      remove: permissionJudge(note.permissionsMap, 'canDelete'),
      read: true,//后台返回的都是可以看的
      write: isNormal(note.type) && permissionJudge(note.permissionsMap, 'canWrite'),
      exec: !isRevision(note.type) && permissionJudge(note.permissionsMap, 'canExecute'),
      commit: isNormal(note.type) && permissionJudge(note.permissionsMap, 'canCommit'),
      submit: isRevision(note.type) && permissionJudge(note.permissionsMap, 'canSubmit')
    };
  };
  this.isTemplate = function(noteType) {
    return noteType === 'template';
  };

  function permissionJudge(permissionMap, key) {
    return !!permissionMap && isRealTrue(permissionMap[key]);
  }

  function isRealTrue(value) {
    return !!value && Boolean(value);
  }

  function isRevision(noteType) {
    return noteType === 'revision';
  }

  function isNormal(noteType) {
    return noteType === 'normal';
  }
}]);
