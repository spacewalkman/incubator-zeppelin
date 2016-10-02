package org.apache.zeppelin.notebook;

import org.apache.commons.lang.NotImplementedException;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * shiro-based authorization,use userDAO
 */
public class ShiroNotebookAuthorization extends NotebookAuthorizationAdaptor {
  private static final Logger LOG = LoggerFactory.getLogger(ShiroNotebookAuthorization.class);

  /**
   * 提供可读写的权限
   */
  private UserDAO userDAO;

  //TODO:需要缓存下来吗?
  public ShiroNotebookAuthorization(
          ZeppelinConfiguration conf) throws PropertyVetoException, IOException, SQLException {
    this.userDAO = new UserDAO(conf);
  }

  @Override
  public boolean isGroupMember(Subject subject, String groupId) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    if (null == groupId || groupId.isEmpty()) {
      groupId = "*";
    }

    //group_leader同样是group_member
    return subject.hasRole(String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId));
  }

  //group_leader=group_member +  group_submitter,避免为group_leader设置单独的role
  @Override
  public boolean isGroupLeader(Subject subject, String groupId) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    if (null == groupId || groupId.isEmpty()) {
      groupId = "*";
    }


    return subject.hasRole(String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId)) && subject.hasRole(String.format(GROUP_SUBMITTER_ROLE_NAME_FORMAT, groupId));
  }

  /**
   * 判断是否能提交一个note的revision到组委会
   */
  public boolean isSubmitter(Subject subject, String groupId,
                             String noteId) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    return subject.isPermitted(String.format(NOTE_GROUP_SUBMITTER_PERMISSION_FORMAT, groupId, noteId));
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

    return subject.isPermitted(String.format(NOTE_GROUP_COMMITTER_PERMISSION_FORMAT, groupId, noteId));
  }

  @Override
  public boolean isReader(Subject subject, String groupId, String noteId) {
    return doAuthenticate(subject, groupId, String.format(NOTE_READER_PERMISSION_FORMAT, noteId), String.format(NOTE_GROUP_READER_PERMISSION_FORMAT, groupId, noteId));
  }

  @Override
  public boolean isWriter(Subject subject, String groupId, String noteId) {
    return doAuthenticate(subject, groupId, String.format(NOTE_WRITER_PERMISSION_FORMAT, noteId), String.format(NOTE_GROUP_WRITER_PERMISSION_FORMAT, groupId, noteId));
  }

  @Override
  public boolean isOwner(Subject subject, String groupId, String noteId) {
    return doAuthenticate(subject, groupId, String.format(NOTE_OWNER_PERMISSION_FORMAT, noteId), String.format(NOTE_GROUP_OWNER_PERMISSION_FORMAT, groupId, noteId));
  }

  @Override
  public boolean isExecutor(Subject subject, String groupId, String noteId) {
    return doAuthenticate(subject, groupId, String.format(NOTE_EXECUTOR_PERMISSION_FORMAT, noteId), String.format(NOTE_GROUP_EXECUTOR_PERMISSION_FORMAT, groupId, noteId));
  }

  /**
   * 判断subject是否具有某种权限
   *
   * @param subject                      当前subject
   * @param groupId                      组id
   * @param withoutGroupPermissionFormat 权限是3维的，groupId字段为空，直接控制到note级别
   * @param withGroupPermissionFormat    权限是4维的，groupId字段不为空，控制到组级别
   */
  private boolean doAuthenticate(Subject subject, String groupId,
                                 final String withoutGroupPermissionFormat,
                                 final String withGroupPermissionFormat) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    if (groupId == null || groupId.isEmpty()) {//同上，用来控制"模板"权限
      return subject.isPermitted(withoutGroupPermissionFormat);
    }

    return subject.isPermitted(withGroupPermissionFormat);
  }

  @Override
  public boolean isAdmin(Subject subject) {
    if (!subject.isAuthenticated()) {
      return false;
    }

    return subject.hasRole(ROLE_ADMIN);
  }


  //TODO:unique userName and roleName
  @Override
  public void addGroupMember(String groupId, String userName) {
    if (!userDAO.isUserExist(userName)) {
      LOG.warn("user: " + userName + " doesn't exist!");
      return;
    }

    userDAO.assignRoleToUser(userName, String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId));
  }

  /**
   * 为用户添加"队长"角色
   *
   * @param groupId  队id
   * @param userName 用户名
   */
  @Override
  public void addGroupLeader(String groupId, String userName) {
    userDAO.assignRoleToUser(userName, String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId), String.format(GROUP_SUBMITTER_ROLE_NAME_FORMAT, groupId));
  }

  /**
   * 单独添加参赛队,实际上创建了2个角色,一个是队员,一个是队长角色,并为这2个角色设置权限
   *
   * @param groupId 队id
   */
  @Override
  public void addGroup(String groupId) {
    //添加note sumbmitter role
    userDAO.assignPermissionToRole(String.format(GROUP_SUBMITTER_ROLE_NAME_FORMAT, groupId), String.format(NOTE_GROUP_SUBMITTER_PERMISSION_FORMAT, groupId, "*"));

    //添加队员的permissions到role
    userDAO.assignPermissionToRole(String.format(GROUP_MEMBER_ROLE_NAME_FORMAT, groupId), String.format(GROUP_MEMBER_PERMISSIONS_FORMAT, GROUP_MEMBER_PERMISSIONS, groupId, "*"));
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
      userDAO.assignRoleToUser(userName, roleName);
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
      userDAO.assignPermissionToRole(roleName, permission);
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
    return userDAO.getUsersForGroup(String.format("group_%%_%s", groupId));//TODO:转义是否生效;是否使用mysql索引,然后采用<>来比较,走索引会比较快呢?
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
    return userDAO.isRoleExistForUser(userName, String.format(INTERPRETER_ROLE_NAME_FORMAT, interpreterId));
  }
}
