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
    writableJdbcRealm.createUser("qianyong", "123");

    boolean isUserExsit = writableJdbcRealm.isUserExist("qianyong");
    assertTrue(isUserExsit);

    //测试登录
    Subject subject = buildNewSubject("qianyong", RealmName);
    Subject subjectAfter = realmSecurityManager.login(subject, new UsernamePasswordToken("qianyong", "123"));//传递给login的password不能hash
    assertTrue(subjectAfter.isAuthenticated());

    int affectedCount = writableJdbcRealm.deleteUser("qianyong");
    assertTrue(affectedCount >= 1);

    isUserExsit = writableJdbcRealm.isUserExist("qianyong");
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

  }

  @Test
  public void assignPermissionToRole() throws Exception {

  }

  @Test
  public void getUsersForGroup() throws Exception {

  }

  @Test
  public void isRoleExistForUser() throws Exception {

  }

}