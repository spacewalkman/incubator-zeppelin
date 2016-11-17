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

angular.module('zeppelinWebApp').service('baseUrlSrv', ['$location', '$rootScope', function($location, $rootScope) {

  this.getPort = function() {
    var port = Number(location.port);
    if (!port) {
      port = 80;
      //port = 8080;
      if (location.protocol === 'https:') {
        port = 443;
      }
    }
    //Exception for when running locally via grunt
    if (port === 3333 || port === 9000) {
      port = 8080;
    }
    return port;
  };

  this.getWebsocketUrl = function() {
    var wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    //return wsProtocol + '//' + this.getHost() + ':' + this.getPort() + skipTrailingSlash(location.pathname) + '/ws';
    return wsProtocol + '//' + this.getHost() + ':' + this.getPort() + '/ws';
  };

  this.getRestApiBase = function() {
    //return location.protocol + '//' + this.getHost() + ':' + this.getPort() + skipTrailingSlash(location.pathname) +
    return location.protocol + '//' + this.getHost() + ':' + this.getPort() + '/api';
  };

  // var skipTrailingSlash = function(path) {
  //   return path.replace(/\/$/, '');
  // };

  this.getHost = function() {
    return $rootScope.ticket.serverIP;
  };
}]);
