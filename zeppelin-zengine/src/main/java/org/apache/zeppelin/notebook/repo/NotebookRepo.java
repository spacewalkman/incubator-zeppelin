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

package org.apache.zeppelin.notebook.repo;

import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.repo.commit.SubmitLeftOver;
import org.apache.zeppelin.notebook.repo.commit.SubmitStrategy;
import org.apache.zeppelin.notebook.repo.commit.SubmitStrategyVolationException;

import java.io.IOException;
import java.util.List;

/**
 * Notebook repository (persistence layer) abstraction
 */
public interface NotebookRepo {
  /**
   * Lists notebook information about all notebooks in storage.
   *
   * @param principal contains user information.
   */
  @ZeppelinApi
  List<NoteInfo> list(String principal) throws IOException;

  /**
   * Get the notebook with the given id.
   *
   * @param noteId    is notebook id.
   * @param principal contains user information.
   */
  @ZeppelinApi
  Note get(String noteId, String principal) throws IOException;

  /**
   * Save given note in storage
   *
   * @param note      is the note itself.
   * @param principal contains user information.
   */
  @ZeppelinApi
  void save(Note note, String principal) throws IOException;

  /**
   * Remove note with given id.
   *
   * @param noteId    is the note id.
   * @param principal contains user information.
   */
  @ZeppelinApi
  void remove(String noteId, String principal) throws IOException;

  /**
   * Release any underlying resources
   */
  @ZeppelinApi
  void close();

  /**
   * Versioning API (optional, preferred to have).
   */

  /**
   * chekpoint (set revision) for notebook.
   *
   * @param note          note
   * @param checkpointMsg message description of the checkpoint
   * @param principal     current principal
   * @return Rev
   */
  @ZeppelinApi
  Revision checkpoint(Note note, String checkpointMsg,
                      String principal) throws IOException;

  /**
   * Get particular revision of the Notebook.
   *
   * @param noteId Id of the Notebook
   * @param revId  revision of the Notebook
   * @return a Notebook
   */

  @ZeppelinApi
  Note get(String noteId, String revId, String principal)
          throws IOException;

  /**
   * List of revisions of the given Notebook.
   *
   * @param noteId id of the Notebook
   * @return list of revisions
   */
  @ZeppelinApi
  List<Revision> revisionHistory(String noteId, String principal);

  /**
   * 提交revision到组委会
   *
   * @param revisionId 待提交的版本
   * @param noteId     当前提交的note id，在dbms中由于revisionId是主键，故没有使用noteid
   * @return 指定id的note的版本
   */
  SubmitLeftOver submit(String noteId, String revisionId) throws SubmitStrategyVolationException;

  /**
   * 获取当前参赛队对该题目已经提交的次数
   *
   * @param team      参赛队
   * @param projectId 题目id
   * @return 已经提交到组委会的次数, 如果为-1表示不支持查询已经提交的次数
   */
  int currentSubmitTimes(String team, String projectId);

  /**
   * Represents the 'Revision' a point in life of the notebook
   */
  class Revision {

    /**
     * 默认没有提交给组委会
     */
    public Revision(String revId, String noteId, String noteName, String message, long time,
                    String author, String committer, String team, String projectId) {
      this(revId, noteId, noteName, message, time, author, committer, team, projectId, false);
    }

    /**
     * @param isSubmitted 是否已经提交给组委会
     */
    public Revision(String revId, String noteId, String noteName, String message, long time,
                    String author, String committer, String team, String projectId,
                    boolean isSubmitted) {
      this.id = revId;
      this.noteId = noteId;
      this.message = message;
      this.noteName = noteName;
      this.time = time;
      this.author = author;
      this.committer = committer;
      this.team = team;
      this.projectId = projectId;
      this.isSubmitted = isSubmitted;
    }

    public String id;
    public String noteName;
    public String noteId;
    public String message;
    public long time;
    public String author;//revision的author
    public String committer;//该revision如果提交给组委会，该字段记录提交者
    public String team;//参赛队，即：group
    public String projectId;//参赛的题目
    public boolean isSubmitted = false;//是否已经提交

    public String sha1;//derived属性，通过content计算出来的
  }

}
