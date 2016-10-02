package org.apache.zeppelin.notebook;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.JdbcUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.repo.NotebookDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

/**
 * add CRUD capability to JdbcRealm,which can be used By zeppelin
 */
public class UserDAO {

  private static final Logger LOG = LoggerFactory.getLogger(UserDAO.class);

  /**
   * is user exist
   */
  public static final String IS_USER_EXSIT_SQL = "select * from user where user_name = ?";

  /**
   * delete by user name，which is uniqe
   */
  public static final String DELETE_USER_EXSIT_SQL = "delete from user where user_name = ?";

  /**
   * is role exist
   */
  public static final String IS_ROLE_EXSIT_SQL = "select * from ROLE_PERMISSION where role_name = ?";

  /**
   * select all users for a group
   */
  public static final String SELECT_USER_FOR_GROUP_SQL = "select user_name from USER_ROLE where role_name like ?";//TODO:可以用外键关联吗?


  /**
   * is user has role
   */
  public static final String IS_USE_HAS_ROLE_SQL = "select * from USER_ROLE where user_name=? and role_name=?";

  /**
   * add role to user default sql, demo schema as below:
   * TODO: schema
   */
  public static final String INSERT_ROLE_TO_USER_SQL = "insert into USER_ROLE(user_name,role_name) values (?,?)";

  /**
   * add new user sql
   */
  public static final String INSERT_USER_SQL = "insert into USER(user_name,password) values (?,?)";

  /**
   * 创建角色，并给角色授权permission
   */
  public static final String INSERT_PERMISSION_TO_ROLE_SQL = "insert into ROLE_PERMISSION(role_name,permission) values (?,?)";

  private DataSource dataSource;

  public UserDAO(
          ZeppelinConfiguration conf) throws PropertyVetoException, SQLException, IOException {
    this.dataSource = NotebookDataSource.getInstance(conf).getDataSource();
  }

  /**
   * add new userName if not exist
   * TODO:创建队伍的时候使用
   *
   * @param userName username
   * @param password user password,not hashed yet
   * @credentialsMatcher shrio realm用到的处理密码加密之后匹配的工具类
   */
  public void createUser(String userName, String password,
                         HashedCredentialsMatcher hashedCredentialsMatcher) {
    if (isUserExist(userName)) {
      LOG.warn("user: " + userName + " , already exist,ignore add request");
      return;
    }

    SimpleHash hash = new SimpleHash(hashedCredentialsMatcher.getHashAlgorithmName(), password, null, 1024);
    final String hashedPassword = hash.toBase64();
    LOG.debug("hash之后的byte长度{},\n{}", hashedPassword.length(), hashedPassword);
    // hashedPassword = new Sha256Hash(password, userName).getBytes();

    PreparedStatement ps = null;
    Connection connection = null;

    try {
      connection = this.dataSource.getConnection();
      ps = connection.prepareStatement(INSERT_USER_SQL);
      ps.setString(1, userName);
      ps.setString(2, hashedPassword);//与shiro.ini配置文件中的sha256Matcher.storedCredentialsHexEncoded = false对应

      int count = ps.executeUpdate();
      if (count < 1) {
        throw new AuthenticationException("can't add userName: " + userName);
      }

    } catch (SQLException e) {
      final String message = "There was a SQL error while add userName [" + userName + "]";
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }
      // Rethrow any SQL errors as an authentication exception
      throw new AuthenticationException(message, e);
    } finally {
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }
  }

  /**
   * 用户是否存在
   */
  public boolean isUserExist(String userName) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;

    try {
      connection = this.dataSource.getConnection();
      ps = connection.prepareStatement(IS_USER_EXSIT_SQL);
      ps.setString(1, userName);

      // Execute query
      rs = ps.executeQuery();

      // Loop over results - although we are only expecting one result, since usernames should be unique
      boolean foundResult = false;
      while (rs.next()) {

        // Check to ensure only one row is processed
        if (foundResult) {
          throw new AuthenticationException("More than one user row found for user [" + userName + "]. Usernames must be unique.");
        }

        foundResult = true;
      }

      return foundResult;
    } catch (SQLException e) {
      final String message = "There was a SQL error while judge user: [" + userName + "] exist";
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }
      throw new AuthenticationException(message, e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }
  }

  /**
   * 删除指定的user
   *
   * @param userName 用户名
   * @return 删除的行数
   */
  public int deleteUser(String userName) {
    PreparedStatement ps = null;
    Connection connection = null;

    try {
      connection = this.dataSource.getConnection();
      ps = connection.prepareStatement(DELETE_USER_EXSIT_SQL);
      ps.setString(1, userName);
      return ps.executeUpdate();

    } catch (SQLException e) {
      final String message = "There was a SQL error while delete user: [" + userName + "]";
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }
      throw new AuthenticationException(message, e);
    } finally {
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }
  }

  /**
   * 判断role是否已经存在
   */
  public boolean isRoleExist(String roleName) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;

    try {
      connection = this.dataSource.getConnection();
      ps = connection.prepareStatement(IS_ROLE_EXSIT_SQL);
      ps.setString(1, roleName);

      // Execute query
      rs = ps.executeQuery();

      // Loop over results - although we are only expecting one result, since usernames should be unique
      boolean foundResult = false;
      while (rs.next()) {

        // Check to ensure only one row is processed
        if (foundResult) {
          throw new AuthenticationException("More than one user row found for user [" + roleName + "]. role name must be unique.");
        }

        foundResult = true;
      }

      return foundResult;
    } catch (SQLException e) {
      final String message = "There was a SQL error while judge role: [" + roleName + "] exist";
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }
      throw new AuthenticationException(message, e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }
  }

  /**
   * assign roles to user(transactional)
   *
   * @param user  user name
   * @param roles role names
   */
  public void assignRoleToUser(String user, String... roles) throws AuthenticationException {
    PreparedStatement ps = null;
    Connection connection = null;

    try {
      connection = this.dataSource.getConnection();
      connection.setAutoCommit(false);

      ps = dataSource.getConnection().prepareStatement(INSERT_ROLE_TO_USER_SQL);
      for (String role : roles) {
        ps.setString(1, user);
        ps.setString(2, role);

        ps.addBatch();
      }

      int[] counts = ps.executeBatch();

      connection.commit();
    } catch (SQLException e) {
      final String message = "There was a SQL error while assign role to user [" + user + "]";
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }

      try {
        connection.rollback();
      } catch (SQLException e1) {
        LOG.error("error rolling back transaction", e);
      }
      // Rethrow any SQL errors as an authentication exception
      throw new AuthenticationException(message, e);
    } finally {
      try {
        connection.setAutoCommit(true);
      } catch (SQLException e) {
        LOG.error("error reset to auto commit ", e);
      }
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }
  }


  /**
   * assign permission to role
   * TODO:创建队伍的时候使用
   *
   * @param permission permission,which is confront to Shiro WildcardPermission
   * @param role       role name
   */
  public void assignPermissionToRole(String role, String permission) {
    PreparedStatement ps = null;
    Connection connection = null;

    try {
      connection = dataSource.getConnection();
      ps = connection.prepareStatement(INSERT_PERMISSION_TO_ROLE_SQL);
      ps.setString(1, role);
      ps.setString(2, permission);

      int count = ps.executeUpdate();
      if (count < 1) {
        throw new AuthenticationException("can't assign permission: " + permission + " to role: " + role);
      }
    } catch (SQLException e) {
      final String message = "There was a SQL error while assign permission: " + permission + " to role: " + role;
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }
      // Rethrow any SQL errors as an authzentication exception
      throw new AuthenticationException(message, e);
    } finally {
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }
  }

  /**
   * 查询属于同一个参赛队的所有队员
   *
   * @param groupPermissionPrefix 队伍权限名,例如group_member_groupX
   * @return 队员的username集合
   */
  public List<String> getUsersForGroup(final String groupPermissionPrefix) {
    PreparedStatement ps = null;
    List<String> userNames = new ArrayList<String>();
    Connection connection = null;
    ResultSet rs = null;

    try {
      connection = dataSource.getConnection();
      ps = connection.prepareStatement(SELECT_USER_FOR_GROUP_SQL);
      ps.setString(1, groupPermissionPrefix);

      // Execute query
      rs = ps.executeQuery();
      while (rs.next()) {
        userNames.add(rs.getString(1));
      }
      rs.close();
    } catch (SQLException e) {
      final String message = "There was a SQL error while find users for group: " + groupPermissionPrefix;
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }
      throw new AuthenticationException(message, e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }

    return userNames;
  }

  /**
   * is user has a specific role
   *
   * @param userName user'name
   * @param roleName role name
   * @return is user has a specific role or not
   */
  public boolean isRoleExistForUser(final String userName, final String roleName) {
    PreparedStatement ps = null;
    ResultSet rs = null;
    Connection connection = null;

    try {
      connection = dataSource.getConnection();
      ps = connection.prepareStatement(IS_USE_HAS_ROLE_SQL);
      ps.setString(1, userName);
      ps.setString(2, roleName);

      rs = ps.executeQuery();
      if (rs.next()) {
        return true;
      }

      return false;
    } catch (SQLException e) {
      final String message = "There was a SQL error while judge role: [" + roleName + "] exist";
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }
      throw new AuthenticationException(message, e);
    } finally {
      JdbcUtils.closeResultSet(rs);
      JdbcUtils.closeStatement(ps);
      JdbcUtils.closeConnection(connection);
    }
  }


}
