package org.apache.zeppelin.notebook.repo;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import org.apache.zeppelin.conf.ZeppelinConfiguration;

import java.beans.PropertyVetoException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

/**
 * singleton解决Datasource的唯一实例问题，供JdbcNotebookRepo使用，解决connection pooling的问题
 */
public class NotebookDataSource {
  private static final String JDBC_URL_FORMAT = "jdbc:%s://%s:%d/%s?useSSL=false";
  private volatile static NotebookDataSource datasource;
  private ComboPooledDataSource ds;

  private static final int DEFAULT_MAX_POOL_SIZE = 10;
  private static final int DEFAULT_MIN_POOL_SIZE = 3;

  private NotebookDataSource(
          ZeppelinConfiguration conf) throws PropertyVetoException {
    String dbType = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_DB_TYPE);
    String driver = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_DRIVER);
    String host = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_HOST);
    int port = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_PORT);
    String database = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_DATABASE);
    String userName = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_USER_NAME);
    String password = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_PASSWORD);

    int minPoolSize = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_MIN_POOL_SIZE);
    if (minPoolSize < 0) {
      minPoolSize = DEFAULT_MIN_POOL_SIZE;
    }
    int maxPoolSize = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_JDBC_MAX_POOL_SIZE);
    if (maxPoolSize < 0) {
      maxPoolSize = DEFAULT_MAX_POOL_SIZE;
    }

    if (maxPoolSize < minPoolSize) {
      maxPoolSize = minPoolSize;
    }

    ds = new ComboPooledDataSource();
    ds.setDriverClass(driver);
    ds.setUser(userName);
    ds.setPassword(password);
    //ds.setAutoCommitOnClose(true);
    ds.setMaxPoolSize(maxPoolSize);
    ds.setMinPoolSize(minPoolSize);
    ds.setJdbcUrl(String.format(JDBC_URL_FORMAT, dbType, host, port, database));
  }

  public static NotebookDataSource getInstance(
          ZeppelinConfiguration conf) throws PropertyVetoException {
    if (datasource == null) {
      synchronized (NotebookDataSource.class) {
        if (datasource == null) {
          datasource = new NotebookDataSource(conf);
        }
      }
    }
    return datasource;
  }


  public Connection getConnection() throws SQLException {
    return this.ds.getConnection();
  }

  public DataSource getDataSource() {
    return this.ds;
  }


  public void close() {
    this.ds.close();
  }
}
