package org.apache.zeppelin.realm;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.JdbcUtils;
import org.apache.zeppelin.ticket.TicketUserNameToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * add CRUD capability to JdbcRealm,which can be used By zeppelin
 */
public class WritableJdbcRealm extends JdbcRealm {//TODO: 处理schema中的主外键约束

  private static final Logger LOG = LoggerFactory.getLogger(WritableJdbcRealm.class);

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(
          AuthenticationToken token) throws AuthenticationException {
    return null;
  }

  /**
   * 覆写以避免返回principal被强制转换成String，实际上是UserProfile
   */
  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    //null usernames are invalid
    if (principals == null) {
      throw new AuthorizationException("PrincipalCollection method argument cannot be null.");
    }

    UserProfile userProfile = (UserProfile) getAvailablePrincipal(principals);
    String username = userProfile.getUserName();

    Connection conn = null;
    Set<String> roleNames = null;
    Set<String> permissions = null;
    try {
      conn = dataSource.getConnection();

      // Retrieve roles and permissions from database
      roleNames = getRoleNamesForUser(conn, username);
      if (permissionsLookupEnabled) {
        permissions = getPermissions(conn, username, roleNames);
      }

    } catch (SQLException e) {
      final String message = "There was a SQL error while authorizing user [" + username + "]";
      if (LOG.isErrorEnabled()) {
        LOG.error(message, e);
      }

      // Rethrow any SQL errors as an authorization exception
      throw new AuthorizationException(message, e);
    } finally {
      JdbcUtils.closeConnection(conn);
    }

    SimpleAuthorizationInfo info = new SimpleAuthorizationInfo(roleNames);
    info.setStringPermissions(permissions);
    return info;
  }

  /**
   * 表明该realm不参与用户身份鉴别
   * TODO：如果是原生zeppelin引用，应该删除这段代码
   */
  @Override
  public boolean supports(AuthenticationToken token) {
    if (token instanceof TicketUserNameToken) {
      return true;
    }

    return false;
  }
}
