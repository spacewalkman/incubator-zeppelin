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

angular.module('zeppelinWebApp').service('arrayOrderingSrv', ['notePermission', function(notePermission) {

  var arrayOrderingSrv = this;

  this.notebookListOrdering = function(note) {
    return arrayOrderingSrv.getSorting(note);
  };

  this.getNoteName = function(note) {
    if (note.name === undefined || note.name.trim() === '') {
      return 'Note ' + note.id;
    } else {
      return note.name;
    }
  };

  //将template置顶，剩下的note按照lastModifiedTime降序
  this.getSorting = function(note) {
    if (!note.lastModifiedTime) {
      return Number.NEGATIVE_INFINITY;
    }

    if (notePermission.isTemplate(note.type)) {
      return Number.POSITIVE_INFINITY;
    }

    return note.lastModifiedTime;
  };

}]);
