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
package org.apache.zeppelin.socket;

import com.google.common.base.Strings;
import com.google.gson.reflect.TypeToken;

import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.display.AngularObjectRegistryListener;
import org.apache.zeppelin.helium.ApplicationEventListener;
import org.apache.zeppelin.helium.HeliumPackage;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcessListener;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.NotebookAuthorizationAdaptor;
import org.apache.zeppelin.notebook.NotebookEventListener;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphJobListener;
import org.apache.zeppelin.notebook.repo.NotebookRepo.Revision;
import org.apache.zeppelin.notebook.repo.commit.SubmitLeftOver;
import org.apache.zeppelin.notebook.repo.commit.SubmitStrategyVolationException;
import org.apache.zeppelin.notebook.socket.Message;
import org.apache.zeppelin.notebook.socket.Message.OP;
import org.apache.zeppelin.realm.UserProfile;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.server.ZeppelinServer;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.types.InterpreterSettingsList;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.GsonUtil;
import org.apache.zeppelin.utils.InterpreterBindingUtils;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.http.HttpServletRequest;

/**
 * Zeppelin websocket service.
 */
public class NotebookServer extends WebSocketServlet implements
        NotebookSocketListener, JobListenerFactory, AngularObjectRegistryListener,
        RemoteInterpreterProcessListener, ApplicationEventListener {
  /**
   * Job manager service type
   */
  protected enum JOB_MANAGER_SERVICE {
    JOB_MANAGER_PAGE("JOB_MANAGER_PAGE");
    private String serviceTypeKey;

    JOB_MANAGER_SERVICE(String serviceType) {
      this.serviceTypeKey = serviceType;
    }

    String getKey() {
      return this.serviceTypeKey;
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(NotebookServer.class);
  final Map<String, List<NotebookSocket>> noteSocketMap = new HashMap<>();
  final Queue<NotebookSocket> connectedSockets = new ConcurrentLinkedQueue<>();

  private Notebook notebook() {
    return ZeppelinServer.notebook;
  }

  @Override
  public void configure(WebSocketServletFactory factory) {
    factory.setCreator(new NotebookWebSocketCreator(this));
  }

  public boolean checkOrigin(HttpServletRequest request, String origin) {
    try {
      return org.apache.zeppelin.utils.SecurityUtils.isValidOrigin(origin, ZeppelinConfiguration.create());
    } catch (UnknownHostException e) {
      LOG.error(e.toString(), e);
    } catch (URISyntaxException e) {
      LOG.error(e.toString(), e);
    }
    return false;
  }

  public NotebookSocket doWebSocketConnect(HttpServletRequest req, String protocol) {
    return new NotebookSocket(req, protocol, this);
  }

  @Override
  public void onOpen(NotebookSocket conn) {
    LOG.info("New connection from {} : {}", conn.getRequest().getRemoteAddr(),
            conn.getRequest().getRemotePort());
    connectedSockets.add(conn);
  }

  @Override
  public void onMessage(NotebookSocket conn, String msg) {
    Notebook notebook = notebook();
    try {
      Message messagereceived = deserializeMessage(msg);
      LOG.debug("RECEIVE << " + messagereceived.op);
      LOG.debug("RECEIVE PRINCIPAL << " + messagereceived.principal);
      LOG.debug("RECEIVE TICKET << " + messagereceived.ticket);
      LOG.debug("RECEIVE GROUP << " + messagereceived.group);
      LOG.debug("RECEIVE ProjectId << " + messagereceived.projectId);

      if (LOG.isTraceEnabled()) {
        LOG.trace("RECEIVE MSG = " + messagereceived);
      }

      Subject subject = TicketContainer.instance.getCachedSubject(messagereceived.ticket, messagereceived.principal);
      if (subject == null || !subject.isAuthenticated()) {
        return;
      }

      ZeppelinConfiguration conf = ZeppelinConfiguration.create();
      boolean allowAnonymous = conf.
              getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_ANONYMOUS_ALLOWED);
      if (!allowAnonymous && messagereceived.principal.equals("anonymous")) {
        throw new Exception("Anonymous access not allowed ");
      }

      /** Lets be elegant here */
      switch (messagereceived.op) {
        case LIST_NOTES:
          unicastNoteList(conn, subject, true);
          break;
        case RELOAD_NOTES_FROM_REPO:
          unicastNoteList(conn, subject, true);
          //broadcastReloadedNoteList(subject);
          break;
        case GET_HOME_NOTE:
          sendHomeNote(conn, subject, notebook, messagereceived);
          break;
        case GET_NOTE:
          sendNote(conn, subject, notebook, messagereceived);
          break;
        case NEW_NOTE:
          createNote(conn, subject, notebook, messagereceived);
          break;
        case DEL_NOTE:
          removeNote(conn, subject, notebook, messagereceived);
          break;
        case CLONE_NOTE:
          cloneNote(conn, subject, notebook, messagereceived);
          break;
        case IMPORT_NOTE:
          importNote(conn, subject, notebook, messagereceived);
          break;
        case COMMIT_PARAGRAPH:
          updateParagraph(conn, subject, notebook, messagereceived);
          break;
        case RUN_PARAGRAPH:
          runParagraph(conn, subject, notebook, messagereceived);
          break;
        case CANCEL_PARAGRAPH:
          cancelParagraph(conn, subject, notebook, messagereceived);
          break;
        case MOVE_PARAGRAPH:
          moveParagraph(conn, subject, notebook, messagereceived);
          break;
        case INSERT_PARAGRAPH:
          insertParagraph(conn, subject, notebook, messagereceived);
          break;
        case PARAGRAPH_REMOVE:
          removeParagraph(conn, subject, notebook, messagereceived);
          break;
        case PARAGRAPH_CLEAR_OUTPUT:
          clearParagraphOutput(conn, subject, notebook, messagereceived);
          break;
        case NOTE_UPDATE:
          updateNote(conn, subject, notebook, messagereceived);
          break;
        case COMPLETION:
          completion(conn, subject, notebook, messagereceived);
          break;
        case PING:
          break; //do nothing
        case ANGULAR_OBJECT_UPDATED:
          angularObjectUpdated(conn, subject, notebook, messagereceived);
          break;
        case ANGULAR_OBJECT_CLIENT_BIND:
          angularObjectClientBind(conn, subject, notebook, messagereceived);
          break;
        case ANGULAR_OBJECT_CLIENT_UNBIND:
          angularObjectClientUnbind(conn, subject, notebook, messagereceived);
          break;
        case LIST_CONFIGURATIONS:
          sendAllConfigurations(conn, subject, notebook);
          break;
        case CHECKPOINT_NOTEBOOK:
          checkpointNotebook(conn, notebook, messagereceived);
          break;
        case LIST_REVISION_HISTORY:
          listRevisionHistory(conn, notebook, messagereceived);
          break;
        case NOTE_REVISION:
          getNoteByRevision(conn, notebook, messagereceived);
          break;
        case NOTE_REVISION_SUBMIT://提交到组委会
          submitNotebook(conn, notebook, messagereceived);
          break;
        case QUERY_SUBMIT_TIME://查询已经提交的次数
          currentSubmitTimes(conn, notebook, messagereceived);
          break;
        case LIST_NOTEBOOK_JOBS:
          unicastNotebookJobInfo(conn, messagereceived);
          break;
        case UNSUBSCRIBE_UPDATE_NOTEBOOK_JOBS:
          unsubscribeNotebookJobInfo(conn);
          break;
        case GET_INTERPRETER_BINDINGS:
          getInterpreterBindings(conn, messagereceived);
          break;
        case SAVE_INTERPRETER_BINDINGS:
          saveInterpreterBindings(conn, messagereceived);
          break;
        default:
          break;
      }
    } catch (Exception e) {
      LOG.error("Can't handle message", e);
    }
  }

  @Override
  public void onClose(NotebookSocket conn, int code, String reason) {
    LOG.info("Closed connection to {} : {}. ({}) {}", conn.getRequest()
            .getRemoteAddr(), conn.getRequest().getRemotePort(), code, reason);
    removeConnectionFromAllNote(conn);
    connectedSockets.remove(conn);
  }

  protected Message deserializeMessage(String msg) {
    return GsonUtil.fromJson(msg, Message.class);
  }

  protected String serializeMessage(Message m) {
    return GsonUtil.toJson(m);
  }

  private void addConnectionToNote(String noteId, NotebookSocket socket) {
    synchronized (noteSocketMap) {
      removeConnectionFromAllNote(socket); // make sure a socket relates only a single note.
      List<NotebookSocket> socketList = noteSocketMap.get(noteId);
      if (socketList == null) {
        socketList = new LinkedList<>();
        noteSocketMap.put(noteId, socketList);
      }
      if (!socketList.contains(socket)) {
        socketList.add(socket);
      }
    }
  }

  private void removeConnectionFromNote(String noteId, NotebookSocket socket) {
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketList = noteSocketMap.get(noteId);
      if (socketList != null) {
        socketList.remove(socket);
      }
    }
  }

  private void removeNote(String noteId) {
    synchronized (noteSocketMap) {
      noteSocketMap.remove(noteId);
    }
  }

  private void removeConnectionFromAllNote(NotebookSocket socket) {
    synchronized (noteSocketMap) {
      Set<String> keys = noteSocketMap.keySet();
      for (String noteId : keys) {
        removeConnectionFromNote(noteId, socket);
      }
    }
  }

  private String getOpenNoteId(NotebookSocket socket) {
    String id = null;
    synchronized (noteSocketMap) {
      Set<String> keys = noteSocketMap.keySet();
      for (String noteId : keys) {
        List<NotebookSocket> sockets = noteSocketMap.get(noteId);
        if (sockets.contains(socket)) {
          id = noteId;
        }
      }
    }

    return id;
  }

  private void broadcastToNoteBindedInterpreter(String interpreterGroupId,
                                                Message m) {
    Notebook notebook = notebook();
    List<Note> notes = notebook.getAllNotes();
    for (Note note : notes) {
      List<String> ids = notebook.getInterpreterFactory().getInterpreters(note.getId());
      for (String id : ids) {
        if (id.equals(interpreterGroupId)) {
          broadcast(note.getId(), m);
        }
      }
    }
  }

  private void broadcast(String noteId, Message m) {
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m.op);
      for (NotebookSocket conn : socketLists) {
        try {
          conn.send(serializeMessage(m));
        } catch (IOException e) {
          LOG.error("socket error", e);
        }
      }
    }
  }

  private void broadcastExcept(String noteId, Message m, NotebookSocket exclude) {
    synchronized (noteSocketMap) {
      List<NotebookSocket> socketLists = noteSocketMap.get(noteId);
      if (socketLists == null || socketLists.size() == 0) {
        return;
      }
      LOG.debug("SEND >> " + m.op);
      for (NotebookSocket conn : socketLists) {
        if (exclude.equals(conn)) {
          continue;
        }
        try {
          conn.send(serializeMessage(m));
        } catch (IOException e) {
          LOG.error("socket error", e);
        }
      }
    }
  }

  private void broadcastAll(Message m) {
    for (NotebookSocket conn : connectedSockets) {
      try {
        conn.send(serializeMessage(m));
      } catch (IOException e) {
        LOG.error("socket error", e);
      }
    }
  }

  private void unicast(Message m, NotebookSocket conn) {
    try {
      conn.send(serializeMessage(m));
    } catch (IOException e) {
      LOG.error("socket error", e);
    }
  }

  public void unicastNotebookJobInfo(NotebookSocket conn, Message fromMessage) throws IOException {
    addConnectionToNote(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(), conn);
    List<Map<String, Object>> notebookJobs = notebook()
            .getJobListByUnixTime(false, 0, fromMessage.principal);

    Map<String, Object> response = new HashMap<>();

    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notebookJobs);

    conn.send(serializeMessage(new Message(OP.LIST_NOTEBOOK_JOBS)
            .put("notebookJobs", response)));
  }

  public void broadcastUpdateNotebookJobInfo(long lastUpdateUnixTime) throws IOException {
    List<Map<String, Object>> notebookJobs = new LinkedList<>();
    Notebook notebookObject = notebook();
    List<Map<String, Object>> jobNotes = null;
    if (notebookObject != null) {
      jobNotes = notebook().getJobListByUnixTime(false, lastUpdateUnixTime, null);
      notebookJobs = jobNotes == null ? notebookJobs : jobNotes;
    }

    Map<String, Object> response = new HashMap<>();
    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", notebookJobs != null ? notebookJobs : new LinkedList<>());

    broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
            new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));
  }

  public void unsubscribeNotebookJobInfo(NotebookSocket conn) {
    removeConnectionFromNote(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(), conn);
  }

  public void saveInterpreterBindings(NotebookSocket conn, Message fromMessage) {
    String noteId = (String) fromMessage.data.get("noteID");
    try {
      List<String> settingIdList = GsonUtil.fromJson(String.valueOf(
              fromMessage.data.get("selectedSettingIds")), new TypeToken<ArrayList<String>>() {
      }.getType());
      notebook().bindInterpretersToNote(noteId, settingIdList);
      broadcastInterpreterBindings(noteId,
              InterpreterBindingUtils.getInterpreterBindings(notebook(), noteId));
    } catch (Exception e) {
      LOG.error("Error while saving interpreter bindings", e);
    }
  }

  public void getInterpreterBindings(NotebookSocket conn, Message fromMessage)
          throws IOException {
    String noteID = (String) fromMessage.data.get("noteID");
    List<InterpreterSettingsList> settingList =
            InterpreterBindingUtils.getInterpreterBindings(notebook(), noteID);
    conn.send(serializeMessage(new Message(OP.INTERPRETER_BINDINGS)
            .put("interpreterBindings", settingList)));
  }

  /**
   * refresh notes from repo,and apply filtered by userAndRoles
   */
  public List<Map<String, String>> generateNotebooksInfo(boolean isReload, Subject subject) {
    Notebook notebook = this.refreshNotes(isReload);
    List<Note> notes = notebook.getAllNotes();
    ZeppelinConfiguration conf = notebook.getConf();
    String homescreenNotebookId = conf.getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);
    boolean hideHomeScreenNotebookFromList = conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN_HIDE);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();

    List<Map<String, String>> notesInfo = new LinkedList<>();
    for (Note note : notes) {
      //返回该user对该note的读写和owner权限
      String mask = null;
      if (notebookAuthorization.isAdmin(subject)) {
        mask = "admin";
      } else if (notebookAuthorization.isOwner(subject, note.getGroup(), note.getId())) {
        mask = "owner";//如果不是组内成员，但是是组内note的reader吗？
      } else if (notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
        mask = "writer";
      } else if (notebookAuthorization.isReader(subject, note.getGroup(), note.getId())) {
        mask = "reader";
      } else {
        continue;
      }

      Map<String, String> info = new HashMap<>(3);
      if (hideHomeScreenNotebookFromList && note.getId().equals(homescreenNotebookId)) {
        continue;
      }

      info.put("id", note.getId());
      info.put("name", note.getName());
      info.put("mask", mask);//TODO:前台可以根据状态显示只读和可写图标
      notesInfo.add(info);
    }

    return notesInfo;
  }

  /**
   * add note's creator to owners set, creator should not be explicit set in shiro.ini
   */
  //TODO:这里应该是groupId
  private void addCreatorToOwners(NotebookAuthorizationAdaptor notebookAuthorization, Note note) {
    notebookAuthorization.addOwner(note.getId(), note.getCreatedBy());
  }

  /**
   * 重新加载所有的note，不根据用户过滤，根据user过滤的操作放到该方法的调用者上
   */
  private Notebook refreshNotes(boolean isReload) {
    Notebook notebook = notebook();

    if (isReload) {
      try {
        long startTime = System.currentTimeMillis();
        notebook.reloadAllNotes(null);//这里不能传入userName，如果按照userName=createdBy过滤，则不是creator的note就不能显示出来了
        long endTime = System.currentTimeMillis();
        LOG.debug("查询所有的note列表时间:{} 秒", (endTime - startTime) / 1000.0);
      } catch (IOException e) {
        LOG.error("Fail to reload notes from repository", e);
      }
    }
    return notebook;
  }

  public void broadcastNote(Note note) {
    broadcast(note.getId(), new Message(OP.NOTE).put("note", note));
  }

  /**
   * get all existing notes, filter by userAndRoles
   */
  public void broadcastInterpreterBindings(String noteId,
                                           List settingList) {
    broadcast(noteId, new Message(OP.INTERPRETER_BINDINGS)
            .put("interpreterBindings", settingList));
  }

  public void broadcastNoteList(Subject subject) {
    List<Map<String, String>> notesInfo = generateNotebooksInfo(true, subject);
    broadcastAll(new Message(OP.NOTES_INFO).put("notes", notesInfo));
  }

  public void unicastNoteList(NotebookSocket conn, Subject subject, boolean isReload) {
    List<Map<String, String>> notesInfo = generateNotebooksInfo(isReload, subject);
    unicast(new Message(OP.NOTES_INFO).put("notes", notesInfo), conn);
  }

  /**
   * 权限不足时，发送给前端的message通知
   *
   * @param conn    当前WS connection
   * @param subject 当前用户
   * @param op      操作类型
   * @param group   参赛队
   * @param noteId  note的id
   */
  void permissionError(NotebookSocket conn, Subject subject, String op, String group,
                       String noteId) throws IOException {
    conn.send(serializeMessage(new Message(OP.AUTH_INFO).put("info",
            "权限不足:用户[" + ((UserProfile) (subject.getPrincipal())).getUserName() + "]不能" + op + "算法:[" + noteId + "],参赛队:[" + group + "]")));
  }

  private void sendNote(NotebookSocket conn, Subject subject, Notebook notebook,
                        Message fromMessage) throws IOException {

    LOG.info("New operation from {} : {} : {} : {} : {}", conn.getRequest().getRemoteAddr(),
            conn.getRequest().getRemotePort(),
            fromMessage.principal, fromMessage.op, fromMessage.get("id")
    );

    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }

    Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (note != null) {
      if (!notebookAuthorization.isReader(subject, note.getGroup(), note.getId())) {
        permissionError(conn, subject, "读取", note.getGroup(), note.getId());
        return;
      }
      addConnectionToNote(note.getId(), conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
      sendAllAngularObjects(note, conn);
    } else {
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", null)));
    }
  }

  private void sendHomeNote(NotebookSocket conn, Subject subject,
                            Notebook notebook, Message fromMessage) throws IOException {
    String noteId = notebook.getConf().getString(ConfVars.ZEPPELIN_NOTEBOOK_HOMESCREEN);

    Note note = null;
    if (noteId != null) {
      note = notebook.getNote(noteId);
    }

    if (note != null) {
      NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
      if (!notebookAuthorization.isReader(subject, note.getGroup(), note.getId())) {
        permissionError(conn, subject, "读取", note.getGroup(), note.getId());
        return;
      }
      addConnectionToNote(note.getId(), conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", note)));
      sendAllAngularObjects(note, conn);
    } else {
      removeConnectionFromAllNote(conn);
      conn.send(serializeMessage(new Message(OP.NOTE).put("note", null)));
    }
  }

  private void updateNote(NotebookSocket conn, Subject subject,
                          Notebook notebook, Message fromMessage)
          throws SchedulerException, IOException {
    String noteId = (String) fromMessage.get("id");
    String name = (String) fromMessage.get("name");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    if (noteId == null) {
      return;
    }
    if (config == null) {
      return;
    }

    Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    if (note != null) {
      boolean cronUpdated = isCronUpdated(config, note.getConfig());
      note.setName(name);
      note.setConfig(config);
      if (cronUpdated) {
        notebook.refreshCron(note.getId());
      }

      UserProfile userProfile = (UserProfile) (subject.getPrincipal());
      note.persist(userProfile.getUserName());
      broadcastNote(note);
      unicastNoteList(conn, subject, false);
    }
  }

  private boolean isCronUpdated(Map<String, Object> configA,
                                Map<String, Object> configB) {
    boolean cronUpdated = false;
    if (configA.get("cron") != null && configB.get("cron") != null
            && configA.get("cron").equals(configB.get("cron"))) {
      cronUpdated = true;
    } else if (configA.get("cron") == null && configB.get("cron") == null) {
      cronUpdated = false;
    } else if (configA.get("cron") != null || configB.get("cron") != null) {
      cronUpdated = true;
    }

    return cronUpdated;
  }

  private void createNote(NotebookSocket conn, Subject subject, Notebook notebook, Message message)
          throws IOException {
    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    Note note = notebook.createNote(userProfile.getUserName(), userProfile.getTeam(), userProfile.getProjectId());
    note.setGroup(message.group);//设置note所属的参赛队
    note.setProjectId(message.projectId);//设置note所属的题目
    note.addParagraph(); // it's an empty note. so add one paragraph
    if (message != null) {
      String noteName = (String) message.get("name");
      if (noteName == null || noteName.isEmpty()) {
        noteName = "Note " + note.getId();
      }
      note.setName(noteName);
    }

    //TODO：遗留代码，在使用note-authorization.json作为authentication持久化机制的时候，会使用；
    //TODO： shiro jdbcRealm没有override该方法，如果note的creator有区别于其他组内成员的其他permission，则需要在ShiroNotebookAuthorization进行override控制
    addCreatorToNoteOwner(notebook, message.principal, note);

    note.persist(userProfile.getUserName());
    addConnectionToNote(note.getId(), conn);

    Message messageToWeb = new Message(OP.NEW_NOTE);
    messageToWeb.put("note", note);
    messageToWeb.group = message.group;
    messageToWeb.principal = message.principal;
    messageToWeb.roles = message.roles;
    messageToWeb.ticket = message.ticket;
    messageToWeb.projectId = message.projectId;

    conn.send(serializeMessage(messageToWeb));
    unicastNoteList(conn, subject, false);
  }

  /**
   * set bussiness topic to note
   */
  private void setNoteTopic(NotebookSocket conn, Subject subject,
                            Notebook notebook, Message message)//TODO: client message assemble
          throws IOException {
    String noteId = (String) message.get("id");
    if (noteId == null || noteId.isEmpty()) {
      return;
    }

    String topic = (String) message.get("topic");
    if (topic == null || topic.isEmpty()) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      return;
    }

    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    note.setTopic(topic);

//    Subject shiroSubject = buildNewSubject(message.principal, ZeppelinConfiguration.create().getString(ConfVars.ZEPPELIN_SHIRO_REALM_NAME));
//    AuthenticationInfo subject = new AuthenticationInfo(message.principal);

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    note.persist(userProfile.getUserName());
    broadcast(note.getId(), new Message(OP.NOTE_TOPIC).put("topic", topic)); //TODO: qy client update topic(when multiple clients)
  }

  /**
   * set tags to note
   */
  private void setNoteTags(NotebookSocket conn, Subject subject,
                           Notebook notebook, Message message)
          throws IOException {
    String noteId = (String) message.get("id");
    if (noteId == null || noteId.isEmpty()) {
      return;
    }

    String tagsString = (String) message.get("tags");
    if (tagsString == null || tagsString.isEmpty()) {
      return;
    }
    String[] tags = tagsString.split(",");
    if (tags.length == 0) {
      return;
    }

    Note note = notebook.getNote(noteId);
    if (note == null) {
      return;
    }

    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    note.setTags(Arrays.asList(tags));

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    note.persist(userProfile.getUserName());
    broadcast(note.getId(), new Message(OP.NOTE_TAGS).put("tags", tags)); //TODO: qy client update tags(when multiple clients)
  }


  /**
   * set note creator to owners set
   */
  private void addCreatorToNoteOwner(Notebook notebook, String principal, Note note) {
    Set<String> owners = new LinkedHashSet<>(1);
    owners.add(principal);
    notebook.getNotebookAuthorization().setOwners(note.getId(), owners);
  }

  private void removeNote(NotebookSocket conn, Subject subject,
                          Notebook notebook, Message fromMessage)
          throws IOException {
    String noteId = (String) fromMessage.get("id");
    if (noteId == null) {
      return;
    }

    Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isOwner(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "删除", note.getGroup(), note.getId());
      return;
    }

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    notebook.removeNote(noteId, userProfile.getUserName());
    removeNote(noteId);
    unicastNoteList(conn, subject, false);
  }

  private void updateParagraph(NotebookSocket conn, Subject subject,
                               Notebook notebook, Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();

    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    Paragraph p = note.getParagraph(paragraphId);
    p.settings.setParams(params);
    p.setConfig(config);
    p.setTitle((String) fromMessage.get("title"));
    p.setText((String) fromMessage.get("paragraph"));

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    note.persist(userProfile.getUserName());
    broadcast(note.getId(), new Message(OP.PARAGRAPH).put("paragraph", p));
  }

  private void cloneNote(NotebookSocket conn, Subject subject,
                         Notebook notebook, Message fromMessage)
          throws IOException, CloneNotSupportedException {
    String noteId = getOpenNoteId(conn);
    String name = (String) fromMessage.get("name");

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    Note newNote = notebook.cloneNote(noteId, name, userProfile.getUserName(), userProfile.getTeam(), userProfile.getProjectId());

    addConnectionToNote(newNote.getId(), conn);
    conn.send(serializeMessage(new Message(OP.NEW_NOTE).put("note", newNote)));
    broadcastNoteList(subject);
  }

  /**
   * TODO: change created by when import someone else's note?
   */
  protected Note importNote(NotebookSocket conn, Subject subject,
                            Notebook notebook, Message fromMessage)
          throws IOException {
    Note note = null;
    if (fromMessage != null) {
      String noteName = (String) ((Map) fromMessage.get("notebook")).get("name");
      String noteJson = GsonUtil.toJson(fromMessage.get("notebook"));

      UserProfile userProfile = (UserProfile) (subject.getPrincipal());
      note = notebook.importNote(noteJson, noteName, userProfile.getUserName(), userProfile.getTeam(), userProfile.getProjectId());

      note.persist(userProfile.getUserName());
      broadcastNote(note);
      broadcastNoteList(subject);
    }
    return note;
  }

  private void removeParagraph(NotebookSocket conn, Subject subject,
                               Notebook notebook, Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }
    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();

    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    String userName = userProfile.getUserName();
    /** We dont want to remove the last paragraph */
    if (!note.isLastParagraph(paragraphId)) {
      note.removeParagraph(paragraphId);
      note.persist(userName);
      broadcastNote(note);
    }
  }

  private void clearParagraphOutput(NotebookSocket conn, Subject subject,
                                    Notebook notebook, Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }
    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();

    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    note.clearParagraphOutput(paragraphId);
    broadcastNote(note);
  }

  private void completion(NotebookSocket conn, Subject subject, Notebook notebook,
                          Message fromMessage) throws IOException {
    String paragraphId = (String) fromMessage.get("id");
    String buffer = (String) fromMessage.get("buf");
    int cursor = (int) Double.parseDouble(fromMessage.get("cursor").toString());
    Message resp = new Message(OP.COMPLETION_LIST).put("id", paragraphId);
    if (paragraphId == null) {
      conn.send(serializeMessage(resp));
      return;
    }

    final Note note = notebook.getNote(getOpenNoteId(conn));
    List<InterpreterCompletion> candidates = note.completion(paragraphId, buffer, cursor);
    resp.put("completions", candidates);
    conn.send(serializeMessage(resp));
  }

  /**
   * When angular object updated from client
   *
   * @param conn        the web socket.
   * @param notebook    the notebook.
   * @param fromMessage the message.
   */
  private void angularObjectUpdated(NotebookSocket conn, Subject subject,
                                    Notebook notebook, Message fromMessage) {
    String noteId = (String) fromMessage.get("noteId");
    String paragraphId = (String) fromMessage.get("paragraphId");
    String interpreterGroupId = (String) fromMessage.get("interpreterGroupId");
    String varName = (String) fromMessage.get("name");
    Object varValue = fromMessage.get("value");
    AngularObject ao = null;
    boolean global = false;
    // propagate change to (Remote) AngularObjectRegistry
    Note note = notebook.getNote(noteId);
    if (note != null) {
      List<InterpreterSetting> settings = notebook.getInterpreterFactory()
              .getInterpreterSettings(note.getId());
      for (InterpreterSetting setting : settings) {
        if (setting.getInterpreterGroup(note.getId()) == null) {
          continue;
        }
        if (interpreterGroupId.equals(setting.getInterpreterGroup(note.getId()).getId())) {
          AngularObjectRegistry angularObjectRegistry = setting
                  .getInterpreterGroup(note.getId()).getAngularObjectRegistry();

          // first trying to get local registry
          ao = angularObjectRegistry.get(varName, noteId, paragraphId);
          if (ao == null) {
            // then try notebook scope registry
            ao = angularObjectRegistry.get(varName, noteId, null);
            if (ao == null) {
              // then try global scope registry
              ao = angularObjectRegistry.get(varName, null, null);
              if (ao == null) {
                LOG.warn("Object {} is not binded", varName);
              } else {
                // path from client -> server
                ao.set(varValue, false);
                global = true;
              }
            } else {
              // path from client -> server
              ao.set(varValue, false);
              global = false;
            }
          } else {
            ao.set(varValue, false);
            global = false;
          }
          break;
        }
      }
    }

    if (global) { // broadcast change to all web session that uses related
      // interpreter.
      for (Note n : notebook.getAllNotes()) {
        List<InterpreterSetting> settings = notebook.getInterpreterFactory()
                .getInterpreterSettings(note.getId());
        for (InterpreterSetting setting : settings) {
          if (setting.getInterpreterGroup(n.getId()) == null) {
            continue;
          }
          if (interpreterGroupId.equals(setting.getInterpreterGroup(n.getId()).getId())) {
            AngularObjectRegistry angularObjectRegistry = setting
                    .getInterpreterGroup(n.getId()).getAngularObjectRegistry();
            this.broadcastExcept(
                    n.getId(),
                    new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
                            .put("interpreterGroupId", interpreterGroupId)
                            .put("noteId", n.getId())
                            .put("paragraphId", ao.getParagraphId()),
                    conn);
          }
        }
      }
    } else { // broadcast to all web session for the note
      this.broadcastExcept(
              note.getId(),
              new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
                      .put("interpreterGroupId", interpreterGroupId)
                      .put("noteId", note.getId())
                      .put("paragraphId", ao.getParagraphId()),
              conn);
    }
  }

  /**
   * Push the given Angular variable to the target interpreter angular registry given a noteId and a
   * paragraph id
   */
  protected void angularObjectClientBind(NotebookSocket conn, Subject subject,
                                         Notebook notebook, Message fromMessage)
          throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    Object varValue = fromMessage.get("value");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = notebook.getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException("target paragraph not specified for " +
              "angular value bind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note,
              paragraphId);

      final AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();
      if (registry instanceof RemoteAngularObjectRegistry) {

        RemoteAngularObjectRegistry remoteRegistry = (RemoteAngularObjectRegistry) registry;
        pushAngularObjectToRemoteRegistry(noteId, paragraphId, varName, varValue, remoteRegistry,
                interpreterGroup.getId(), conn);

      } else {
        pushAngularObjectToLocalRepo(noteId, paragraphId, varName, varValue, registry,
                interpreterGroup.getId(), conn);
      }
    }
  }

  /**
   * Remove the given Angular variable to the target interpreter(s) angular registry given a noteId
   * and an optional list of paragraph id(s)
   */
  protected void angularObjectClientUnbind(NotebookSocket conn, Subject subject,
                                           Notebook notebook, Message fromMessage)
          throws Exception {
    String noteId = fromMessage.getType("noteId");
    String varName = fromMessage.getType("name");
    String paragraphId = fromMessage.getType("paragraphId");
    Note note = notebook.getNote(noteId);

    if (paragraphId == null) {
      throw new IllegalArgumentException("target paragraph not specified for " +
              "angular value unBind");
    }

    if (note != null) {
      final InterpreterGroup interpreterGroup = findInterpreterGroupForParagraph(note,
              paragraphId);

      final AngularObjectRegistry registry = interpreterGroup.getAngularObjectRegistry();

      if (registry instanceof RemoteAngularObjectRegistry) {
        RemoteAngularObjectRegistry remoteRegistry = (RemoteAngularObjectRegistry) registry;
        removeAngularFromRemoteRegistry(noteId, paragraphId, varName, remoteRegistry,
                interpreterGroup.getId(), conn);
      } else {
        removeAngularObjectFromLocalRepo(noteId, paragraphId, varName, registry,
                interpreterGroup.getId(), conn);
      }
    }
  }

  private InterpreterGroup findInterpreterGroupForParagraph(Note note, String paragraphId)
          throws Exception {
    final Paragraph paragraph = note.getParagraph(paragraphId);
    if (paragraph == null) {
      throw new IllegalArgumentException("Unknown paragraph with id : " + paragraphId);
    }
    return paragraph.getCurrentRepl().getInterpreterGroup();
  }

  private void pushAngularObjectToRemoteRegistry(String noteId, String paragraphId,
                                                 String varName, Object varValue,
                                                 RemoteAngularObjectRegistry remoteRegistry,
                                                 String interpreterGroupId, NotebookSocket conn) {

    final AngularObject ao = remoteRegistry.addAndNotifyRemoteProcess(varName, varValue,
            noteId, paragraphId);

    this.broadcastExcept(
            noteId,
            new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", ao)
                    .put("interpreterGroupId", interpreterGroupId)
                    .put("noteId", noteId)
                    .put("paragraphId", paragraphId),
            conn);
  }

  private void removeAngularFromRemoteRegistry(String noteId, String paragraphId,
                                               String varName,
                                               RemoteAngularObjectRegistry remoteRegistry,
                                               String interpreterGroupId, NotebookSocket conn) {
    final AngularObject ao = remoteRegistry.removeAndNotifyRemoteProcess(varName, noteId,
            paragraphId);
    this.broadcastExcept(
            noteId,
            new Message(OP.ANGULAR_OBJECT_REMOVE).put("angularObject", ao)
                    .put("interpreterGroupId", interpreterGroupId)
                    .put("noteId", noteId)
                    .put("paragraphId", paragraphId),
            conn);
  }

  private void pushAngularObjectToLocalRepo(String noteId, String paragraphId, String varName,
                                            Object varValue, AngularObjectRegistry registry,
                                            String interpreterGroupId, NotebookSocket conn) {
    AngularObject angularObject = registry.get(varName, noteId, paragraphId);
    if (angularObject == null) {
      angularObject = registry.add(varName, varValue, noteId, paragraphId);
    } else {
      angularObject.set(varValue, true);
    }

    this.broadcastExcept(
            noteId,
            new Message(OP.ANGULAR_OBJECT_UPDATE).put("angularObject", angularObject)
                    .put("interpreterGroupId", interpreterGroupId)
                    .put("noteId", noteId)
                    .put("paragraphId", paragraphId),
            conn);
  }

  private void removeAngularObjectFromLocalRepo(String noteId, String paragraphId, String varName,
                                                AngularObjectRegistry registry,
                                                String interpreterGroupId, NotebookSocket conn) {
    final AngularObject removed = registry.remove(varName, noteId, paragraphId);
    if (removed != null) {
      this.broadcastExcept(
              noteId,
              new Message(OP.ANGULAR_OBJECT_REMOVE).put("angularObject", removed)
                      .put("interpreterGroupId", interpreterGroupId)
                      .put("noteId", noteId)
                      .put("paragraphId", paragraphId),
              conn);
    }
  }

  private void moveParagraph(NotebookSocket conn, Subject subject, Notebook notebook,
                             Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    final int newIndex = (int) Double.parseDouble(fromMessage.get("index")
            .toString());
    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    note.moveParagraph(paragraphId, newIndex);

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    note.persist(userProfile.getUserName());
    broadcastNote(note);
  }

  private void insertParagraph(NotebookSocket conn, Subject subject,
                               Notebook notebook, Message fromMessage) throws IOException {
    final int index = (int) Double.parseDouble(fromMessage.get("index")
            .toString());
    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isWriter(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "修改", note.getGroup(), note.getId());
      return;
    }

    note.insertParagraph(index);

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    note.persist(userProfile.getUserName());
    broadcastNote(note);
  }

  private void cancelParagraph(NotebookSocket conn, Subject subject, Notebook notebook,
                               Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isExecutor(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "取消执行", note.getGroup(), note.getId());
      return;
    }

    Paragraph p = note.getParagraph(paragraphId);
    p.abort();
  }

  private void runParagraph(NotebookSocket conn, Subject subject, Notebook notebook,
                            Message fromMessage) throws IOException {
    final String paragraphId = (String) fromMessage.get("id");
    if (paragraphId == null) {
      return;
    }

    String noteId = getOpenNoteId(conn);
    final Note note = notebook.getNote(noteId);
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isExecutor(subject, note.getGroup(), note.getId())) {
      permissionError(conn, subject, "执行", note.getGroup(), note.getId());
      return;
    }

    Paragraph p = note.getParagraph(paragraphId);
    String text = (String) fromMessage.get("paragraph");
    p.setText(text);
    p.setTitle((String) fromMessage.get("title"));

    if (!fromMessage.principal.equals("anonymous")) {
      AuthenticationInfo authenticationInfo = new AuthenticationInfo(fromMessage.principal,
              fromMessage.ticket);
      p.setAuthenticationInfo(authenticationInfo);
    } else {
      p.setAuthenticationInfo(new AuthenticationInfo());
    }

    Map<String, Object> params = (Map<String, Object>) fromMessage.get("params");
    p.settings.setParams(params);
    Map<String, Object> config = (Map<String, Object>) fromMessage.get("config");
    p.setConfig(config);
    // if it's the last paragraph, let's add a new one
    boolean isTheLastParagraph = note.isLastParagraph(p.getId());
    if (!(text.trim().equals(p.getMagic()) || Strings.isNullOrEmpty(text)) &&
            isTheLastParagraph) {
      note.addParagraph();
    }

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    note.persist(userProfile.getUserName()); //p.setText(text) will update lastUpdated time,so note must updated too,not only paragraph
    try {
      note.run(paragraphId);
    } catch (Exception ex) {
      LOG.error("Exception from run", ex);
      if (p != null) {
        p.setReturn(
                new InterpreterResult(InterpreterResult.Code.ERROR, ex.getMessage()),
                ex);
        p.setStatus(Status.ERROR);
        broadcast(note.getId(), new Message(OP.PARAGRAPH).put("paragraph", p));
      }
    }
  }

  private void sendAllConfigurations(NotebookSocket conn, Subject subject,
                                     Notebook notebook) throws IOException {
    ZeppelinConfiguration conf = notebook.getConf();

    Map<String, String> configurations = conf.dumpConfigurations(conf,
            new ZeppelinConfiguration.ConfigurationKeyPredicate() {
              @Override
              public boolean apply(String key) {
                return !key.contains("password") &&
                        !key.equals(ZeppelinConfiguration
                                .ConfVars
                                .ZEPPELIN_NOTEBOOK_AZURE_CONNECTION_STRING
                                .getVarName());
              }
            });

    conn.send(serializeMessage(new Message(OP.CONFIGURATIONS_INFO)
            .put("configurations", configurations)));
  }

  private void checkpointNotebook(NotebookSocket conn, Notebook notebook,
                                  Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String commitMessage = (String) fromMessage.get("commitMessage");
    String group = fromMessage.group;

    if (group == null || group.isEmpty()) {
      throw new IllegalArgumentException("group is null");
    }

    Subject subject = TicketContainer.instance.getCachedSubject(fromMessage.ticket, fromMessage.principal);

    //是否有commit权限
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isCommitter(subject, group, noteId)) {
      permissionError(conn, subject, "保存草稿", group, noteId);//即：类似git的commit，提交一个版本，UI设计时叫"保存草稿"
      return;
    }

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    Revision revision = notebook.checkpointNote(noteId, commitMessage, userProfile.getUserName());
    if (revision != null) {
      List<Revision> revisions = notebook.listRevisionHistory(noteId, userProfile.getUserName());
      conn.send(serializeMessage(new Message(OP.LIST_REVISION_HISTORY).put("revisionList", revisions)));
    } else {//如果note没有更新，通知前台
      conn.send(serializeMessage(new Message(OP.NO_CHANGE_FOUND).put("info", "算法没有修改")));
    }
  }

  /**
   * 提交note的revision到组委会，根据提交策略，限制提交次数
   * TODO:前端是否将"提交到组委会"label中显示目前允许提交的次数
   */
  private void submitNotebook(NotebookSocket conn, Notebook notebook,
                              Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String revisionId = (String) fromMessage.get("revisionId");
    String group = fromMessage.group;

    if (group == null || group.isEmpty()) {
      throw new IllegalArgumentException("group is null");
    }

    Subject subject = TicketContainer.instance.getCachedSubject(fromMessage.ticket, fromMessage.principal);

    //是否有submit权限
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isSubmitter(subject, group, noteId)) {
      UserProfile userProfile = (UserProfile) (subject.getPrincipal());
      LOG.warn("{} is not submitter of group:{}, note:{}", userProfile.getUserName(), group, noteId);
      return;
    }

    SubmitLeftOver submitLeftOver = null;
    try {
      submitLeftOver = notebook.submit(noteId, revisionId);
    } catch (Exception e) {
      if (e instanceof SubmitStrategyVolationException) {
        conn.send(serializeMessage(new Message(OP.REVISION_SUBMIT).put("submitLeftOver", e.getMessage())));
      }

      return;
    }

    if (submitLeftOver == null) {//已经提交了
      conn.send(serializeMessage(new Message(OP.REVISION_SUBMIT).put("submitLeftOver", "该版本已经提交过了")));
      return;
    }

    conn.send(serializeMessage(new Message(OP.REVISION_SUBMIT).put("submitLeftOver", submitLeftOver.toString())));
  }

  /**
   * 查询已经提交的次数
   */
  private void currentSubmitTimes(NotebookSocket conn, Notebook notebook,
                                  Message fromMessage) throws IOException {
    String group = fromMessage.group;
    String projectId = fromMessage.projectId;

    if (group == null || group.isEmpty()) {
      throw new IllegalArgumentException("group is null");
    }

    int submitTimes = notebook.currentSubmitTimes(group, projectId);
    conn.send(serializeMessage(new Message(OP.ACK_SUBMIT_TIME).put("info", submitTimes)));//TODO：前端需要处理这个OP=ACK_SUBMIT_TIME，并且binding submitLeftOver，显示指定时间内还剩余提交次数
  }

  private void listRevisionHistory(NotebookSocket conn, Notebook notebook,
                                   Message fromMessage) throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    Subject subject = TicketContainer.instance.getCachedSubject(fromMessage.ticket, fromMessage.principal);
    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    List<Revision> revisions = notebook.listRevisionHistory(noteId, userProfile.getUserName());

    conn.send(serializeMessage(new Message(OP.LIST_REVISION_HISTORY)
            .put("revisionList", revisions)));
  }

  private void getNoteByRevision(NotebookSocket conn, Notebook notebook, Message fromMessage)
          throws IOException {
    String noteId = (String) fromMessage.get("noteId");
    String revisionId = (String) fromMessage.get("revisionId");
    String group = (String) fromMessage.get("group");

    if (group == null || group.isEmpty()) {
      throw new IllegalArgumentException("group is null");
    }

    Subject subject = TicketContainer.instance.getCachedSubject(fromMessage.ticket, fromMessage.principal);

    //是否有commit权限
    NotebookAuthorizationAdaptor notebookAuthorization = notebook.getNotebookAuthorization();
    if (!notebookAuthorization.isCommitter(subject, group, noteId)) {
      permissionError(conn, subject, "查看历史版本", group, noteId);//即：类似git的commit，提交一个版本，UI设计时叫"查看历史版本"
      return;
    }

    UserProfile userProfile = (UserProfile) (subject.getPrincipal());
    Note revisionNote = notebook.getNoteByRevision(noteId, revisionId, userProfile.getUserName());
    conn.send(serializeMessage(new Message(OP.NOTE_REVISION)
            .put("noteId", noteId)
            .put("revisionId", revisionId)
            .put("data", revisionNote)));
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer
   *
   * @param output output to append
   */
  @Override
  public void onOutputAppend(String noteId, String paragraphId, String output) {
    Message msg = new Message(OP.PARAGRAPH_APPEND_OUTPUT)
            .put("noteId", noteId)
            .put("paragraphId", paragraphId)
            .put("data", output);
    broadcast(noteId, msg);
  }

  /**
   * This callback is for the paragraph that runs on ZeppelinServer
   *
   * @param output output to update (replace)
   */
  @Override
  public void onOutputUpdated(String noteId, String paragraphId, String output) {
    Message msg = new Message(OP.PARAGRAPH_UPDATE_OUTPUT)
            .put("noteId", noteId)
            .put("paragraphId", paragraphId)
            .put("data", output);
    broadcast(noteId, msg);
  }

  /**
   * When application append output
   */
  @Override
  public void onOutputAppend(String noteId, String paragraphId, String appId, String output) {
    Message msg = new Message(OP.APP_APPEND_OUTPUT)
            .put("noteId", noteId)
            .put("paragraphId", paragraphId)
            .put("appId", appId)
            .put("data", output);
    broadcast(noteId, msg);
  }

  /**
   * When application update output
   */
  @Override
  public void onOutputUpdated(String noteId, String paragraphId, String appId, String output) {
    Message msg = new Message(OP.APP_UPDATE_OUTPUT)
            .put("noteId", noteId)
            .put("paragraphId", paragraphId)
            .put("appId", appId)
            .put("data", output);
    broadcast(noteId, msg);
  }

  @Override
  public void onLoad(String noteId, String paragraphId, String appId, HeliumPackage pkg) {
    Message msg = new Message(OP.APP_LOAD)
            .put("noteId", noteId)
            .put("paragraphId", paragraphId)
            .put("appId", appId)
            .put("pkg", pkg);
    broadcast(noteId, msg);
  }

  @Override
  public void onStatusChange(String noteId, String paragraphId, String appId, String status) {
    Message msg = new Message(OP.APP_STATUS_CHANGE)
            .put("noteId", noteId)
            .put("paragraphId", paragraphId)
            .put("appId", appId)
            .put("status", status);
    broadcast(noteId, msg);
  }

  /**
   * Notebook Information Change event
   */
  public static class NotebookInformationListener implements NotebookEventListener {
    private NotebookServer notebookServer;

    public NotebookInformationListener(NotebookServer notebookServer) {
      this.notebookServer = notebookServer;
    }

    @Override
    public void onParagraphRemove(Paragraph p) {
      try {
        notebookServer.broadcastUpdateNotebookJobInfo(System.currentTimeMillis() - 5000);
      } catch (IOException ioe) {
        LOG.error("can not broadcast for job manager {}", ioe.getMessage());
      }
    }

    @Override
    public void onNoteRemove(Note note) {
      try {
        notebookServer.broadcastUpdateNotebookJobInfo(System.currentTimeMillis() - 5000);
      } catch (IOException ioe) {
        LOG.error("can not broadcast for job manager {}", ioe.getMessage());
      }

      List<Map<String, Object>> notesInfo = new LinkedList<>();
      Map<String, Object> info = new HashMap<>();
      info.put("notebookId", note.getId());
      // set paragraphs
      List<Map<String, Object>> paragraphsInfo = new LinkedList<>();

      // notebook json object root information.
      info.put("isRunningJob", false);
      info.put("unixTimeLastRun", 0);
      info.put("isRemoved", true);
      info.put("paragraphs", paragraphsInfo);
      notesInfo.add(info);

      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notesInfo);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
              new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));

    }

    @Override
    public void onParagraphCreate(Paragraph p) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListByParagraphId(
              p.getId()
      );
      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
              new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));
    }

    @Override
    public void onNoteCreate(Note note) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListBymNotebookId(
              note.getId()
      );
      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
              new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));
    }

    @Override
    public void onParagraphStatusChange(Paragraph p, Status status) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListByParagraphId(
              p.getId()
      );

      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
              new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));
    }

    @Override
    public void onUnbindInterpreter(Note note, InterpreterSetting setting) {
      Notebook notebook = notebookServer.notebook();
      List<Map<String, Object>> notebookJobs = notebook.getJobListBymNotebookId(
              note.getId()
      );
      Map<String, Object> response = new HashMap<>();
      response.put("lastResponseUnixTime", System.currentTimeMillis());
      response.put("jobs", notebookJobs);

      notebookServer.broadcast(JOB_MANAGER_SERVICE.JOB_MANAGER_PAGE.getKey(),
              new Message(OP.LIST_UPDATE_NOTEBOOK_JOBS).put("notebookRunningJobs", response));
    }
  }

  /**
   * Need description here.
   */
  public static class ParagraphListenerImpl implements ParagraphJobListener {
    private NotebookServer notebookServer;
    private Note note;

    public ParagraphListenerImpl(NotebookServer notebookServer, Note note) {
      this.notebookServer = notebookServer;
      this.note = note;
    }

    @Override
    public void onProgressUpdate(Job job, int progress) {
      notebookServer.broadcast(
              note.getId(),
              new Message(OP.PROGRESS).put("id", job.getId()).put("progress",
                      job.progress()));
    }

    @Override
    public void beforeStatusChange(Job job, Status before, Status after) {
    }

    @Override
    public void afterStatusChange(Job job, Status before, Status after) {
      if (after == Status.ERROR) {
        if (job.getException() != null) {
          LOG.error("Error", job.getException());
        }
      }

      if (job.isTerminated()) {
        LOG.info("Job {} is finished", job.getId());
        try {
          //TODO(khalid): may change interface for JobListener and pass subject from interpreter
          note.persist(null);
        } catch (IOException e) {
          LOG.error(e.toString(), e);
        }
      }
      notebookServer.broadcastNote(note);

      try {
        notebookServer.broadcastUpdateNotebookJobInfo(System.currentTimeMillis() - 5000);
      } catch (IOException e) {
        LOG.error("can not broadcast for job manager {}", e);
      }
    }

    /**
     * This callback is for praragraph that runs on RemoteInterpreterProcess
     */
    @Override
    public void onOutputAppend(Paragraph paragraph, InterpreterOutput out, String output) {
      Message msg = new Message(OP.PARAGRAPH_APPEND_OUTPUT)
              .put("noteId", paragraph.getNote().getId())
              .put("paragraphId", paragraph.getId())
              .put("data", output);

      notebookServer.broadcast(paragraph.getNote().getId(), msg);
    }

    /**
     * This callback is for paragraph that runs on RemoteInterpreterProcess
     */
    @Override
    public void onOutputUpdate(Paragraph paragraph, InterpreterOutput out, String output) {
      Message msg = new Message(OP.PARAGRAPH_UPDATE_OUTPUT)
              .put("noteId", paragraph.getNote().getId())
              .put("paragraphId", paragraph.getId())
              .put("data", output);

      notebookServer.broadcast(paragraph.getNote().getId(), msg);
    }

  }

  @Override
  public ParagraphJobListener getParagraphJobListener(Note note) {
    return new ParagraphListenerImpl(this, note);
  }

  public NotebookEventListener getNotebookInformationListener() {
    return new NotebookInformationListener(this);
  }

  private void sendAllAngularObjects(Note note, NotebookSocket conn) throws IOException {
    List<InterpreterSetting> settings =
            notebook().getInterpreterFactory().getInterpreterSettings(note.getId());
    if (settings == null || settings.size() == 0) {
      return;
    }

    for (InterpreterSetting intpSetting : settings) {
      AngularObjectRegistry registry = intpSetting.getInterpreterGroup(note.getId())
              .getAngularObjectRegistry();
      List<AngularObject> objects = registry.getAllWithGlobal(note.getId());
      for (AngularObject object : objects) {
        conn.send(serializeMessage(new Message(OP.ANGULAR_OBJECT_UPDATE)
                .put("angularObject", object)
                .put("interpreterGroupId",
                        intpSetting.getInterpreterGroup(note.getId()).getId())
                .put("noteId", note.getId())
                .put("paragraphId", object.getParagraphId())
        ));
      }
    }
  }

  @Override
  public void onAdd(String interpreterGroupId, AngularObject object) {
    onUpdate(interpreterGroupId, object);
  }

  @Override
  public void onUpdate(String interpreterGroupId, AngularObject object) {
    Notebook notebook = notebook();
    if (notebook == null) {
      return;
    }

    List<Note> notes = notebook.getAllNotes();
    for (Note note : notes) {
      if (object.getNoteId() != null && !note.getId().equals(object.getNoteId())) {
        continue;
      }

      List<InterpreterSetting> intpSettings = notebook.getInterpreterFactory()
              .getInterpreterSettings(note.getId());
      if (intpSettings.isEmpty()) {
        continue;
      }

      broadcast(
              note.getId(),
              new Message(OP.ANGULAR_OBJECT_UPDATE)
                      .put("angularObject", object)
                      .put("interpreterGroupId", interpreterGroupId)
                      .put("noteId", note.getId())
                      .put("paragraphId", object.getParagraphId()));
    }
  }

  @Override
  public void onRemove(String interpreterGroupId, String name, String noteId, String paragraphId) {
    Notebook notebook = notebook();
    List<Note> notes = notebook.getAllNotes();
    for (Note note : notes) {
      if (noteId != null && !note.getId().equals(noteId)) {
        continue;
      }

      List<String> ids = notebook.getInterpreterFactory().getInterpreters(note.getId());
      for (String id : ids) {
        if (id.equals(interpreterGroupId)) {
          broadcast(
                  note.getId(),
                  new Message(OP.ANGULAR_OBJECT_REMOVE).put("name", name).put(
                          "noteId", noteId).put("paragraphId", paragraphId));
        }
      }
    }
  }
}

