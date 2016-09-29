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

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.repo.commit.SubmitLeftOver;
import org.apache.zeppelin.notebook.repo.commit.SubmitStrategyVolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notebook repository sync with remote storage
 */
public class NotebookRepoSync implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(NotebookRepoSync.class);
  private static final int maxRepoNum = 2;
  private static final String pushKey = "pushNoteIDs";
  private static final String pullKey = "pullNoteIDs";
  private static final String delDstKey = "delDstNoteIDs";

  private static ZeppelinConfiguration config;
  private static final String defaultStorage = "org.apache.zeppelin.notebook.repo.VFSNotebookRepo";

  private List<NotebookRepo> repos = new ArrayList<NotebookRepo>();
  private final boolean oneWaySync;

  @SuppressWarnings("static-access")
  public NotebookRepoSync(ZeppelinConfiguration conf) {
    config = conf;
    oneWaySync = conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_ONE_WAY_SYNC);
    String allStorageClassNames = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_STORAGE).trim();
    if (allStorageClassNames.isEmpty()) {
      allStorageClassNames = defaultStorage;
      LOG.warn("Empty ZEPPELIN_NOTEBOOK_STORAGE conf parameter, using default {}", defaultStorage);
    }
    String[] storageClassNames = allStorageClassNames.split(",");
    if (storageClassNames.length > getMaxRepoNum()) {
      LOG.warn("Unsupported number {} of storage classes in ZEPPELIN_NOTEBOOK_STORAGE : {}\n" +
              "first {} will be used", storageClassNames.length, allStorageClassNames, getMaxRepoNum());
    }

    Class<?> lastNotebookStorageClass = null;
    for (int i = 0; i < Math.min(storageClassNames.length, getMaxRepoNum()); i++) {
      @SuppressWarnings("static-access")
      Class<?> notebookStorageClass;
      try {
        notebookStorageClass = getClass().forName(storageClassNames[i].trim());
        //避免2个repo相同，或者是父子关系，避免save方法调用2次
        if (lastNotebookStorageClass != null) {
          if (lastNotebookStorageClass.isAssignableFrom(notebookStorageClass) || notebookStorageClass.isAssignableFrom(lastNotebookStorageClass)) {//如果class相同，或者一个是另一个的subclass
            continue;
          }
        } else {
          lastNotebookStorageClass = notebookStorageClass;
        }

        Constructor<?> constructor = notebookStorageClass.getConstructor(
                ZeppelinConfiguration.class);
        repos.add((NotebookRepo) constructor.newInstance(conf));
      } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
              InstantiationException | IllegalAccessException | IllegalArgumentException |
              InvocationTargetException e) {
        LOG.warn("Failed to initialize {} notebook storage class", storageClassNames[i], e);
      }
    }
    // couldn't initialize any storage, use default
    if (getRepoCount() == 0) {
      LOG.info("No storages could be initialized, using default {} storage", defaultStorage);
      initializeDefaultStorage(conf);
    }
    if (getRepoCount() > 1) {
      try {
        sync(0, 1);
      } catch (IOException e) {
        LOG.warn("Failed to sync with secondary storage on start {}", e);
      }
    }
  }

  @SuppressWarnings("static-access")
  private void initializeDefaultStorage(ZeppelinConfiguration conf) {
    Class<?> notebookStorageClass;
    try {
      notebookStorageClass = getClass().forName(defaultStorage);
      Constructor<?> constructor = notebookStorageClass.getConstructor(
              ZeppelinConfiguration.class);
      repos.add((NotebookRepo) constructor.newInstance(conf));
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException |
            InstantiationException | IllegalAccessException | IllegalArgumentException |
            InvocationTargetException e) {
      LOG.warn("Failed to initialize {} notebook storage class {}", defaultStorage, e);
    }
  }

  /**
   * Lists Notebooks from the first repository
   */
  @Override
  public List<NoteInfo> list(String principal) throws IOException {
    return getRepo(0).list(principal);
  }

  /* list from specific repo (for tests) */
  List<NoteInfo> list(int repoIndex, String principal) throws IOException {
    return getRepo(repoIndex).list(principal);
  }

  /**
   * Returns from Notebook from the first repository
   */
  @Override
  public Note get(String noteId, String principal) throws IOException {
    return getRepo(0).get(noteId, principal);
  }

  /* get note from specific repo (for tests) */
  Note get(int repoIndex, String noteId, String principal) throws IOException {
    return getRepo(repoIndex).get(noteId, principal);
  }

  /**
   * Saves to all repositories
   */
  @Override
  public void save(Note note, String principal) throws IOException {
    getRepo(0).save(note, principal);
    if (getRepoCount() > 1) {
      try {
        getRepo(1).save(note, principal);
      } catch (IOException e) {
        LOG.info(e.getMessage() + ": Failed to write to secondary storage");
      }
    }
  }

  /* save note to specific repo (for tests) */
  void save(int repoIndex, Note note, String principal) throws IOException {
    getRepo(repoIndex).save(note, principal);
  }

  @Override
  public void remove(String noteId, String principal) throws IOException {
    for (NotebookRepo repo : repos) {
      repo.remove(noteId, principal);
    }
    /* TODO(khalid): handle case when removing from secondary storage fails */
  }


  /**
   * Copies new/updated notes from source to destination storage
   */
  void sync(int sourceRepoIndex, int destRepoIndex) throws IOException {
    LOG.info("Sync started");
    NotebookRepo srcRepo = getRepo(sourceRepoIndex);
    NotebookRepo dstRepo = getRepo(destRepoIndex);

    List<NoteInfo> srcNotes = srcRepo.list(null);
    List<NoteInfo> dstNotes = dstRepo.list(null);

    Map<String, List<String>> noteIDs = notesCheckDiff(srcNotes, srcRepo, dstNotes, dstRepo);
    List<String> pushNoteIDs = noteIDs.get(pushKey);
    List<String> pullNoteIDs = noteIDs.get(pullKey);
    List<String> delDstNoteIDs = noteIDs.get(delDstKey);

    String[] pushNoteIDsArr = new String[pushNoteIDs.size()];
    String[] pullNoteIDsArr = new String[pullNoteIDs.size()];
    String[] delDstNoteIDsArr = new String[delDstNoteIDs.size()];

    if (!pushNoteIDs.isEmpty()) {
      LOG.info("Notes with the following IDs will be pushed");
      for (String id : pushNoteIDs) {
        LOG.info("ID : " + id);
      }
      pushNotes(srcRepo, dstRepo, pushNoteIDs.toArray(pushNoteIDsArr));
    } else {
      LOG.info("Nothing to push");
    }

    if (!pullNoteIDs.isEmpty()) {
      LOG.info("Notes with the following IDs will be pulled");
      for (String id : pullNoteIDs) {
        LOG.info("ID : " + id);
      }
      pushNotes(dstRepo, srcRepo, pullNoteIDs.toArray(pullNoteIDsArr));
    } else {
      LOG.info("Nothing to pull");
    }

    if (!delDstNoteIDs.isEmpty()) {
      LOG.info("Notes with the following IDs will be deleted from dest");
      for (String id : delDstNoteIDs) {
        LOG.info("ID : " + id);
      }
      deleteNotes(dstRepo, delDstNoteIDs.toArray(delDstNoteIDsArr));
    } else {
      LOG.info("Nothing to delete from dest");
    }

    LOG.info("Sync ended");
  }

  public void sync() throws IOException {
    sync(0, 1);
  }

  /**
   * 同步指定的note到backup repo，用在将ES指定为primary repo，vfs为secondary repo，并且想利用git
   * revision功能时需要在note更新时触发从ES同步到VFS，这样基于文件系统的git diff才能起作用
   */
  public void sync(String... ids) throws IOException {
    if (getRepoCount() > 1) {
      pushNotes(this.getRepo(0), this.getRepo(1), ids);
    }
  }

  //采用varargs来在ES到VFS同步修改过的note时复用该方法
  private void pushNotes(NotebookRepo localRepo,
                         NotebookRepo remoteRepo, String... ids) throws IOException {
    for (String id : ids) {
      remoteRepo.save(localRepo.get(id, null), null);
    }
  }

  private void deleteNotes(NotebookRepo repo, String... ids) throws IOException {
    for (String id : ids) {
      repo.remove(id, null);
    }
  }

  public int getRepoCount() {
    return repos.size();
  }

  int getMaxRepoNum() {
    return maxRepoNum;
  }

  public NotebookRepo getRepo(int repoIndex) throws IOException {
    if (repoIndex < 0 || repoIndex >= getRepoCount()) {
      throw new IOException("Requested storage index " + repoIndex
              + " isn't initialized," + " repository count is " + getRepoCount());
    }
    return repos.get(repoIndex);
  }

  private Map<String, List<String>> notesCheckDiff(List<NoteInfo> sourceNotes,
                                                   NotebookRepo sourceRepo,
                                                   List<NoteInfo> destNotes, NotebookRepo destRepo)
          throws IOException {
    List<String> pushIDs = new ArrayList<String>();
    List<String> pullIDs = new ArrayList<String>();
    List<String> delDstIDs = new ArrayList<String>();

    NoteInfo dnote;
    Date sdate, ddate;
    for (NoteInfo snote : sourceNotes) {
      if (snote.getId() == null) {
        continue;
      }

      dnote = containsID(destNotes, snote.getId());
      if (dnote != null) {
        /* note exists in source and destination storage systems */
        sdate = sourceRepo.get(snote.getId(), null).getLastUpdated();
        ddate = destRepo.get(dnote.getId(), null).getLastUpdated();

        if (sdate.compareTo(ddate) != 0) {
          if (sdate.after(ddate) || oneWaySync) {
            /* if source contains more up to date note - push
             * if oneWaySync is enabled, always push no matter who's newer */
            pushIDs.add(snote.getId());
            LOG.info("Modified note is added to push list : " + sdate);
          } else {
            /* destination contains more up to date note - pull */
            LOG.info("Modified note is added to pull list : " + ddate);
            pullIDs.add(snote.getId());
          }
        }
      } else {
        /* note exists in source storage, and absent in destination
         * view source as up to date - push
         * (another scenario : note was deleted from destination - not considered)*/
        pushIDs.add(snote.getId());
      }
    }

    for (NoteInfo note : destNotes) {
      dnote = containsID(sourceNotes, note.getId());
      if (dnote == null) {
        /* note exists in destination storage, and absent in source */
        if (oneWaySync) {
          /* if oneWaySync is enabled, delete the note from destination */
          LOG.info("Extraneous note is added to delete dest list : " + note.getId());
          delDstIDs.add(note.getId());
        } else {
          /* if oneWaySync is disabled, pull the note from destination */
          LOG.info("Missing note is added to pull list : " + note.getId());
          pullIDs.add(note.getId());
        }
      }
    }

    Map<String, List<String>> map = new HashMap<String, List<String>>();
    map.put(pushKey, pushIDs);
    map.put(pullKey, pullIDs);
    map.put(delDstKey, delDstIDs);
    return map;
  }

  private NoteInfo containsID(List<NoteInfo> notes, String id) {
    for (NoteInfo note : notes) {
      if (note.getId() == null) {
        continue;
      }

      if (note.getId().equals(id)) {
        return note;
      }
    }
    return null;
  }

  @Override
  public void close() {
    LOG.info("Closing all notebook storages");
    for (NotebookRepo repo : repos) {
      repo.close();
    }
  }

  //checkpoint to all available storages
  @Override
  public Revision checkpoint(Note note, String checkpointMsg,
                             String principal) throws IOException {
    int repoCount = getRepoCount();
    int repoBound = Math.min(repoCount, getMaxRepoNum());
    int errorCount = 0;
    String errorMessage = "";
    List<Revision> allRepoCheckpoints = new ArrayList<Revision>(repoBound);
    for (int i = 0; i < repoBound; i++) {
      try {
        Revision rev = getRepo(i).checkpoint(note, checkpointMsg, principal);
        if (rev != null) {
          allRepoCheckpoints.add(rev);
        }
      } catch (IOException e) {
        LOG.warn("Couldn't checkpoint in {} storage with index {} for note {}",
                getRepo(i).getClass().toString(), i, note.getId());
        errorMessage += "Error on storage class " + getRepo(i).getClass().toString() +
                " with index " + i + " : " + e.getMessage() + "\n";
        errorCount++;
      }
    }
    // throw exception if failed to commit for all initialized repos
    if (errorCount == repoBound) {
      throw new IOException(errorMessage);
    }
    if (allRepoCheckpoints.size() > 0) {//只返回primary repo的checkpoint成功之后的revision
      Revision returnRev = allRepoCheckpoints.get(0);
      // if failed to checkpoint on first storage, then return result on second
      if (allRepoCheckpoints.size() > 1 && returnRev == null) {
        return allRepoCheckpoints.get(1);
      }
      return returnRev;
    }
    return null;
  }

  /**
   * 遍历primary和backup repo返回第一个找到的repo中的note
   *
   * @param noteId Id of the Notebook
   * @param revId  revision of the Notebook
   */
  @Override
  public Note get(String noteId, String revId, String principal) {
    try {
      for (int i = 0; i < getRepoCount(); i++) {
        Note revisionNote = getRepo(i).get(noteId, revId, principal);
        if (revisionNote != null) {
          return revisionNote;
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to get revision {} of note {}", revId, noteId, e);
    }
    return null;
  }

  /**
   * 遍历所有的repo，返回2者的合集
   *
   * @param noteId id of the Notebook
   */
  @Override
  public List<Revision> revisionHistory(String noteId, String principal) {
    try {
      for (int i = 0; i < getRepoCount(); i++) {
        List<Revision> returnRevisions = getRepo(i).revisionHistory(noteId, principal);
        if (returnRevisions != null) {
          return returnRevisions;
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to list revision history", e);
    }
    return Collections.emptyList();
  }

  /**
   * get first repo as primary repo
   */
  public NotebookRepo getPrimaryRepo() {
    if (repos.size() > 0)
      return repos.get(0);
    else
      return null;
  }

  /**
   * 短路设计，primary和back up repo依次提交，首次完成提交，即返回
   *
   * @param noteId     当前提交的note id，在dbms中由于revisionId是主键，故没有使用noteid
   * @param revisionId 待提交的版本
   */
  @Override
  public SubmitLeftOver submit(String noteId, String revisionId) throws SubmitStrategyVolationException {
    try {
      for (int i = 0; i < getRepoCount(); i++) {
        SubmitLeftOver submitLeftOver = getRepo(i).submit(noteId, revisionId);
        if (submitLeftOver != null) {
          return submitLeftOver;
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to submit revision:'{}' of note:'{}'", revisionId, noteId, e);
    }
    return null;
  }

  @Override
  public int currentSubmitTimes(String team, String projectId) {
    try {
      for (int i = 0; i < getRepoCount(); i++) {
        int time = getRepo(i).currentSubmitTimes(team, projectId);
        if (time != -1) {
          return time;
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to query current submit time for team:'{}' of project:'{}'", team, projectId, e);
    }
    return -1;
  }
}
