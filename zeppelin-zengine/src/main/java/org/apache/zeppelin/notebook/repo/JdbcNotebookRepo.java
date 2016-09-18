package org.apache.zeppelin.notebook.repo;

import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * 将Note存储到RDBMS库中
 */
public class JdbcNotebookRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(JdbcNotebookRepo.class);

  private Connection connection;

  private static final String JDBC_URL_FORMAT = "jdbc:mysql://%s:%d/%s?user=%s&password=%s&useSSL=false";

  private static final String INSERT_SQL = "insert into note(id,createdBy,projectId,team,content) values (?,?,?,?,?)";
  private static final String SEARCH_BY_USER_SQL = "select id,createdBy,projectId,team,content from note where createdBy=?";
  private static final String REMOVE_SQL = "delete from note where id=?";
  private static final String GET_BY_ID_SQL = "select id,createdBy,projectId,team,content from note where id=?";

  public JdbcNotebookRepo(ZeppelinConfiguration conf) throws SQLException {
    String driver = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_DRIVER);
    String host = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_HOST);
    int port = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_PORT);
    String database = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_DATABASE);
    String userName = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_USER_NAME);
    String password = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_PASSWORD);

    try {
      Class.forName(driver);
    } catch (ClassNotFoundException e) {
      LOG.error("{} class not found!", driver);
    }

    connection = DriverManager.getConnection(String.format(JDBC_URL_FORMAT, host, port, database, userName, password));
  }

  @Override
  public List<NoteInfo> list(Subject subject) throws IOException {
    List<NoteInfo> resultNotes = new LinkedList<>();

    try {
      PreparedStatement ps = connection.prepareStatement(SEARCH_BY_USER_SQL);
      ps.setString(1, (String) (subject.getPrincipal()));

      // Execute query
      ResultSet rs = ps.executeQuery();
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

      rs.close();
    } catch (SQLException e) {
      LOG.error("error when load note! {}", e);
    }

    return resultNotes;
  }

  @Override
  public Note get(String noteId, Subject subject) throws IOException {
    Note note = null;
    try {
      PreparedStatement ps = connection.prepareStatement(GET_BY_ID_SQL);
      ps.setString(1, noteId);

      // Execute query
      ResultSet rs = ps.executeQuery();
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

      rs.close();
    } catch (SQLException e) {
      LOG.error("error when get note! {}", e);
    }

    return note;
  }

  @Override
  public void save(Note note, Subject subject) throws IOException {
    try {
      PreparedStatement ps = connection.prepareStatement(INSERT_SQL);
      //id,createdBy,projectId,team,content
      ps.setString(1, note.getId());
      ps.setString(2, note.getCreatedBy());
      ps.setString(3, note.getProjectId());
      ps.setString(4, note.getGroup());
      ps.setString(5, GsonUtil.toJson(note));

      int result = ps.executeUpdate();
      LOG.debug("saved {}", result == 1 ? "success" : "failed");
    } catch (SQLException e) {
      LOG.error("error when save note! {}", e);
    }
  }

  @Override
  public void remove(String noteId, Subject subject) throws IOException {
    try {
      PreparedStatement ps = connection.prepareStatement(REMOVE_SQL);
      //id,createdBy,projectId,team,content
      ps.setString(1, noteId);

      int result = ps.executeUpdate();
      LOG.debug("remove note noteId {} {}", noteId, result == 1 ? "success" : "failed");
    } catch (SQLException e) {
      LOG.error("error when save note! {}", e);
    }
  }

  @Override
  public void close() {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException ex) {
        LOG.error("error when close connection!,{}", ex);
      }
    }
  }

  @Override
  public Revision checkpoint(String noteId, String checkpointMsg,
                             Subject subject) throws IOException {
    return null;
  }

  @Override
  public Note get(String noteId, String revId, Subject subject) throws IOException {
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, Subject subject) {
    return null;
  }
}
