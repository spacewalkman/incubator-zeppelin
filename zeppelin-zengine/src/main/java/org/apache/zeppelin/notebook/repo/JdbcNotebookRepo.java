package org.apache.zeppelin.notebook.repo;

import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * 将Note存储到RDBMS库中
 */
public class JdbcNoteRepo implements NotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(JdbcNoteRepo.class);

  private Connection connection;

  private static final String INSERT_SQL = "insert into note(id,createdBy,projectId,team,content) values (?,?,?,?,?)";
  private static final String SEARCH_BY_USER_SQL = "select id,createdBy,projectId,team,content from note where createdBy=?";
  private static final String REMOVE_SQL = "delete from note where id=?";
  private static final String GET_BY_ID_SQL = "select id,createdBy,projectId,team,content from note where id=?";

  public JdbcNoteRepo(ZeppelinConfiguration conf) throws Exception {
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

    try {
      connection =
              DriverManager.getConnection(String.format("jdbc:mysql://%s:%d/%s/user=%s&password=%s", host, port, database, userName, password);
    } catch (SQLException ex) {
      LOG.error("error when get a connection! {}", ex);
    }
  }

  @Override
  public List<NoteInfo> list(Subject subject) throws IOException {
    return null;
  }

  @Override
  public Note get(String noteId, Subject subject) throws IOException {
    return null;
  }

  @Override
  public void save(Note note, Subject subject) throws IOException {

  }

  @Override
  public void remove(String noteId, Subject subject) throws IOException {

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
