package org.apache.zeppelin.notebook;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ShiroNotebookAuthorizationTest extends AbstractShiroTest {

  ShiroNotebookAuthorization authorization;

  @Test
  public void testSimple() {

    //1.  Create a mock authenticated Subject instance for the test to run:
    Subject subjectUnderTest = mock(Subject.class);
    when(subjectUnderTest.isAuthenticated()).thenReturn(true);

    //2. Bind the subject to the current thread:
    setSubject(subjectUnderTest);

    //perform test logic here.  Any call to
    //SecurityUtils.getSubject() directly (or nested in the
    //call stack) will work properly.
  }

  @After
  public void tearDownSubject() {
    //3. Unbind the subject from the current thread:
    clearSubject();
  }

  static WritableJdbcRealm writableJdbcRealm;

  static RealmSecurityManager realmSecurityManager;

  static Subject subject;

  static final String RealmName = ZeppelinConfiguration.create().getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SHIRO_REALM_NAME);

  @BeforeClass
  public static void init() {
    IniSecurityManagerFactory factory = new IniSecurityManagerFactory("classpath:shiro.ini");
    SecurityManager securityManager = factory.getInstance();
    SecurityUtils.setSecurityManager(securityManager);
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


  @Before
  public void setUp() {
    subject = buildNewSubject("qianyong", RealmName);
    authorization = new ShiroNotebookAuthorization(ZeppelinConfiguration.create());
  }

  @Test
  public void isGroupMember() throws Exception {
    subject.login(new UsernamePasswordToken("qianyong", "123"));
    assertTrue(authorization.isGroupMember(subject, "qianyong"));
  }

  @Test
  public void isGroupLeader() throws Exception {
    subject.login(new UsernamePasswordToken("qianyong", "123"));
    assertTrue(authorization.isGroupLeader(subject, "qianyong"));
  }

  @Test
  public void isSubmitter() throws Exception {
    subject.login(new UsernamePasswordToken("wangyuda", "123"));
    assertTrue(authorization.isSubmitter(subject, "wangyuda", "2BWUDX72Y"));
  }

  @Test
  public void isCommitter() throws Exception {
    subject.login(new UsernamePasswordToken("qianyong", "123"));
    assertTrue(authorization.isCommitter(subject, "qianyong", "2BWUDX72Y"));
  }

  @Test
  public void isReader() throws Exception {
    subject.login(new UsernamePasswordToken("qianyong", "123"));
    assertTrue(authorization.isReader(subject, "qianyong", "2BWUDX72Y"));
  }

  @Test
  public void isWriter() throws Exception {

  }

  @Test
  public void isOwner() throws Exception {

  }

  @Test
  public void isAdmin() throws Exception {

  }

  @Test
  public void addGroupMember() throws Exception {

  }

  @Test
  public void addGroupLeader() throws Exception {

  }

  @Test
  public void addGroup() throws Exception {
    authorization.addGroup("wangyuda");
    authorization.addGroupLeader("wangyuda", "wangyuda");

    List<String> members = authorization.getUsersForGroup("wangyuda");
    assertNotNull(members);
    assertEquals(members.get(0), "wangyuda");

  }

  @Test
  public void grantRolesToUser() throws Exception {

  }

  @Test
  public void grantPermissionsToRole() throws Exception {

  }

  @Test
  public void addOwner() throws Exception {

  }

}