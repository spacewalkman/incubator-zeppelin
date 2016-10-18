package org.apache.zeppelin.notebook.repo;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.repo.commit.SubmitLeftOver;
import org.apache.zeppelin.notebook.repo.commit.SubmitStrategy;
import org.apache.zeppelin.notebook.repo.commit.SubmitStrategyFactory;
import org.apache.zeppelin.notebook.repo.commit.SubmitStrategyVolationException;
import org.apache.zeppelin.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;

/**
 * 将Note存储到RDBMS库中
 */
public class JdbcNotebookRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(JdbcNotebookRepo.class);

  //TODO:上次修改时间，上次提交时间，每个组每个队今天提交了几次，总共提交了几次？
  private static final String NOTE_TABLE_NAME = "note";
  private static final String INSERT_SQL = "insert into " + NOTE_TABLE_NAME + "(id,createdBy,projectId,team,content,last_updated_time) values (?,?,?,?,?,?)";
  private static final String UPDATE_SQL = "update " + NOTE_TABLE_NAME + " set content=?,last_updated_time=? where id=?";//group和projectId不会被更新
  private static final String SEARCH_BY_USER_SQL = "select id,createdBy,projectId,team,content,last_updated_time from " + NOTE_TABLE_NAME + " where createdBy=?";
  private static final String GET_ALL_SQL = "select id,createdBy,projectId,team,content,last_updated_time from " + NOTE_TABLE_NAME;
  private static final String REMOVE_SQL = "delete from " + NOTE_TABLE_NAME + " where id=?";
  private static final String GET_BY_ID_SQL = "select id,createdBy,projectId,team,content,last_updated_time from " + NOTE_TABLE_NAME + " where id=?";

  public static final String NOTE_REVISION_TABLE_NAME = "note_revision"; //revisionId字段采用auto_increment实现
  //采用dbms存储note时，不区分note的author和committer，只存储committer字段
  private static final String REVISION_INSERT_SQL = "insert into " + NOTE_REVISION_TABLE_NAME + "(noteId,note_name,committer,team,projectId,message,commit_date,content,sha1) values (?,?,?,?,?,?,?,?,?)";
  private static final String GET_REVISION_BY_ID = "select id,noteId,note_name,committer,team,projectId,message,commit_date,content,sha1 FROM " + NOTE_REVISION_TABLE_NAME + " where id=?";
  private static final String GET_REVISION_CONTENT_BY_ID = "select content from " + NOTE_REVISION_TABLE_NAME + " where id=? and noteId=? and committer=?";
  private static final String SELECT_REVISION_BY_NOTE_ID = "select id,note_name,committer,message,commit_date,team,projectId,is_submit from " + NOTE_REVISION_TABLE_NAME + " where noteId=? and committer=? order by commit_date desc";
  private static final String SET_SUBMITTED_FLAG_SQL = "update " + NOTE_REVISION_TABLE_NAME + " set is_submit=true where id=? and (is_submit is null OR is_submit=false)";//限制is_submit=false，避免重复submit同一个revision占用提交次数
  private static final String SELECT_LAST_NOTE_CONTENT_SQL = "select n1.sha1 from " + NOTE_REVISION_TABLE_NAME + " n1 where n1.noteId=? and n1.commit_date in (select max(n2.commit_date) from " + NOTE_REVISION_TABLE_NAME + " n2 where n2.noteId=?)";

  /**
   * 查询一段时间内一个参赛队为一个赛题提交次数sql
   */
  public static final String SELECT_TEAM_COMMIT_TIMES_IN_RANGE = "select count(*) FROM " + JdbcNotebookRepo.NOTE_REVISION_TABLE_NAME + " where team=? and projectId=? and is_submit=true and commit_date >=? and commit_date <?";

  /**
   * connection pooling机制
   */
  private NotebookDataSource notebookDataSource;

  /**
   * 计算note json的SHA1值的hash函数
   */
  private HashFunction hashFunction;

  /**
   * 提交次数限制策略
   */
  private SubmitStrategy submitStrategy;//TODO:promote到NotebookRepo中

  private ZeppelinConfiguration conf;

  public JdbcNotebookRepo(ZeppelinConfiguration conf) throws Exception {
    this.conf = conf;
    notebookDataSource = NotebookDataSource.getInstance(conf);
    hashFunction = Hashing.sha1();
  }

  public SubmitStrategy getSubmitStrategy() {
    if (submitStrategy == null) {
      SubmitStrategyFactory submitStrategyFactory = SubmitStrategyFactory.getInstance(conf);
      submitStrategy = submitStrategyFactory.create();
    }

    return submitStrategy;
  }

  /**
   * 算法(note)列表
   *
   * @param principal 当前用户，如果不为null，则按照note.createdBy=principal的条件过滤，否则，不过滤，返回所有的note
   */
  @Override
  public List<NoteInfo> list(String principal) throws IOException {
    List<NoteInfo> resultNotes = new LinkedList<>();

    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;
    try {
      connection = notebookDataSource.getConnection();
      if (principal == null || principal.isEmpty()) {
        ps = connection.prepareStatement(GET_ALL_SQL);
      } else {
        ps = connection.prepareStatement(SEARCH_BY_USER_SQL);
        ps.setString(1, principal);
      }

      rs = ps.executeQuery();
      while (rs.next()) {
        //id,createdBy,projectId,team,content
        String id = rs.getString(1);
        String createdBy = rs.getString(2);
        String projectId = rs.getString(3);
        String team = rs.getString(4);
        String jsonContent = rs.getString(5);

        Note note = GsonUtil.fromJson(jsonContent, Note.class);
        note.setCreatedBy(createdBy);
        note.setProjectId(projectId);
        note.setGroup(team);

        resultNotes.add(new NoteInfo(note));
      }
    } catch (SQLException e) {
      LOG.error("error when list note!", e);
    } finally {
      closeAll(ps, rs, connection);
    }

    return resultNotes;
  }

  @Override
  public Note get(String noteId, String principal) throws IOException {//TODO:目前principal没有验证
    Note note = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;
    try {
      connection = notebookDataSource.getConnection();
      ps = connection.prepareStatement(GET_BY_ID_SQL);
      ps.setString(1, noteId);

      // Execute query
      rs = ps.executeQuery();
      if (rs.next()) {
        String id = rs.getString(1);
        String createdBy = rs.getString(2);
        String projectId = rs.getString(3);
        String team = rs.getString(4);
        String jsonContent = rs.getString(5);
        note = GsonUtil.fromJson(jsonContent, Note.class);
        note.setCreatedBy(createdBy);
        note.setProjectId(projectId);
        note.setGroup(team);
      }
    } catch (SQLException e) {
      LOG.error("error when get note!", e);
    } finally {
      closeAll(ps, rs, connection);
    }

    return note;
  }

  @Override
  public void save(Note note, String principal) throws IOException {
    if (note.getId() == null) {
      throw new IllegalArgumentException("note id is null");
    }

    if (note.getLastUpdated() == null) {
      LOG.warn("note:'" + note.getId() + "' 's lastUpdated is null,skip save!");
      return;
    }

    boolean isExist = isNoteExists(note.getId(), principal);
    PreparedStatement ps = null;
    Connection connection = null;

    String noteJson = GsonUtil.toJson(note);

    try {
      connection = notebookDataSource.getConnection();
      if (isExist) {
        ps = connection.prepareStatement(UPDATE_SQL);
        ps.setString(1, noteJson);
        ps.setTimestamp(2, new Timestamp(note.getLastUpdated().getTime()));
        ps.setString(3, note.getId());
      } else {
        ps = connection.prepareStatement(INSERT_SQL);
        ps.setString(1, note.getId());
        ps.setString(2, note.getCreatedBy());
        ps.setString(3, note.getProjectId());
        ps.setString(4, note.getGroup());
        ps.setString(5, noteJson);
        ps.setTimestamp(6, new Timestamp(note.getLastUpdated().getTime()));
      }

      int result = ps.executeUpdate();
      LOG.debug("saved {}", result == 1 ? "success" : "failed");
    } catch (SQLException e) {
      LOG.error("error when save note!", e);
    } finally {
      closeAll(ps, null, connection);
    }
  }

  /**
   * 查询note是否存在
   */
  private boolean isNoteExists(String noteId, String createdBy) {//TODO:目前createdBy没有参与验证唯一性
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;
    try {
      connection = notebookDataSource.getConnection();
      ps = connection.prepareStatement(GET_BY_ID_SQL);
      ps.setString(1, noteId);

      rs = ps.executeQuery();
      if (rs.next()) {
        return true;
      }
    } catch (SQLException e) {
      LOG.error("error when get note by id:'{}'", noteId);
    } finally {
      closeAll(ps, rs, connection);
    }

    return false;
  }

  @Override
  public void remove(String noteId, String principal) throws IOException {
    PreparedStatement ps = null;
    Connection connection = null;
    try {
      connection = notebookDataSource.getConnection();
      ps = connection.prepareStatement(REMOVE_SQL);
      ps.setString(1, noteId);

      int result = ps.executeUpdate();
      LOG.debug("remove note noteId {} {}", noteId, result == 1 ? "success" : "failed");
    } catch (SQLException e) {
      LOG.error("error when save note!", e);
    } finally {
      closeAll(ps, null, connection);
    }
  }

  @Override
  public void close() {
    this.notebookDataSource.close();
  }

  /**
   * 采用mysql做版本控制，方便实现提交次数的限制
   *
   * @param note          note
   * @param checkpointMsg message description of the checkpoint
   */
  @Override
  public Revision checkpoint(Note note, String checkpointMsg,
                             String principal) throws IOException {
    if (note == null) {
      throw new IllegalArgumentException("note is null");
    }
    if (note.getId() == null || note.getId().isEmpty()) {
      throw new IllegalArgumentException("note id is null");
    }

    if (principal == null || principal.isEmpty()) {
      throw new IllegalArgumentException("principal is empty");
    }

    Revision revision = null;
    PreparedStatement ps = null;
    Connection connection = null;

    //比较存储在dbms中的上一次的sha1和当前noted json的sha1
    String lastSha1 = this.getNoteLastSha1(note.getId());
    String noteJson = GsonUtil.toJson(note);
    HashCode hashCode = hashFunction.hashString(noteJson, Charset.forName("UTF-8"));
    String hash = hashCode.toString();
    if (hash.equals(lastSha1)) {
      LOG.warn("no change found,checkpoint just return");
      return revision;
    }

    try {
      connection = notebookDataSource.getConnection();
      //noteId,committer,team,projectId,message,commit_date,content
      ps = connection.prepareStatement(REVISION_INSERT_SQL, Statement.RETURN_GENERATED_KEYS);//返回自动生成的note_revision表的id字段，该字段为auto_increment类型的
      ps.setString(1, note.getId());
      String committer = principal;
      ps.setString(2, note.getName());
      ps.setString(3, committer);
      ps.setString(4, note.getGroup());
      ps.setString(5, note.getProjectId());
      ps.setString(6, checkpointMsg);
      long currentTimeMs = System.currentTimeMillis();
      ps.setTimestamp(7, new Timestamp(currentTimeMs));
      ps.setString(8, noteJson);//TODO:浪费一次，get出来应该可以直接返回note的json字符串的
      ps.setString(9, hash);

      int result = ps.executeUpdate();
      LOG.debug("checkpoint note noteId {} {}", note.getId(), result == 1 ? "success" : "failed");

      int revisionId = -1;
      ResultSet autoKeyRS = ps.getGeneratedKeys();
      if (autoKeyRS.next()) {
        revisionId = autoKeyRS.getInt(1);
      } else {
        throw new IOException("no auto_increment revId returned!");
      }
      revision = new Revision(revisionId + "", note.getId(), note.getName(), checkpointMsg, currentTimeMs,
              principal, principal, note.getGroup(), note.getProjectId());
    } catch (SQLException e) {
      LOG.error("error when checkpoint note!", e);
    } finally {
      closeAll(ps, null, connection);
    }

    return revision;
  }

  @Override
  public Note get(String noteId, String revId, String principal) throws IOException {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Note note = null;
    Connection connection = null;
    try {
      connection = notebookDataSource.getConnection();
      ps = connection.prepareStatement(GET_REVISION_CONTENT_BY_ID); //TODO:这里noteId与revId是重复的，与git不同，dbms存储版本，是独立的，没有working copy
      ps.setString(1, revId);
      ps.setString(2, noteId);
      ps.setString(3, principal);

      rs = ps.executeQuery();
      if (rs.next()) {
        String content = rs.getString(1);
        note = GsonUtil.fromJson(content, Note.class);
      }
    } catch (SQLException e) {
      LOG.error("error when get note by revId:'{}' and noteId:'{}'", revId, noteId);
    } finally {
      closeAll(ps, rs, connection);
    }

    return note;
  }

  /**
   * 获取一个指定版本的Note revision
   *
   * @param revId revision的id
   * @return 该版本的revision
   */
  public Revision getRevision(String noteId, String revId) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;
    Revision revision = null;

    try {
      connection = notebookDataSource.getConnection();
      ps = connection.prepareStatement(GET_REVISION_BY_ID);
      ps.setString(1, revId);

      rs = ps.executeQuery();
      if (rs.next()) {
        //id,noteId,note_name,committer,team,projectId,message,commit_date,content,sha1
        int id = rs.getInt(1);
        String noteID = rs.getString(2);
        if (!noteID.equals(noteId)) {
          LOG.error("getRevison should return the same noteId as argument,expected:{},but actual:{}", noteId, noteID);
          return null;
        }
        String noteName = rs.getString(3);
        String committer = rs.getString(4);
        String team = rs.getString(5);
        String projectId = rs.getString(6);
        String message = rs.getString(7);
        Timestamp commitTime = rs.getTimestamp(8);
        String sha1 = rs.getString(9);

        revision = new Revision(id + "", noteId, noteName, message, commitTime.getTime(), "", committer, team, projectId);
        revision.sha1 = sha1;
      }
    } catch (SQLException e) {
      LOG.error("error when get note revision by revId:'{}'", revId);
    } finally {
      closeAll(ps, rs, connection);
    }

    return revision;
  }

  private void closeAll(PreparedStatement ps, ResultSet rs, Connection connection) {
    if (ps != null) {
      try {
        ps.close();
      } catch (SQLException e) {
        LOG.error("error when close statement!", e);
      }
    }

    if (rs != null) {
      try {
        rs.close();
      } catch (SQLException e) {
        LOG.error("error when close resultSet!", e);
      }
    }

    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        LOG.error("error when close connection!", e);
      }
    }
  }

  @Override
  public List<Revision> revisionHistory(String noteId, String principal) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;

    List<Revision> revisionList = new LinkedList<>();

    try {
      connection = notebookDataSource.getConnection();
      ps = connection.prepareStatement(SELECT_REVISION_BY_NOTE_ID);
      ps.setString(1, noteId);
      ps.setString(2, principal);

      rs = ps.executeQuery();

      while (rs.next()) {
        int id = rs.getInt(1);
        String noteName = rs.getString(2);
        String committer = rs.getString(3);
        String message = rs.getString(4);
        Timestamp commitDate = rs.getTimestamp(5);
        String team = rs.getString(6);
        String projectId = rs.getString(7);
        boolean isSubmit = rs.getBoolean(8);//TODO：如何显示是否已经提交组委会
        Revision revision = new Revision(id + "", noteId, noteName, message, commitDate.getTime(), committer, committer, team, projectId, isSubmit);//基于dbms的存储，无法区分commit的author和committer，除非做 行级别的代码与用户之间的对应关系
        revisionList.add(revision);
      }
    } catch (SQLException e) {
      LOG.error("error when get note revisionHistory by noteId:'{}'", noteId);
    } finally {
      closeAll(ps, rs, connection);
    }

    return revisionList;
  }


  /**
   * 获取一个note的最近一次提交的sha1 hash值
   *
   * @param noteId note's id
   * @return last sha1
   */
  private String getNoteLastSha1(String noteId) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;
    String result = null;

    try {
      connection = notebookDataSource.getConnection();
      ps = connection.prepareStatement(SELECT_LAST_NOTE_CONTENT_SQL);
      ps.setString(1, noteId);
      ps.setString(2, noteId);

      rs = ps.executeQuery();

      if (rs.next()) {
        result = rs.getString(1);
      }
    } catch (SQLException e) {
      LOG.error("error when get note last sha1 by noteId:'{}'", noteId);
    } finally {
      closeAll(ps, rs, connection);
    }

    return result;
  }


  /**
   * 将具体的revision提交到组委会,具体实现逻辑：
   * <li>在Revision上打个标记，表示该note的这个版本已经提交到组委会</li>
   * <li>根据限制提交次数的策略，控制一个参赛队在一段时间内容的提交次数</li>
   *
   * @param noteId     当前提交的note id，在dbms中由于revisionId是主键，故没有使用noteid
   * @param revisionId 待提交的版本
   * @return 为null，表示该revision已经提交过了;不为null，返回SubmitLeftOver POJO表示按照目前的提交策略，剩余的提交次数限制
   * @throws Exception 违反提交次数限制的异常
   */
  @Override
  public SubmitLeftOver submit(String noteId,
                               String revisionId) throws SubmitStrategyVolationException {
    if (revisionId == null) {
      throw new IllegalArgumentException("revision is null");
    }

    Revision revision = this.getRevision(noteId, revisionId);
    if (revision == null) {
      throw new IllegalArgumentException("revision is null");
    }

    if (revision.team == null || revision.team.isEmpty()) {
      throw new IllegalArgumentException("revision's team is null");
    }
    if (revision.projectId == null || revision.projectId.isEmpty()) {
      throw new IllegalArgumentException("revision's projectId is null");
    }

    int currentTimes = this.queryCurrentSubmitTimes(revision.team, revision.projectId);
    final int maxTimes = this.getSubmitStrategy().getMaxTime();
    LOG.debug("team='{}',groupId='{}',当前已经提交次数:{},允许提交次数:{}", revision.team, revision.projectId, currentTimes, maxTimes);
    //提交之前检查是否超过允许提交次数
    if (currentTimes >= maxTimes) {
      throw new SubmitStrategyVolationException("超过提交次数限制");
    }

    int affected = this.doSubmit(revisionId);
    if (affected == 1) {
      return new SubmitLeftOver(revision.team, revision.projectId, currentTimes + 1, maxTimes, this.getSubmitStrategy().getTypeName());
    } else {//该版本已经submit过了
      return null;
    }
  }

  /**
   * 查询当前队伍对当前题目还被允许提交的次数
   *
   * @param team      队伍
   * @param projectId 赛题
   * @return 已经提交的次数
   */
  @Override
  public SubmitLeftOver currentSubmitLeftTimes(String team, String projectId) {
    final int maxTimes = this.getSubmitStrategy().getMaxTime();
    final int currentTimes = this.queryCurrentSubmitTimes(team, projectId);

    return new SubmitLeftOver(team, projectId, currentTimes, maxTimes, this.getSubmitStrategy().getTypeName());
  }

  /**
   * update该revesion的"是否已经提交"的标志位为true
   */
  private int doSubmit(String revisionId) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;
    int result = -1;

    try {
      NotebookDataSource dataSource = NotebookDataSource.getInstance(conf);

      connection = dataSource.getConnection();
      ps = connection.prepareStatement(SET_SUBMITTED_FLAG_SQL);
      ps.setString(1, revisionId);

      result = ps.executeUpdate();
      LOG.debug("set submitted flag {}", result == 1 ? "success" : "failed");
    } catch (Exception e) {
      LOG.error("error when select max commit time!", e);
    } finally {
      this.closeAll(ps, rs, connection);
    }

    return result;
  }

  /**
   * 查询当前该参赛队、该题目，已经提交了多少次了
   */
  private int queryCurrentSubmitTimes(String team, String projectId) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;

    int result = -1;
    long[] timeRanges = this.getSubmitStrategy().getTimeRange();
    try {
      NotebookDataSource dataSource = NotebookDataSource.getInstance(conf);

      connection = dataSource.getConnection();
      ps = connection.prepareStatement(SELECT_TEAM_COMMIT_TIMES_IN_RANGE);
      ps.setString(1, team);
      ps.setString(2, projectId);
      ps.setTimestamp(3, new Timestamp(timeRanges[0]));
      ps.setTimestamp(4, new Timestamp(timeRanges[1]));

      rs = ps.executeQuery();
      if (rs.next()) {
        result = rs.getInt(1);
      }
    } catch (Exception e) {
      LOG.error("error when select max commit time!", e);
    } finally {
      this.closeAll(ps, rs, connection);
    }

    return result;
  }
}
