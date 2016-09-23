package org.apache.zeppelin.notebook;

import org.apache.commons.lang.NotImplementedException;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * shiro-based authorization,use writableJdbcRealm
 */
public class ShiroNotebookAuthorization extends NotebookAuthorizationAdaptor {
  private static final Logger LOG = LoggerFactory.getLogger(ShiroNotebookAuthorization.class);

  /**
   * 提供可读写的权限
   */
  private WritableJdbcRealm writableJdbcRealm;

  //TODO:需要缓存下来吗?
  public ShiroNotebookAuthorization(ZeppelinConfiguration conf) {
    //TODO:handle cast error
    RealmSecurityManager realmSecurityManager = (RealmSecurityManager) org.apache.shiro.SecurityUtils.getSecurityManager();
    Collection<Realm> realms = realmSecurityManager.getRealms();
    for (Realm realm : realms) {
      if (realm.getName().equals(conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SHIRO_REALM_NAME))) {
        writableJdbcRealm = (WritableJdbcRealm) realm;
        break;
      }
    }

    if (writableJdbcRealm == null) {
      LOG.error("writableJdbcRealm is null");
    }
  }

  @Override
  public boolean isGroupMember(Subject subject, String groupId) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    if (null == groupId || groupId.isEmpty()) {
      groupId = "*";
    }

    return subject.hasRole(String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId));
  }

  @Override
  public boolean isGroupLeader(Subject subject, String groupId) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    if (null == groupId || groupId.isEmpty()) {
      groupId = "*";
    }

    return subject.hasRole(String.format(GROUP_LEADER_ROLE_NAME_FORMAT, groupId));
  }

  /**
   * 判断是否能提交一个note的revision到组委会
   */
  public boolean isSubmitter(Subject subject, String groupId,
                             String noteId) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    return subject.isPermitted(String.format(NOTE_SUBMIT_PERMISSION_FORMAT, groupId, noteId));
  }


  /**
   * 判断是否能为一个note提交版本
   */
  @Override
  public boolean isCommitter(Subject subject, String groupId,
                             String noteId) { //注意：这里的noteid是无业务含义的，以groupId为前缀，是为了给group_member_XXX这样的role授权的时候（其中XXX为groupId)，采用wildcard note:*:XXX:*来表示，避免逐一列举
    if (!subject.isAuthenticated()) {
      return false;
    }

    return subject.isPermitted(String.format(NOTE_COMMITTER_PERMISSION_FORMAT, groupId, noteId));
  }

  @Override
  public boolean isAdmin(Subject subject) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    return subject.isPermitted(ROLE_ADMIN);
  }


  //TODO:unique userName and roleName
  @Override
  public void addGroupMember(String groupId, String userName) {
    if (!writableJdbcRealm.isUserExist(userName)) {
      LOG.warn("user: " + userName + " doesn't exist!");
      return;
    }

    writableJdbcRealm.assignRoleToUser(userName, String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId));
  }

  /**
   * 为用户添加"队长"角色
   *
   * @param groupId  队id
   * @param userName 用户名
   */
  @Override
  public void addGroupLeader(String groupId, String userName) {
    writableJdbcRealm.assignRoleToUser(userName, String.format(GROUP_LEADER_PERMISSION_FORMAT, groupId));
  }

  /**
   * 单独添加参赛队,实际上创建了2个角色,一个是队员,一个是队长角色,并为这2个角色设置权限
   *
   * @param groupId 队id
   */
  @Override
  public void addGroup(String groupId) {
    //添加队长的permission到role
    writableJdbcRealm.assignPermissionToRole(String.format(GROUP_LEADER_ROLE_NAME_FORMAT, groupId), String.format(GROUP_LEADER_PERMISSION_FORMAT, groupId));

    //添加队员的permissions到role
    for (String memberPermissionFormat : GROUP_MEMBER_PERMISSION_FORMATS) {
      writableJdbcRealm.assignPermissionToRole(String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId), String.format(memberPermissionFormat, groupId));
    }
  }

  /**
   * 为用户授角色
   *
   * @param userName  用户名
   * @param roleNames 角色名列表
   */
  @Override
  public void grantRolesToUser(String userName, String... roleNames) {
    for (String roleName : roleNames) {
      writableJdbcRealm.assignRoleToUser(userName, roleName);
    }
  }

  /**
   * 为角色授权限
   *
   * @param roleName    角色名
   * @param permissions 权限列表
   */
  @Override
  public void grantPermissionsToRole(String roleName, String... permissions) {
    for (String permission : permissions) {
      writableJdbcRealm.assignPermissionToRole(roleName, permission);
    }
  }

  /**
   * 查询一个参赛队所有的队员
   *
   * @param groupId 队id
   * @return 队员username列表
   */
  @Override
  public List<String> getUsersForGroup(String groupId) {
    return writableJdbcRealm.getUsersForGroup(String.format("group_%%_%s", groupId));//TODO:转义是否生效;是否使用mysql索引,然后采用<>来比较,走索引会比较快呢?
  }

  @Override
  public void addOwner(String noteId, String userName) {
    throw new NotImplementedException();//TODO:实现
  }

  /**
   * 判断某个用户是否有使用形如interpreter_user_%s这样的role name,该格式由常量INTERPRETER_ROLE_NAME_FORMAT定义
   */
  @Override
  public boolean canUseInterpreter(final String userName, final String interpreterId) {
    //对jdbcRealm来讲,就是判断某人是否有指定的角色
    return writableJdbcRealm.isRoleExistForUser(userName, String.format(INTERPRETER_ROLE_NAME_FORMAT, interpreterId));
  }
}
