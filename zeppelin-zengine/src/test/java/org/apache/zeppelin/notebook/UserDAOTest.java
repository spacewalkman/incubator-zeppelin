package org.apache.zeppelin.notebook;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
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

  static RealmSecurityManager realmSecurityManager;

  static ZeppelinConfiguration conf = ZeppelinConfiguration.create();

  static final String RealmName = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SHIRO_REALM_NAME);

  @BeforeClass
  public static void init() throws PropertyVetoException, SQLException, IOException {
    IniSecurityManagerFactory factory = new IniSecurityManagerFactory("classpath:shiro.ini");
    SecurityManager securityManager = factory.getInstance();
    realmSecurityManager = (RealmSecurityManager) securityManager;

    userDAO = new UserDAO(conf);
  }

  private Subject buildNewSubject(String principal, String realmName) {
    PrincipalCollection principals = new SimplePrincipalCollection(principal, realmName);
    return new Subject.Builder(realmSecurityManager).principals(principals).buildSubject();
  }

  /**
   * 分析组内部所有的账户名字+IP监控项目用户
   */
  final String[] all_users = {"qianyong", "duqiang", "wangyuda", "fanyeliang", "duchangtai", "fengyan", "fumingzhu",
          "gongjuntai", "jianglinhui", "mayunlong", "ouyangfeng", "xiefen", "yangzhenyong", "yaunli", "zhangmeiqi",
          "zhangrongyu", "zhangshu", "zhaolei", "zhouyuanyuan", "zuojun", "goupan", "shiyang", "wangyanfeng", "zhouchao1", "chenhonghong3", "user1"};


  /**
   * 初始化所有的测试账户
   */
  @Test
  public void initUsers() throws Exception {
    for (int i = 0; i < all_users.length; i++) {
      userDAO.createUser(all_users[i], "123", new HashedCredentialsMatcher("SHA-256"));

      boolean isUserExsit = userDAO.isUserExist(all_users[i]);
      assertTrue(isUserExsit);

      //测试登录
      Subject subject = buildNewSubject(all_users[i], RealmName);
      Subject subjectAfter = realmSecurityManager.login(subject, new UsernamePasswordToken(all_users[i], "123"));//传递给login的password不能hash
      assertTrue(subjectAfter.isAuthenticated());
    }
  }

  @Test
  public void createUser() throws Exception {
    userDAO.createUser("user1", "123", new HashedCredentialsMatcher("SHA-256"));

    boolean isUserExsit = userDAO.isUserExist("user1");
    assertTrue(isUserExsit);

    //测试登录
    Subject subject = buildNewSubject("user1", RealmName);
    Subject subjectAfter = realmSecurityManager.login(subject, new UsernamePasswordToken("user1", "123"));//传递给login的password不能hash
    assertTrue(subjectAfter.isAuthenticated());

    int affectedCount = userDAO.deleteUser("user1");
    assertTrue(affectedCount >= 1);

    isUserExsit = userDAO.isUserExist("user1");
    assertFalse(isUserExsit);

    subjectAfter.logout();
    assertFalse(subjectAfter.isAuthenticated());

  }

  @Test
  public void getNewConnection() throws Exception {

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