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
  .controller('SideBarCtrl', function($scope, $rootScope, $http, $routeParams,
                                  $location, notebookListDataFactory, baseUrlSrv, websocketMsgSrv, arrayOrderingSrv) {

    var vm = this;
    vm.arrayOrderingSrv = arrayOrderingSrv;
    vm.connected = websocketMsgSrv.isConnected();
    vm.isActive = isActive;
    vm.notes = notebookListDataFactory;
    vm.showNewArea = showNewArea;
    vm.toggleHistory = toggleHistory;
    vm.removeNote = removeNote;

    $scope.listActive = true;
    $scope.historyActive = false;
    $scope.adding = false;
    $scope.noteRevisions = [];

    initController();

    function getZeppelinVersion() {
      $http.get(baseUrlSrv.getRestApiBase() + '/version').success(
        function(data, status, headers, config) {
          $rootScope.zeppelinVersion = data.body;
        }).error(
        function(data, status, headers, config) {
          console.log('Error %o %o', status, data.message);
        });
    }

    function initController() {
      angular.element('.note-list,history-list').perfectScrollbar({suppressScrollX: true});

      angular.element(document).click(function() {
        $scope.adding = false;
      });

      getZeppelinVersion();
      loadNotes();
    }

    function isActive(noteId) {
      return ($routeParams.noteId === noteId);
    }

    function loadNotes() {
      websocketMsgSrv.getNotebookList();
    }
    function showNewArea() {
      $scope.adding = true;
    }
    function toggleHistory() {
      $scope.listActive = !$scope.listActive;
      $scope.historyActive = !$scope.historyActive;
    }
    function removeNote(noteId) {
      console.log('TODO:remove note!')
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
      console.log('We got the revisions %o', data);
      $scope.noteRevisions = data.revisionList;
    });
  });
