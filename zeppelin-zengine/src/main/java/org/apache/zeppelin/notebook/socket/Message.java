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

package org.apache.zeppelin.notebook.socket;

import java.util.HashMap;
import java.util.Map;

/**
 * Zeppelin websocker massage template class.
 */
public class Message {
  /**
   * Representation of event type.
   */
  public static enum OP {
    GET_HOME_NOTE, // [c-s] load note for home screen

    GET_NOTE, // [c-s] client load note
              // @param id note id

    NOTE, // [s-c] note info
          // @param note serlialized Note object

    NOTE_TOPIC, // [s-c] note bussiness topic

    NOTE_TAGS, // [s-c] note tags

    PARAGRAPH, // [s-c] paragraph info
               // @param paragraph serialized paragraph object

    PROGRESS, // [s-c] progress update
              // @param id paragraph id
              // @param progress percentage progress

    NEW_NOTE, // [c-s] create new notebook
    SET_NOTE_TOPIC, // [c-s] set note topic
                    // @param id paragraph id
                    // @param note's topic
    SET_NOTE_TAGS, // [c-s] set note tags
                   // @param id note id
                   // @param note's tags array

    DEL_NOTE, // [c-s] delete notebook
              // @param id note id
    CLONE_NOTE, // [c-s] clone new notebook
                // @param id id of note to clone
                // @param name name fpor the cloned note
    IMPORT_NOTE,  // [c-s] import notebook
                  // @param object notebook
    NOTE_UPDATE,

    RUN_PARAGRAPH, // [c-s] run paragraph
                   // @param id paragraph id
                  // @param paragraph paragraph content.ie. script
                  // @param config paragraph config
                  // @param params paragraph params

    COMMIT_PARAGRAPH, // [c-s] commit paragraph
                      // @param id paragraph id
                      // @param title paragraph title
                      // @param paragraph paragraph content.ie. script
                      // @param config paragraph config
                      // @param params paragraph params

    CANCEL_PARAGRAPH, // [c-s] cancel paragraph run
                      // @param id paragraph id

    MOVE_PARAGRAPH, // [c-s] move paragraph order
                    // @param id paragraph id
                    // @param index index the paragraph want to go

    INSERT_PARAGRAPH, // [c-s] create new paragraph below current paragraph
                      // @param target index

    COMPLETION, // [c-s] ask completion candidates
                // @param id
                // @param buf current code
                // @param cursor cursor position in code

    COMPLETION_LIST, // [s-c] send back completion candidates list
                     // @param id
                     // @param completions list of string

    LIST_NOTES, // [c-s] ask list of note
    RELOAD_NOTES_FROM_REPO, // [c-s] reload notes from repo

    NOTES_INFO, // [s-c] list of note infos
                // @param notes serialized List<NoteInfo> object

    PARAGRAPH_REMOVE,
    PARAGRAPH_CLEAR_OUTPUT,
    PARAGRAPH_APPEND_OUTPUT,  // [s-c] append output
    PARAGRAPH_UPDATE_OUTPUT,  // [s-c] update (replace) output
    PING,
    AUTH_INFO,

    ANGULAR_OBJECT_UPDATE,  // [s-c] add/update angular object
    ANGULAR_OBJECT_REMOVE,  // [s-c] add angular object del

    ANGULAR_OBJECT_UPDATED,  // [c-s] angular object value updated,

    ANGULAR_OBJECT_CLIENT_BIND,  // [c-s] angular object updated from AngularJS z object

    ANGULAR_OBJECT_CLIENT_UNBIND,  // [c-s] angular object unbind from AngularJS z object

    LIST_CONFIGURATIONS, // [c-s] ask all key/value pairs of configurations
    CONFIGURATIONS_INFO, // [s-c] all key/value pairs of configurations
                  // @param settings serialized Map<String, String> object

    CHECKPOINT_NOTEBOOK,    // [c-s] checkpoint notebook to storage repository
                            // @param noteId
                            // @param checkpointName

    LIST_REVISION_HISTORY,  // [c-s] list revision history of the notebook
                            // @param noteId
    NOTE_REVISION,          // [c-s] get certain revision of note
                            // @param noteId
                            // @param revisionId
    NOTE_REVISION_SUBMIT,   // [c-s] 提交note一个指定的revision版本到组委会

    APP_APPEND_OUTPUT,      // [s-c] append output
    APP_UPDATE_OUTPUT,      // [s-c] update (replace) output
    APP_LOAD,               // [s-c] on app load
    APP_STATUS_CHANGE,      // [s-c] on app status change

    LIST_NOTEBOOK_JOBS,     // [c-s] get notebook job management infomations
    LIST_UPDATE_NOTEBOOK_JOBS, // [s-c] get job management informations
    UNSUBSCRIBE_UPDATE_NOTEBOOK_JOBS, // [c-s] unsubscribe job information for job management
    GET_INTERPRETER_BINDINGS, // [c-s] get interpreter bindings
                              // @param noteID
    SAVE_INTERPRETER_BINDINGS, // [c-s] save interpreter bindings
                               // @param noteID
                               // @param selectedSettingIds
    INTERPRETER_BINDINGS, // [s-c] interpreter bindings

    REVISION_SUBMIT//[s-c] submit note到组委会
  }

  public OP op;
  public Map<String, Object> data = new HashMap<String, Object>();
  public String ticket = "anonymous";
  public String principal = "anonymous";
  public String group="default_team";//TODO:qy,default team name
  public String roles = "";
  public String projectId="";//算法大赛的题目或者众包项目的的id

  public Message(OP op) {
    this.op = op;
  }

  public Message put(String k, Object v) {
    data.put(k, v);
    return this;
  }

  public Object get(String k) {
    return data.get(k);
  }

  public <T> T getType(String key) {
    return (T) data.get(key);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Message{");
    sb.append("data=").append(data);
    sb.append(", op=").append(op);
    sb.append('}');
    return sb.toString();
  }
}
