package org.apache.zeppelin.notebook;

import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserDAOTest {
  static UserDAO userDAO;

  @BeforeClass
  public static void init() throws PropertyVetoException, SQLException, IOException {
    userDAO = new UserDAO(ZeppelinConfiguration.create());
  }


  @Test
  public void createUser() throws Exception {
    userDAO.createUser("user1", "123", new HashedCredentialsMatcher("SHA-256"));

    boolean isUserExsit = userDAO.isUserExist("user1");
    assertTrue(isUserExsit);

    int affectedCount = userDAO.deleteUser("user1");
    assertTrue(affectedCount >= 1);

    isUserExsit = userDAO.isUserExist("user1");
    assertFalse(isUserExsit);
  }

  @Test
  public void isUserExist() throws Exception {

  }

  @Test
  public void isRoleExist() throws Exception {

  }

  @Test
  public void assignRoleToUser() throws Exception {
    final String templateReaderRole = "template_reader";
    final String userName = "wangyuda";
    userDAO.assignRoleToUser(userName, templateReaderRole);

    boolean isExist = userDAO.isRoleExistForUser(userName, templateReaderRole);
    assertTrue(isExist);
  }

  /**
   * 测试单独建立template_reader role和permission插入
   */
  @Test
  public void assignPermissionToRole() throws Exception {
    final String templateReaderRole = "template_reader";
    userDAO.assignPermissionToRole(templateReaderRole, "note:reader:2BZJE92ZD,2BVBBJYAV");

    boolean isExist = userDAO.isRoleExist(templateReaderRole);
    assertTrue(isExist);
  }

  @Test
  public void getUsersForGroup() throws Exception {

  }

  @Test
  public void isRoleExistForUser() throws Exception {

  }

}