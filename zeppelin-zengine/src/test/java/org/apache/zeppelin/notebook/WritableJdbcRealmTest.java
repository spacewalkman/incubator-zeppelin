package org.apache.zeppelin.notebook;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.*;

public class WritableJdbcRealmTest {
  static WritableJdbcRealm writableJdbcRealm;

  static RealmSecurityManager realmSecurityManager;

  static final String RealmName = ZeppelinConfiguration.create().getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SHIRO_REALM_NAME);

  @BeforeClass
  public static void init() {
    IniSecurityManagerFactory factory = new IniSecurityManagerFactory("classpath:shiro.ini");
    SecurityManager securityManager = factory.getInstance();
    realmSecurityManager = (RealmSecurityManager) securityManager;
    Collection<Realm> realms = realmSecurityManager.getRealms();
    for (Realm realm : realms) {
      if (realm.getName().equals(RealmName)) {
        writableJdbcRealm = (WritableJdbcRealm) realm;
        break;
      }
    }
  }

  private Subject buildNewSubject(String principal, String realmName) {
    PrincipalCollection principals = new SimplePrincipalCollection(principal, realmName);
    return new Subject.Builder(realmSecurityManager).principals(principals).buildSubject();
  }

  @Test
  public void createUser() throws Exception {
    writableJdbcRealm.createUser("user1", "123");

    boolean isUserExsit = writableJdbcRealm.isUserExist("user1");
    assertTrue(isUserExsit);

    //测试登录
    Subject subject = buildNewSubject("user1", RealmName);
    Subject subjectAfter = realmSecurityManager.login(subject, new UsernamePasswordToken("user1", "123"));//传递给login的password不能hash
    assertTrue(subjectAfter.isAuthenticated());

    int affectedCount = writableJdbcRealm.deleteUser("user1");
    assertTrue(affectedCount >= 1);

    isUserExsit = writableJdbcRealm.isUserExist("user1");
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
    writableJdbcRealm.assignRoleToUser(userName, templateReaderRole);

    boolean isExist = writableJdbcRealm.isRoleExistForUser(userName, templateReaderRole);
    assertTrue(isExist);
  }

  /**
   * 测试单独建立template_reader role和permission插入
   */
  @Test
  public void assignPermissionToRole() throws Exception {
    final String templateReaderRole = "template_reader";
    writableJdbcRealm.assignPermissionToRole(templateReaderRole, "note:reader:2BZJE92ZD,2BVBBJYAV");

    boolean isExist = writableJdbcRealm.isRoleExist(templateReaderRole);
    assertTrue(isExist);
  }

  @Test
  public void getUsersForGroup() throws Exception {

  }

  @Test
  public void isRoleExistForUser() throws Exception {

  }

}