/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';
(function() {
  var zeppelinWebApp = angular.module('zeppelinWebApp', [
    'ngCookies',
    'ngAnimate',
    'ngRoute',
    'ngSanitize',
    'angular-websocket',
    'ui.ace',
    'ui.bootstrap',
    'as.sortable',
    'ngTouch',
    'ngDragDrop',
    'angular.filter',
    'monospaced.elastic',
    'puElasticInput',
    'xeditable',
    'ngToast',
    'focus-if',
    'ngResource',
    'esri.map'
  ])
    .filter('breakFilter', function() {
      return function(text) {
        if (!!text) {
          return text.replace(/\n/g, '<br />');
        }
      };
    })
    .config(function($httpProvider, $routeProvider, ngToastProvider) {
      // withCredentials when running locally via grunt
      $httpProvider.defaults.withCredentials = true;

      $routeProvider
        .when('/', {
          templateUrl: 'app/home/home.html'
        })
        .when('/notebook/:noteId', {
          templateUrl: 'app/notebook/notebook.html',
          controller: 'NotebookCtrl'
        })
        .when('/notebook/:noteId/paragraph?=:paragraphId', {
          templateUrl: 'app/notebook/notebook.html',
          controller: 'NotebookCtrl'
        })
        .when('/notebook/:noteId/paragraph/:paragraphId?', {
          templateUrl: 'app/notebook/notebook.html',
          controller: 'NotebookCtrl'
        })
        .when('/jobmanager', {
          templateUrl: 'app/jobmanager/jobmanager.html',
          controller: 'JobmanagerCtrl'
        })
        .when('/interpreter', {
          templateUrl: 'app/interpreter/interpreter.html',
          controller: 'InterpreterCtrl'
        })
        .when('/credential', {
          templateUrl: 'app/credential/credential.html',
          controller: 'CredentialCtrl'
        })
        .when('/configuration', {
          templateUrl: 'app/configuration/configuration.html',
          controller: 'ConfigurationCtrl'
        })
        .when('/search/:searchTerm', {
          templateUrl: 'app/search/result-list.html',
          controller: 'SearchResultCtrl'
        })
        .otherwise({
          redirectTo: '/'
        });

      ngToastProvider.configure({
        dismissButton: true,
        dismissOnClick: false,
        timeout: 6000,
        horizontalPosition: 'center'
      });
    });

  /*function auth() {
   var $http = angular.injector(['ng']).get('$http');
   var baseUrlSrv = angular.injector(['zeppelinWebApp']).get('baseUrlSrv');
   // withCredentials when running locally via grunt
   $http.defaults.withCredentials = true;
   jQuery.ajaxSetup({
   dataType: 'json',
   xhrFields: {
   withCredentials: true
   },
   crossDomain: true
   });
   return $http.get(baseUrlSrv.getRestApiBase() + '/security/ticket').then(function(response) {
   zeppelinWebApp.run(function($rootScope) {
   $rootScope.ticket = angular.fromJson(response.data).body;
   });
   }, function(errorResponse) {
   // Handle error case
   });
   }*/

  function gerParam(name) {
    var search = location.search ? location.search.substring(1) : '';
    var parts = search.split('&');
    for (var i = parts.length - 1; i >= 0; i--) {
      if (parts[i].startsWith(name + '=')) {
        return parts[i].substring(name.length + 1);
      }
    }
    return '';
  }

  function bootstrapApplication() {
    zeppelinWebApp.run(function($rootScope, $location) {
      //从url上获取信息
      $rootScope.ticket = {
        ticket: gerParam('ticket'),//'a2529786-2b00-47a8-9503-8e4b786f991e',
        serverIndex: gerParam('serverIndex'),//1,
        projectId: gerParam('projectId'),//'project1'
        serverIP:gerParam('ip')
      };
      $rootScope.prjName = decodeURIComponent(gerParam('projectName'));

      $rootScope.$on('$routeChangeStart', function(event, next, current) {
        if (!$rootScope.ticket && next.$$route && !next.$$route.publicAccess) {
          $location.path('/');
        }
      });
    });
    angular.bootstrap(document, ['zeppelinWebApp']);
  }

  angular.element(document).ready(function() {
    bootstrapApplication();
  });
}());
