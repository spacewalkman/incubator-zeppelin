/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

angular.module('zeppelinWebApp').controller('NotebookCtrl', function($scope, $route, $routeParams, $location,
                                                                     $rootScope, $http, $timeout,
                                                                     ngToast, websocketMsgSrv, notePermission,
                                                                     baseUrlSrv, saveAsService,
                                                                     noteRevisionJudgement) {
  $scope.note = null;
  $scope.moment = moment;
  $scope.editorToggled = false;
  $scope.tableToggled = false;
  $scope.viewOnly = false;
  $scope.submitable = false;

  $scope.showSetting = false;
  $scope.looknfeelOption = [{k: 'default', v: '代码视图'}, {k: 'report', v: '报告视图'}];
  // $scope.cronOption = [
  //   {name: 'None', value: undefined},
  //   {name: '1m', value: '0 0/1 * * * ?'},
  //   {name: '5m', value: '0 0/5 * * * ?'},
  //   {name: '1h', value: '0 0 0/1 * * ?'},
  //   {name: '3h', value: '0 0 0/3 * * ?'},
  //   {name: '6h', value: '0 0 0/6 * * ?'},
  //   {name: '12h', value: '0 0 0/12 * * ?'},
  //   {name: '1d', value: '0 0 0 * * ?'}
  // ];

  $scope.interpreterBindings = ['r', 'python', 'spark', 'md', 'jdbc'];
  $scope.isNoteDirty = null;
  $scope.saveTimer = null;
  $scope.interpreterSaved = false;
  $scope.submitTimes = 0;
  $scope.submited = $location.search().v && $location.search().s && !Boolean($location.search().s);

  var connectedOnce = false;
  var saveSetting = function() {
    //写死的执行器ID
    var selectedInterpreterNames = [
      'spark',
      'md',
      'python',
      'jdbc'];

    websocketMsgSrv.saveInterpreterBindings($scope.note.id, selectedInterpreterNames);
    console.log('Interpreter bindings saved', selectedInterpreterNames);
  };

  // user auto complete related
  $scope.noteRevisions = [];

  $scope.$on('setConnectedStatus', function(event, param) {
    if (connectedOnce && param) {
      initNotebook();
    }
    connectedOnce = true;
  });

  /** Init the new controller */
  var initNotebook = function() {
    $scope.isRevision = noteRevisionJudgement.isHistory();
    //是否要进入历史视图
    if ($scope.isRevision) {
      websocketMsgSrv.getNoteByRevision($routeParams.noteId, $location.search().v);
    } else {
      websocketMsgSrv.getNotebook($routeParams.noteId);
    }
    websocketMsgSrv.listRevisionHistory($routeParams.noteId);
    var currentRoute = $route.current;
    if (currentRoute) {
      setTimeout(
        function() {
          var routeParams = currentRoute.params;
          var $id = angular.element('#' + routeParams.paragraph + '_container');

          if ($id.length > 0) {
            // adjust for navbar
            var top = $id.offset().top - 103;
            angular.element('html, body').scrollTo({top: top, left: 0});
          }

          // force notebook reload on user change
          $scope.$on('setNoteMenu', function(event, note) {
            initNotebook();
          });
        },
        1000);
    }
  };

  initNotebook();

  $scope.focusParagraphOnClick = function(clickEvent) {
    if (!$scope.note) {
      return;
    }
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      var paragraphId = $scope.note.paragraphs[i].id;
      if (jQuery.contains(angular.element('#' + paragraphId + '_container')[0], clickEvent.target)) {
        $scope.$broadcast('focusParagraph', paragraphId, 0, true);
        break;
      }
    }
  };

  // register mouseevent handler for focus paragraph
  document.addEventListener('click', $scope.focusParagraphOnClick);

  $scope.keyboardShortcut = function(keyEvent) {
    // handle keyevent
    if (!$scope.viewOnly) {
      $scope.$broadcast('keyEvent', keyEvent);
    }
  };

  // register mouseevent handler for focus paragraph
  document.addEventListener('keydown', $scope.keyboardShortcut);

  /** Remove the note and go back tot he main page */
  /** TODO(anthony): In the nearly future, go back to the main page and telle to the dude that the note have been remove */
  $scope.removeNote = function(noteId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: '是否确定删除算法?',
      callback: function(result) {
        if (result) {
          websocketMsgSrv.deleteNotebook(noteId);
          $location.path('/');
        }
      }
    });
  };

  //Export notebook
  $scope.exportNotebook = function() {
    var jsonContent = JSON.stringify($scope.note);
    saveAsService.saveAs(jsonContent, $scope.note.name, 'json');
  };

  //Clone note
  $scope.cloneNote = function(noteId) {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: '是否建立副本?',
      callback: function(result) {
        if (result) {
          websocketMsgSrv.cloneNotebook(noteId);
          $location.path('/');
        }
      }
    });
  };

  // checkpoint/commit notebook
  $scope.checkpointNotebook = function(event) {
    if (!validateCheckpointMessage()) {
      event.stopPropagation();
      return;
    }
    $scope.saveNote();//checkpoint之前先save一遍，使得mysql和Git repo同步
    websocketMsgSrv.checkpointNotebook($routeParams.noteId, $scope.note.checkpoint.message);

    var success = $scope.$on('listRevisionHistory', function() {
      $scope.note.checkpoint.message = '';
      success();

      ngToast.danger({
        content: '算法快照提交成功。',
        verticalPosition: 'bottom',
        timeout: '3000'
      });
    });
    $timeout(success, 1500);

    angular.element(document).click();
  };

  $scope.checkpointMsgChanged = function() {
    validateCheckpointMessage();
  };

  var validateCheckpointMessage = function() {
    if (!$scope.note.checkpoint || !$scope.note.checkpoint.message) {
      $scope.commitMsgError = '提交日志必须填写！';
      return false;
    } else {
      $scope.commitMsgError = null;
    }
    return true;
  };

  var findRevirsionId = function() {
    return $location.search().v || $scope.noteRevisions[0].id;
  };

  // 提交该版本到组委会
  $scope.submitNotebook = function(revisionId) {
    if ($scope.submitTimes <= 0) {
      return;
    }

    if (!$scope.noteRevisions || $scope.noteRevisions.length === 0) {
      ngToast.danger({
        content: '没有可用评测的算法快照，请先提交快照。',
        timeout: '3000'
      });
      return;
    }
    var revisionID = findRevirsionId();
    console.log('submit noteId:' + $routeParams.noteId + 'revisionId:' + revisionID);
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: '提交当前版本到组委会评测？还可以提交' + $scope.submitTimes + '次。',
      callback: function(result) {
        if (result) {
          websocketMsgSrv.submitNotebook($routeParams.noteId, revisionID);
        }
      }
    });
  };

  //查询该参赛队对赛题的当前的提交次数
  $scope.currentSubmitTimes = function(group, projectId) {
    websocketMsgSrv.currentSubmitTimes(group, projectId);
  };

  $scope.runNote = function() {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: '执行所有段落?',
      callback: function(result) {
        if (result) {
          _.forEach($scope.note.paragraphs, function(n, key) {
            angular.element('#' + n.id + '_paragraphColumn_main').scope().runParagraph(n.text);
          });
        }
      }
    });
  };

  $scope.saveNote = function() {
    if ($scope.note && $scope.note.paragraphs) {
      _.forEach($scope.note.paragraphs, function(n, key) {
        angular.element('#' + n.id + '_paragraphColumn_main').scope().saveParagraph();
      });
      $scope.isNoteDirty = null;
    }
  };

  $scope.clearAllParagraphOutput = function() {
    BootstrapDialog.confirm({
      closable: true,
      title: '',
      message: '确认清除算法输出?',
      callback: function(result) {
        if (result) {
          _.forEach($scope.note.paragraphs, function(n, key) {
            angular.element('#' + n.id + '_paragraphColumn_main').scope().clearParagraphOutput();
          });
        }
      }
    });
  };

  $scope.toggleAllEditor = function() {
    if ($scope.editorToggled) {
      $scope.$broadcast('openEditor');
    } else {
      $scope.$broadcast('closeEditor');
    }
    $scope.editorToggled = !$scope.editorToggled;
  };

  $scope.showAllEditor = function() {
    $scope.$broadcast('openEditor');
  };

  $scope.hideAllEditor = function() {
    $scope.$broadcast('closeEditor');
  };

  $scope.toggleAllTable = function() {
    if ($scope.tableToggled) {
      $scope.$broadcast('openTable');
    } else {
      $scope.$broadcast('closeTable');
    }
    $scope.tableToggled = !$scope.tableToggled;
  };

  $scope.showAllTable = function() {
    $scope.$broadcast('openTable');
  };

  $scope.hideAllTable = function() {
    $scope.$broadcast('closeTable');
  };

  $scope.isNoteRunning = function() {
    var running = false;
    if (!$scope.note) {
      return false;
    }
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].status === 'PENDING' || $scope.note.paragraphs[i].status === 'RUNNING') {
        running = true;
        break;
      }
    }
    return running;
  };

  $scope.killSaveTimer = function() {
    if ($scope.saveTimer) {
      $timeout.cancel($scope.saveTimer);
      $scope.saveTimer = null;
    }
  };

  $scope.startSaveTimer = function() {
    $scope.killSaveTimer();
    $scope.isNoteDirty = true;
    //console.log('startSaveTimer called ' + $scope.note.id);
    $scope.saveTimer = $timeout(function() {
      $scope.saveNote();
    }, 10000);
  };

  angular.element(window).on('beforeunload', function(e) {
    $scope.killSaveTimer();
    $scope.saveNote();
  });

  $scope.setLookAndFeel = function(looknfeel) {
    $scope.note.config.looknfeel = looknfeel;
    $scope.setConfig();
  };
  $scope.getLookAndFeel = function() {
    var result = '';
    _.forEach($scope.looknfeelOption, function(feel, index) {
      if ($scope.note && feel.k === $scope.note.config.looknfeel) {
        result = feel.v;
      }
    });
    return result;
  };
  /** Set cron expression for this note **/
  $scope.setCronScheduler = function(cronExpr) {
    $scope.note.config.cron = cronExpr;
    $scope.setConfig();
  };

  /** Set the username of the user to be used to execute all notes in notebook **/
  $scope.setCronExecutingUser = function(cronExecutingUser) {
    $scope.note.config.cronExecutingUser = cronExecutingUser;
    $scope.setConfig();
  };

  /** Set release resource for this note **/
  $scope.setReleaseResource = function(value) {
    $scope.note.config.releaseresource = value;
    $scope.setConfig();
  };

  /** Update note config **/
  $scope.setConfig = function(config) {
    if (config) {
      $scope.note.config = config;
    }
    websocketMsgSrv.updateNotebook($scope.note.id, $scope.note.name, $scope.note.config);
  };

  /** Update the note name */
  $scope.sendNewName = function() {
    if ($scope.note.name) {
      websocketMsgSrv.updateNotebook($scope.note.id, $scope.note.name, $scope.note.config);
    }
  };

  var initializeLookAndFeel = function() {
    if (!$scope.note.config.looknfeel) {
      $scope.note.config.looknfeel = 'default';
    } else {
      $scope.viewOnly = $scope.note.config.looknfeel === 'report' ? true : false;
    }
    $scope.note.paragraphs[0].focus = true;
    $rootScope.$broadcast('setLookAndFeel', $scope.note.config.looknfeel);
  };

  var cleanParagraphExcept = function(paragraphId, note) {
    var noteCopy = {};
    noteCopy.id = note.id;
    noteCopy.name = note.name;
    noteCopy.config = note.config;
    noteCopy.info = note.info;
    noteCopy.paragraphs = [];
    for (var i = 0; i < note.paragraphs.length; i++) {
      if (note.paragraphs[i].id === paragraphId) {
        noteCopy.paragraphs[0] = note.paragraphs[i];
        if (!noteCopy.paragraphs[0].config) {
          noteCopy.paragraphs[0].config = {};
        }
        noteCopy.paragraphs[0].config.editorHide = true;
        noteCopy.paragraphs[0].config.tableHide = false;
        break;
      }
    }
    return noteCopy;
  };

  var updateNote = function(note) {
    /** update Note name */
    if (note.name !== $scope.note.name) {
      console.log('change note name: %o to %o', $scope.note.name, note.name);
      $scope.note.name = note.name;
    }

    $scope.note.config = note.config;
    $scope.note.info = note.info;

    var newParagraphIds = note.paragraphs.map(function(x) {
      return x.id;
    });
    var oldParagraphIds = $scope.note.paragraphs.map(function(x) {
      return x.id;
    });

    var numNewParagraphs = newParagraphIds.length;
    var numOldParagraphs = oldParagraphIds.length;

    var paragraphToBeFocused;
    var focusedParagraph;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      var paragraphId = $scope.note.paragraphs[i].id;
      if (angular.element('#' + paragraphId + '_paragraphColumn_main').scope().paragraphFocused) {
        focusedParagraph = paragraphId;
        break;
      }
    }

    /** add a new paragraph */
    if (numNewParagraphs > numOldParagraphs) {
      for (var index in newParagraphIds) {
        if (oldParagraphIds[index] !== newParagraphIds[index]) {
          $scope.note.paragraphs.splice(index, 0, note.paragraphs[index]);
          paragraphToBeFocused = note.paragraphs[index].id;
          break;
        }
        $scope.$broadcast('updateParagraph', {
          note: $scope.note, // pass the note object to paragraph scope
          paragraph: note.paragraphs[index]
        });
      }
    }

    /** update or move paragraph */
    if (numNewParagraphs === numOldParagraphs) {
      for (var idx in newParagraphIds) {
        var newEntry = note.paragraphs[idx];
        if (oldParagraphIds[idx] === newParagraphIds[idx]) {
          $scope.$broadcast('updateParagraph', {
            note: $scope.note, // pass the note object to paragraph scope
            paragraph: newEntry
          });
        } else {
          // move paragraph
          var oldIdx = oldParagraphIds.indexOf(newParagraphIds[idx]);
          $scope.note.paragraphs.splice(oldIdx, 1);
          $scope.note.paragraphs.splice(idx, 0, newEntry);
          // rebuild id list since paragraph has moved.
          oldParagraphIds = $scope.note.paragraphs.map(function(x) {
            return x.id;
          });
        }

        if (focusedParagraph === newParagraphIds[idx]) {
          paragraphToBeFocused = focusedParagraph;
        }
      }
    }

    /** remove paragraph */
    if (numNewParagraphs < numOldParagraphs) {
      for (var oldidx in oldParagraphIds) {
        if (oldParagraphIds[oldidx] !== newParagraphIds[oldidx]) {
          $scope.note.paragraphs.splice(oldidx, 1);
          break;
        }
      }
    }

    // restore focus of paragraph
    for (var f = 0; f < $scope.note.paragraphs.length; f++) {
      if (paragraphToBeFocused === $scope.note.paragraphs[f].id) {
        $scope.note.paragraphs[f].focus = true;
      }
    }

  };

  // var getInterpreterBindings = function () {
  //   websocketMsgSrv.getInterpreterBindings($scope.note.id);
  // };

  /*  $scope.$on('interpreterBindings', function (event, data) {
   $scope.interpreterBindings = data.interpreterBindings;
   $scope.interpreterBindingsOrig = angular.copy($scope.interpreterBindings); // to check dirty

   //set default interpreter for paragraphs

   });*/

  $scope.interpreterSelectionListeners = {
    accept: function(sourceItemHandleScope, destSortableScope) {
      return true;
    },
    itemMoved: function(event) {
    },
    orderChanged: function(event) {
    }
  };

  $scope.closeSetting = function() {
    if (isSettingDirty()) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Interpreter setting changes will be discarded.',
        callback: function(result) {
          if (result) {
            $scope.$apply(function() {
              $scope.showSetting = false;
            });
          }
        }
      });
    } else {
      $scope.showSetting = false;
    }
  };

  $scope.toggleSetting = function() {
    if ($scope.showSetting) {
      $scope.closeSetting();
    } else {
      $scope.openSetting();
      $scope.closePermissions();
    }
  };

  var getPermissions = function(callback) {
    $http.get(baseUrlSrv.getRestApiBase() + '/notebook/' + $scope.note.id + '/permissions')
      .success(function(data, status, headers, config) {
        $scope.permissions = data.body;
        $scope.permissionsOrig = angular.copy($scope.permissions); // to check dirty

        var selectJson = {
          tokenSeparators: [',', ' '],
          ajax: {
            url: function(params) {
              if (!params.term) {
                return false;
              }
              return baseUrlSrv.getRestApiBase() + '/security/userlist/' + params.term;
            },
            delay: 250,
            processResults: function(data, params) {
              var results = [];

              if (data.body.users.length !== 0) {
                var users = [];
                for (var len = 0; len < data.body.users.length; len++) {
                  users.push({
                    'id': data.body.users[len],
                    'text': data.body.users[len]
                  });
                }
                results.push({
                  'text': 'Users :',
                  'children': users
                });
              }
              if (data.body.roles.length !== 0) {
                var roles = [];
                for (var len = 0; len < data.body.roles.length; len++) {
                  roles.push({
                    'id': data.body.roles[len],
                    'text': data.body.roles[len]
                  });
                }
                results.push({
                  'text': 'Roles :',
                  'children': roles
                });
              }
              return {
                results: results,
                pagination: {
                  more: false
                }
              };
            },
            cache: false
          },
          width: ' ',
          tags: true,
          minimumInputLength: 3
        };

        angular.element('#selectOwners').select2(selectJson);
        angular.element('#selectReaders').select2(selectJson);
        angular.element('#selectWriters').select2(selectJson);
        if (callback) {
          callback();
        }
      }).error(function(data, status, headers, config) {
      if (status !== 0) {
        console.log('Error %o %o', status, data.message);
      }
    });
  };

  $scope.openPermissions = function() {
    $scope.showPermissions = true;
    getPermissions();
  };

  $scope.closePermissions = function() {
    if (isPermissionsDirty()) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: 'Changes will be discarded.',
        callback: function(result) {
          if (result) {
            $scope.$apply(function() {
              $scope.showPermissions = false;
            });
          }
        }
      });
    } else {
      $scope.showPermissions = false;
    }
  };

  function convertPermissionsToArray() {
    $scope.permissions.owners = angular.element('#selectOwners').val();
    $scope.permissions.readers = angular.element('#selectReaders').val();
    $scope.permissions.writers = angular.element('#selectWriters').val();
  }

  $scope.savePermissions = function() {
    convertPermissionsToArray();
    $http.put(baseUrlSrv.getRestApiBase() + '/notebook/' + $scope.note.id + '/permissions',
      $scope.permissions, {withCredentials: true}).success(function(data, status, headers, config) {
      getPermissions(function() {
        console.log('Note permissions %o saved', $scope.permissions);
        BootstrapDialog.alert({
          closable: true,
          title: 'Permissions Saved Successfully!!!',
          message: 'Owners : ' + $scope.permissions.owners + '\n\n' + 'Readers : ' +
          $scope.permissions.readers + '\n\n' + 'Writers  : ' + $scope.permissions.writers
        });
        $scope.showPermissions = false;
      });
    }).error(function(data, status, headers, config) {
      console.log('Error %o %o', status, data.message);
      BootstrapDialog.show({
        closable: false,
        closeByBackdrop: false,
        closeByKeyboard: false,
        title: 'Insufficient privileges',
        message: data.message,
        buttons: [
          {
            label: 'Login',
            action: function(dialog) {
              dialog.close();
              angular.element('#loginModal').modal({
                show: 'true'
              });
            }
          },
          {
            label: 'Cancel',
            action: function(dialog) {
              dialog.close();
              $location.path('/');
            }
          }
        ]
      });
    });
  };

  $scope.togglePermissions = function() {
    if ($scope.showPermissions) {
      $scope.closePermissions();
      angular.element('#selectOwners').select2({});
      angular.element('#selectReaders').select2({});
      angular.element('#selectWriters').select2({});
    } else {
      $scope.openPermissions();
      $scope.closeSetting();
    }
  };

  var isSettingDirty = function() {
    if (angular.equals($scope.interpreterBindings, $scope.interpreterBindingsOrig)) {
      return false;
    } else {
      return true;
    }
  };

  var isPermissionsDirty = function() {
    if (angular.equals($scope.permissions, $scope.permissionsOrig)) {
      return false;
    } else {
      return true;
    }
  };

  angular.element(document).click(function() {
    angular.element('.ace_autocomplete').hide();
  });

  /*
   ** $scope.$on functions below
   */

  $scope.$on('setConnectedStatus', function(event, param) {
    if (connectedOnce && param) {
      initNotebook();
    }
    connectedOnce = true;
  });

  $scope.$on('moveParagraphUp', function(event, paragraphId) {
    var newIndex = -1;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        newIndex = i - 1;
        break;
      }
    }
    if (newIndex < 0 || newIndex >= $scope.note.paragraphs.length) {
      return;
    }
    // save dirtyText of moving paragraphs.
    var prevParagraphId = $scope.note.paragraphs[newIndex].id;
    angular.element('#' + paragraphId + '_paragraphColumn_main').scope().saveParagraph();
    angular.element('#' + prevParagraphId + '_paragraphColumn_main').scope().saveParagraph();
    websocketMsgSrv.moveParagraph(paragraphId, newIndex);
  });

  $scope.$on('moveParagraphDown', function(event, paragraphId) {
    var newIndex = -1;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        newIndex = i + 1;
        break;
      }
    }

    if (newIndex < 0 || newIndex >= $scope.note.paragraphs.length) {
      return;
    }
    // save dirtyText of moving paragraphs.
    var nextParagraphId = $scope.note.paragraphs[newIndex].id;
    angular.element('#' + paragraphId + '_paragraphColumn_main').scope().saveParagraph();
    angular.element('#' + nextParagraphId + '_paragraphColumn_main').scope().saveParagraph();
    websocketMsgSrv.moveParagraph(paragraphId, newIndex);
  });

  $scope.$on('moveFocusToPreviousParagraph', function(event, currentParagraphId) {
    var focus = false;
    for (var i = $scope.note.paragraphs.length - 1; i >= 0; i--) {
      if (focus === false) {
        if ($scope.note.paragraphs[i].id === currentParagraphId) {
          focus = true;
          continue;
        }
      } else {
        $scope.$broadcast('focusParagraph', $scope.note.paragraphs[i].id, -1);
        break;
      }
    }
  });

  $scope.$on('moveFocusToNextParagraph', function(event, currentParagraphId) {
    var focus = false;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if (focus === false) {
        if ($scope.note.paragraphs[i].id === currentParagraphId) {
          focus = true;
          continue;
        }
      } else {
        $scope.$broadcast('focusParagraph', $scope.note.paragraphs[i].id, 0);
        break;
      }
    }
  });

  $scope.$on('insertParagraph', function(event, paragraphId, position) {
    var newIndex = -1;
    for (var i = 0; i < $scope.note.paragraphs.length; i++) {
      if ($scope.note.paragraphs[i].id === paragraphId) {
        //determine position of where to add new paragraph; default is below
        if (position === 'above') {
          newIndex = i;
        } else {
          newIndex = i + 1;
        }
        break;
      }
    }

    if (newIndex < 0 || newIndex > $scope.note.paragraphs.length) {
      return;
    }
    websocketMsgSrv.insertParagraph(newIndex, $scope.interpreterBindings[0]);
  });

  $scope.$on('setNoteContent', function(event, note) {
    if (note === undefined) {
      $location.path('/');
    }

    $scope.paragraphUrl = $routeParams.paragraphId;
    $scope.asIframe = $routeParams.asIframe;
    if ($scope.paragraphUrl) {
      note = cleanParagraphExcept($scope.paragraphUrl, note);
      $rootScope.$broadcast('setIframe', $scope.asIframe);
    }

    if ($scope.note === null) {
      $scope.note = note;
    } else {
      updateNote(note);
    }
    initializeLookAndFeel();
    //open interpreter binding setting when there're none selected
    // getInterpreterBindings();
    saveSetting();

    //计算note权限
    $scope.note.permission = notePermission.countPermission($scope.note);

    //type = 'template',必须viewOnly
    $scope.viewOnly = $scope.viewOnly || notePermission.isTemplate($scope.note.type);

    console.log('right note', $scope.note);

    //查询可提交次数
    if (notePermission.isRevision($scope.note.type)) {
      websocketMsgSrv.currentSubmitTimes($rootScope.ticket.projectId);
    }
  });
  //这里显示revision历史
  $scope.$on('listRevisionHistory', function(event, data) {
    $scope.noteRevisions = data.revisionList;
  });

  // receive certain revision of note
  $scope.$on('noteRevision', function(event, revision) {
    $rootScope.$broadcast('setNoteContent', revision.data);
  });
  //更新可提交次数
  $scope.$on('flushSubmitTimes', function(event, times) {
    $scope.submitTimes = times.leftTimes;
  });
  $scope.$on('$destroy', function() {
    angular.element(window).off('beforeunload');
    $scope.killSaveTimer();
    $scope.saveNote();

    document.removeEventListener('click', $scope.focusParagraphOnClick);
    document.removeEventListener('keydown', $scope.keyboardShortcut);
  });
  $scope.$on('revisionSubmit', function(data) {
    if (!data.errorMessage) {
      $scope.submitTimes = parseInt(data.leftTimes);
    }

    ngToast.danger({
      content: data.errorMessage || data.message,
      timeout: '3000'
    });
  });
});
