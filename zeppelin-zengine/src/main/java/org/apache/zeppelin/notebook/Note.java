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

package org.apache.zeppelin.notebook;

import com.google.gson.Gson;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.Input;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepoSync;
import org.apache.zeppelin.notebook.utility.IdHashes;
import org.apache.zeppelin.resource.ResourcePoolUtils;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.user.Credentials;
import org.apache.zeppelin.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Binded interpreters for a note
 */
public class Note implements Serializable, ParagraphJobListener {
  private static final Logger logger = LoggerFactory.getLogger(Note.class);
  private static final long serialVersionUID = 7920699076577612429L;

  // threadpool for delayed persist of note
  private static final ScheduledThreadPoolExecutor delayedPersistThreadPool =
          new ScheduledThreadPoolExecutor(0);

  static {
    delayedPersistThreadPool.setRemoveOnCancelPolicy(true);
  }

  final List<Paragraph> paragraphs = new LinkedList<>();

  private String name = "";
  private String id;

  private transient ZeppelinConfiguration conf = ZeppelinConfiguration.create();

  private String createdBy;// used for filter notes & permission check
  private List<String> tags;
  private String topic;
  private Date lastUpdated;//max(lastUpdaed in paragraphs)

  private Map<String, List<AngularObject>> angularObjects = new HashMap<>();

  private transient InterpreterFactory factory;
  private transient JobListenerFactory jobListenerFactory;
  private transient NotebookRepo repo;
  private transient SearchService index;
  private transient ScheduledFuture delayedPersist;
  private transient NoteEventListener noteEventListener;
  private transient Credentials credentials;

  /*
   * note configurations.
   * - looknfeel - cron
   */
  private Map<String, Object> config = new HashMap<>();

  /*
   * note information.
   * - cron : cron expression validity.
   */
  private Map<String, Object> info = new HashMap<>();


  public Note() {
  }

  /**
   * @param createdBy note's creator, current principal
   */
  public Note(NotebookRepo repo, InterpreterFactory factory,
              JobListenerFactory jlFactory, SearchService noteIndex, Credentials credentials, String createdBy, NoteEventListener noteEventListener) {
    this.repo = repo;
    this.factory = factory;
    this.jobListenerFactory = jlFactory;
    this.index = noteIndex;
    this.noteEventListener = noteEventListener;
    this.credentials = credentials;
    generateId();
    this.createdBy = createdBy;
  }

  private void generateId() {
    id = IdHashes.generateId();
  }

  private String getDefaultInterpreterName() {
    InterpreterSetting setting = factory.getDefaultInterpreterSetting(getId());
    return null != setting ? setting.getName() : StringUtils.EMPTY;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  private String normalizeNoteName(String name) {
    name = name.trim();
    name = name.replace("\\", "/");
    while (name.contains("///")) {
      name = name.replaceAll("///", "/");
    }
    name = name.replaceAll("//", "/");
    if (name.length() == 0) {
      name = "/";
    }
    return name;
  }

  public void setName(String name) {
    if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
      name = normalizeNoteName(name);
    }
    this.name = name;
  }

  void setInterpreterFactory(InterpreterFactory factory) {
    this.factory = factory;
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        p.setInterpreterFactory(factory);
      }
    }
  }

  public void initializeJobListenerForParagraph(Paragraph paragraph) {
    final Note paragraphNote = paragraph.getNote();
    if (paragraphNote.getId().equals(this.getId())) {
      throw new IllegalArgumentException(String.format("The paragraph %s from note %s " +
              "does not belong to note %s", paragraph.getId(), paragraphNote.getId(),
              this.getId()));
    }

    boolean foundParagraph = false;
    for (Paragraph ownParagraph : paragraphs) {
      if (paragraph.getId().equals(ownParagraph.getId())) {
        paragraph.setListener(this.jobListenerFactory.getParagraphJobListener(this));
        foundParagraph = true;
      }
    }

    if (!foundParagraph) {
      throw new IllegalArgumentException(String.format("Cannot find paragraph %s " +
                      "from note %s", paragraph.getId(), paragraphNote.getId()));
    }
  }

  void setJobListenerFactory(JobListenerFactory jobListenerFactory) {
    this.jobListenerFactory = jobListenerFactory;
  }

  void setNotebookRepo(NotebookRepo repo) {
    this.repo = repo;
  }

  public void setIndex(SearchService index) {
    this.index = index;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public void setCredentials(Credentials credentials) {
    this.credentials = credentials;
  }

  Map<String, List<AngularObject>> getAngularObjects() {
    return angularObjects;
  }

  /**
   * Add paragraph last.
   */

  public Paragraph addParagraph() {
    Paragraph p = new Paragraph(this, this, factory);
    setParagraphMagic(p, paragraphs.size());
    synchronized (paragraphs) {
      paragraphs.add(p);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(p);
    }
    return p;
  }

  /**
   * Clone paragraph and add it to note.
   *
   * @param srcParagraph source paragraph
   */
  void addCloneParagraph(Paragraph srcParagraph) {

    // Keep paragraph original ID
    final Paragraph newParagraph = new Paragraph(srcParagraph.getId(), this, this, factory);

    Map<String, Object> config = new HashMap<>(srcParagraph.getConfig());
    Map<String, Object> param = new HashMap<>(srcParagraph.settings.getParams());
    Map<String, Input> form = new HashMap<>(srcParagraph.settings.getForms());

    newParagraph.setConfig(config);
    newParagraph.settings.setParams(param);
    newParagraph.settings.setForms(form);
    newParagraph.setText(srcParagraph.getText());
    newParagraph.setTitle(srcParagraph.getTitle());

    try {
      Gson gson = GsonUtil.getGson();
      String resultJson = gson.toJson(srcParagraph.getReturn());
      InterpreterResult result = gson.fromJson(resultJson, InterpreterResult.class);
      newParagraph.setReturn(result, null);
    } catch (Exception e) {
      // 'result' part of Note consists of exception, instead of actual interpreter results
      logger.warn(
              "Paragraph " + srcParagraph.getId() + " has a result with exception. " + e.getMessage());
    }

    synchronized (paragraphs) {
      paragraphs.add(newParagraph);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(newParagraph);
    }
  }

  /**
   * Insert paragraph in given index.
   *
   * @param index index of paragraphs
   */
  public Paragraph insertParagraph(int index) {
    Paragraph p = new Paragraph(this, this, factory);
    setParagraphMagic(p, index);
    synchronized (paragraphs) {
      paragraphs.add(index, p);
    }
    if (noteEventListener != null) {
      noteEventListener.onParagraphCreate(p);
    }
    return p;
  }

  /**
   * used by ElasticSearchRepo when deserialized from index
   */
  public Paragraph addParagraph(Paragraph paragraph) {
    synchronized (paragraphs) {
      paragraphs.add(paragraph);
    }
    return paragraph;
  }

  /**
   * Remove paragraph by id.
   *
   * @param paragraphId ID of paragraph
   * @return a paragraph that was deleted, or <code>null</code> otherwise
   */
  public Paragraph removeParagraph(String paragraphId) {
    removeAllAngularObjectInParagraph(paragraphId);
    ResourcePoolUtils.removeResourcesBelongsToParagraph(getId(), paragraphId);
    synchronized (paragraphs) {
      Iterator<Paragraph> i = paragraphs.iterator();
      while (i.hasNext()) {
        Paragraph p = i.next();
        if (p.getId().equals(paragraphId)) {
          index.deleteIndexDoc(this, p);
          i.remove();

          if (noteEventListener != null) {
            noteEventListener.onParagraphRemove(p);
          }
          return p;
        }
      }
    }
    return null;
  }

  /**
   * Clear paragraph output by id.
   *
   * @param paragraphId ID of paragraph
   * @return Paragraph
   */
  public Paragraph clearParagraphOutput(String paragraphId) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (p.getId().equals(paragraphId)) {
          p.setReturn(null, null);
          return p;
        }
      }
    }
    return null;
  }

  /**
   * Move paragraph into the new index (order from 0 ~ n-1).
   *
   * @param index       new index
   * @param paragraphId ID of paragraph
   * @param index       new index
   */
  public void moveParagraph(String paragraphId, int index) {
    moveParagraph(paragraphId, index, false);
  }

  /**
   * Move paragraph into the new index (order from 0 ~ n-1).
   *
   * @param paragraphId                ID of paragraph
   * @param index                      new index
   * @param throwWhenIndexIsOutOfBound whether throw IndexOutOfBoundException when index is out of
   *                                   bound
   */
  public void moveParagraph(String paragraphId, int index, boolean throwWhenIndexIsOutOfBound) {
    synchronized (paragraphs) {
      int oldIndex;
      Paragraph p = null;

      if (index < 0 || index >= paragraphs.size()) {
        if (throwWhenIndexIsOutOfBound) {
          throw new IndexOutOfBoundsException(
              "paragraph size is " + paragraphs.size() + " , index is " + index);
        } else {
          return;
        }
      }

      for (int i = 0; i < paragraphs.size(); i++) {
        if (paragraphs.get(i).getId().equals(paragraphId)) {
          oldIndex = i;
          if (oldIndex == index) {
            return;
          }
          p = paragraphs.remove(i);
        }
      }

      if (p != null) {
        paragraphs.add(index, p);
      }
    }
  }

  public boolean isLastParagraph(String paragraphId) {
    if (!paragraphs.isEmpty()) {
      synchronized (paragraphs) {
        if (paragraphId.equals(paragraphs.get(paragraphs.size() - 1).getId())) {
          return true;
        }
      }
      return false;
    }
    /** because empty list, cannot remove nothing right? */
    return true;
  }

  public Paragraph getParagraph(String paragraphId) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (p.getId().equals(paragraphId)) {
          return p;
        }
      }
    }
    return null;
  }


  /**
   * get paragraph inde in paragraphs List
   */
  public int getParagraphIndex(Paragraph paragraph) {
    if (paragraph.getId() == null || paragraph.getId().isEmpty()) {
      return -1;
    }

    synchronized (paragraphs) {
      for (int i = 0; i < paragraphs.size(); i++) {
        if (paragraphs.get(i).getId().equals(paragraph.getId())) {
          return i;
        }
      }

      return -1;
    }
  }

  public Paragraph getLastParagraph() {
    synchronized (paragraphs) {
      return paragraphs.get(paragraphs.size() - 1);
    }
  }

  public List<Map<String, String>> generateParagraphsInfo() {
    List<Map<String, String>> paragraphsInfo = new LinkedList<>();
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        Map<String, String> info = populateParagraphInfo(p);
        paragraphsInfo.add(info);
      }
    }
    return paragraphsInfo;
  }

  public Map<String, String> generateSingleParagraphInfo(String paragraphId) {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (p.getId().equals(paragraphId)) {
          return populateParagraphInfo(p);
        }
      }
      return new HashMap<>();
    }
  }

  private Map<String, String> populateParagraphInfo(Paragraph p) {
    Map<String, String> info = new HashMap<>();
    info.put("id", p.getId());
    info.put("status", p.getStatus().toString());
    if (p.getDateStarted() != null) {
      info.put("started", p.getDateStarted().toString());
    }
    if (p.getDateFinished() != null) {
      info.put("finished", p.getDateFinished().toString());
    }
    if (p.getStatus().isRunning()) {
      info.put("progress", String.valueOf(p.progress()));
    }
    return info;
  }

  private void setParagraphMagic(Paragraph p, int index) {
    if (paragraphs.size() > 0) {
      String magic;
      if (index == 0) {
        magic = paragraphs.get(0).getMagic();
      } else {
        magic = paragraphs.get(index - 1).getMagic();
      }
      if (StringUtils.isNotEmpty(magic)) {
        p.setText(magic + "\n");
      }
    }
  }

  /**
   * Run all paragraphs sequentially.
   */
  public void runAll() {
    String cronExecutingUser = (String) getConfig().get("cronExecutingUser");
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (!p.isEnabled()) {
          continue;
        }
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setUser(cronExecutingUser);
        p.setAuthenticationInfo(authenticationInfo);
        run(p.getId());
      }
    }
  }

  /**
   * Run a single paragraph.
   *
   * @param paragraphId ID of paragraph
   */
  public void run(String paragraphId) {
    Paragraph p = getParagraph(paragraphId);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));
    String requiredReplName = p.getRequiredReplName();
    Interpreter intp = factory.getInterpreter(getId(), requiredReplName);
    if (intp == null) {
      String intpExceptionMsg =
          p.getJobName() + "'s Interpreter " + requiredReplName + " not found";
      InterpreterException intpException = new InterpreterException(intpExceptionMsg);
      InterpreterResult intpResult =
          new InterpreterResult(InterpreterResult.Code.ERROR, intpException.getMessage());
      p.setReturn(intpResult, intpException);
      p.setStatus(Job.Status.ERROR);
      throw intpException;
    }
    if (p.getConfig().get("enabled") == null || (Boolean) p.getConfig().get("enabled")) {
      intp.getScheduler().submit(p);
    }
  }

  /**
   * Check whether all paragraphs belongs to this note has terminated
   */
  boolean isTerminated() {
    synchronized (paragraphs) {
      for (Paragraph p : paragraphs) {
        if (!p.isTerminated()) {
          return false;
        }
      }
    }

    return true;
  }

  public List<InterpreterCompletion> completion(String paragraphId, String buffer, int cursor) {
    Paragraph p = getParagraph(paragraphId);
    p.setListener(jobListenerFactory.getParagraphJobListener(this));

    return p.completion(buffer, cursor);
  }

  public List<Paragraph> getParagraphs() {
    synchronized (paragraphs) {
      return new LinkedList<>(paragraphs);
    }
  }

  private void snapshotAngularObjectRegistry() {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = factory.getInterpreterSettings(getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      InterpreterGroup intpGroup = setting.getInterpreterGroup(id);
      AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();
      angularObjects.put(intpGroup.getId(), registry.getAllWithGlobal(id));
    }
  }

  private void removeAllAngularObjectInParagraph(String paragraphId) {
    angularObjects = new HashMap<>();

    List<InterpreterSetting> settings = factory.getInterpreterSettings(getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting setting : settings) {
      InterpreterGroup intpGroup = setting.getInterpreterGroup(id);
      AngularObjectRegistry registry = intpGroup.getAngularObjectRegistry();

      if (registry instanceof RemoteAngularObjectRegistry) {
        // remove paragraph scope object
        ((RemoteAngularObjectRegistry) registry).removeAllAndNotifyRemoteProcess(id, paragraphId);

        // remove app scope object
        List<ApplicationState> appStates = getParagraph(paragraphId).getAllApplicationStates();
        if (appStates != null) {
          for (ApplicationState app : appStates) {
            ((RemoteAngularObjectRegistry) registry)
                    .removeAllAndNotifyRemoteProcess(id, app.getId());
          }
        }
      } else {
        registry.removeAll(id, paragraphId);

        // remove app scope object
        List<ApplicationState> appStates = getParagraph(paragraphId).getAllApplicationStates();
        if (appStates != null) {
          for (ApplicationState app : appStates) {
            registry.removeAll(id, app.getId());
          }
        }
      }
    }
  }


  /**
   * TODO:not used(qy) partial update paragraph only, when changed only happened to individual
   * paragraph only
   */
  public void persistParagraph(Paragraph paragraph) throws IOException {
    stopDelayedPersistTimer();
    snapshotAngularObjectRegistry();

    this.setLastUpdated(this.getMaxLastUpdateInParagraphs());
    index.updateIndexParagraph(this, paragraph);
  }

  /**
   * persist note and paragraphs
   */
  public void persist(AuthenticationInfo subject) throws IOException {
    stopDelayedPersistTimer();
    snapshotAngularObjectRegistry();

    this.setLastUpdated(this.getMaxLastUpdateInParagraphs());

    index.updateIndexDoc(this);

    if (repo instanceof NotebookRepoSync) {
      NotebookRepoSync repoSync = (NotebookRepoSync) repo;
      NotebookRepo noteRepo = repoSync.getPrimaryRepo();

      if (index != noteRepo) {//when use ElasticSearch both as repo and SearchService,don't save twice
        repo.save(this, subject);
      }
    } else {
      if (repo != index) {
        repo.save(this, subject);
      }
    }

  }

  /**
   * max dateUpdated from all paragraphs
   */
  private Date getMaxLastUpdateInParagraphs() {
    Date maxUpdated = null;
    for (Paragraph para : this.getParagraphs()) {
      if (para.dateUpdated != null) {
        if (maxUpdated == null) {
          maxUpdated = para.dateUpdated;
        } else {
          if (maxUpdated.before(para.dateUpdated)) {
            maxUpdated = para.dateUpdated;
          }
        }
      }
    }
    return maxUpdated;
  }

  /**
   * Persist this note with maximum delay.
   */
  public void persist(int maxDelaySec, AuthenticationInfo subject) {
    startDelayedPersistTimer(maxDelaySec, subject);
  }

  void unpersist(AuthenticationInfo subject) throws IOException {
    repo.remove(getId(), subject);
  }


  private void startDelayedPersistTimer(int maxDelaySec, final AuthenticationInfo subject) {
    synchronized (this) {
      if (delayedPersist != null) {
        return;
      }

      delayedPersist = delayedPersistThreadPool.schedule(new Runnable() {

        @Override
        public void run() {
          try {
            persist(subject);
          } catch (IOException e) {
            logger.error(e.getMessage(), e);
          }
        }
      }, maxDelaySec, TimeUnit.SECONDS);
    }
  }

  private void stopDelayedPersistTimer() {
    synchronized (this) {
      if (delayedPersist == null) {
        return;
      }

      delayedPersist.cancel(false);
    }
  }

  public Map<String, Object> getConfig() {
    if (config == null) {
      config = new HashMap<>();
    }
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  public Map<String, Object> getInfo() {
    if (info == null) {
      info = new HashMap<>();
    }
    return info;
  }

  public void setInfo(Map<String, Object> info) {
    this.info = info;
  }

  @Override
  public void beforeStatusChange(Job job, Status before, Status after) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.beforeStatusChange(job, before, after);
      }
    }
  }

  @Override
  public void afterStatusChange(Job job, Status before, Status after) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.afterStatusChange(job, before, after);
      }
    }

    if (noteEventListener != null) {
      noteEventListener.onParagraphStatusChange((Paragraph) job, after);
    }
  }

  @Override
  public void onProgressUpdate(Job job, int progress) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onProgressUpdate(job, progress);
      }
    }
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }


  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }


  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public Date getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }


  @Override
  public void onOutputAppend(Paragraph paragraph, InterpreterOutput out, String output) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onOutputAppend(paragraph, out, output);
      }
    }
  }

  @Override
  public void onOutputUpdate(Paragraph paragraph, InterpreterOutput out, String output) {
    if (jobListenerFactory != null) {
      ParagraphJobListener listener = jobListenerFactory.getParagraphJobListener(this);
      if (listener != null) {
        listener.onOutputUpdate(paragraph, out, output);
      }
    }
  }

  void setNoteEventListener(NoteEventListener noteEventListener) {
    this.noteEventListener = noteEventListener;
  }

}
