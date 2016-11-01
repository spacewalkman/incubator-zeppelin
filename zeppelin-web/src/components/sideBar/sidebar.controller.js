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

angular.module('zeppelinWebApp')
  .controller('SideBarCtrl', function($scope, $rootScope, $http, $routeParams, $timeout, $location,
                                      notebookListDataFactory, baseUrlSrv, websocketMsgSrv, arrayOrderingSrv,
                                      noteRevisionJudgement, notePermission, noteNameService) {

    var vm = this;
    vm.arrayOrderingSrv = arrayOrderingSrv;
    vm.connected = websocketMsgSrv.isConnected();
    vm.isActive = isActive;
    vm.notes = notebookListDataFactory;
    vm.showNewArea = showNewArea;
    vm.toggleHistory = toggleHistory;
    vm.removeNote = removeNote;
    vm.createNote = createNote;
    vm.handleNameEnter = createNote;
    vm.handleNameChange = validateNoteName;
    vm.removeable = removeable;
    vm.readOnly = readOnly;

    $scope.listActive = true;
    $scope.historyActive = false;
    $scope.adding = false;
    $scope.noteRevisions = [];

    initController();

    function initController() {
      angular.element('.scroll-panel').perfectScrollbar();

      angular.element(document).click(function(e) {
        $scope.$apply(function() {
          $scope.adding = false;
        });
      });

      loadNotes();

      //如果是加载的历史版本，切换选中状态
      setPageviewState();
    }

    function setPageviewState() {
      if (noteRevisionJudgement.isHistory()) {
        $scope.listActive = false;
        $scope.historyActive = true;
      }
    }

    function isActive(noteId, versionId) {
      var currentVersion = $location.search().v;
      if (currentVersion || versionId) {
        return currentVersion === versionId && $routeParams.noteId === noteId;
      }
      return ($routeParams.noteId === noteId);
    }

    function loadNotes() {
      websocketMsgSrv.getNotebookList();
    }

    function showNewArea(event) {
      $scope.adding = true;
      focusNoteInput();
      event.stopPropagation();
    }

    function toggleHistory() {
      $scope.listActive = !$scope.listActive;
      $scope.historyActive = !$scope.historyActive;
    }

    function removeNote(noteId) {
      BootstrapDialog.confirm({
        closable: true,
        title: '',
        message: '是否确定删除算法?',
        callback: function(result) {
          if (result) {
            websocketMsgSrv.deleteNotebook(noteId);
            //如果删除的是当前选中项，则跳转到根目录
            if (isActive(noteId)) {
              $location.path('/');
            }
          }
        }
      });
    }

    function createNote(event) {
      if (validateNoteName()) {
        websocketMsgSrv.createNotebook($scope.notename);
        $scope.notename = '';
        $scope.adding = false;

      }
    }

    function validateNoteName() {
      var msg = noteNameService.validate($scope.notename);
      $scope.noteNameError = msg;
      if (msg) {
        event && event.stopPropagation();
        focusNoteInput();
        return false;
      } else {
        return true;
      }
    }

    function focusNoteInput() {
      $timeout(function() {
        angular.element('.input-new').focus();
      }, 300);
    }

    function removeable(note) {
      return notePermission.countPermission(note).remove;
    }

    function readOnly(note) {
      return !notePermission.countPermission(note).write;
    }

    /*
     ** $scope.$on functions below
     */

    $scope.$on('setNoteMenu', function(event, notes) {
      notebookListDataFactory.setNotes(notes);
    });

    $scope.$on('setConnectedStatus', function(event, param) {
      vm.connected = param;
    });
    //这里显示revision历史
    $scope.$on('listRevisionHistory', function(event, data) {
      $scope.noteRevisions = data.revisionList;
    });
  });
