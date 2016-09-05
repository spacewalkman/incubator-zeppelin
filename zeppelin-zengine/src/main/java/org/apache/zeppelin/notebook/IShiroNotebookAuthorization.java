package org.apache.zeppelin.notebook;

import org.apache.shiro.subject.Subject;

import java.util.List;

/**
 * 基于shiro WildcardPermission的权限验证,只到参赛队级别
 */
public interface IShiroNotebookAuthorization {

  /**
   * 参赛队队长的role名字,占位符为队id,如果队id叫abc,那么该队的队长的role的名称为group_leader_abc
   */
  String GROUP_LEADER_ROLE_NAME_FORMAT = "group_leader_%s";

  /**
   * 队长的权限,占位符为形如groupX_*,noteId的schema对应为groupId_noteId
   */
  String GROUP_LEADER_PERMISSION_FORMAT = "note:*:%s";

  /**
   * 某个note可读,占位符为具体的noteId，noteId的schema对应为groupId_noteId
   */
  String NOTE_READER_PERMISSION_FORMAT = "note:readers:%s";

  /**
   * 某个note可写,占位符为具体的noteId，noteId的schema对应为groupId_noteId
   */
  String NOTE_WRITER_PERMISSION_FORMAT = "note:writers:%s";

  /**
   * 某个note的所有者,占位符为具体的noteId，noteId的schema对应为groupId_noteId
   */
  String NOTE_OWNER_PERMISSION_FORMAT = "note:owners:%s";

  /**
   * 某个note可以提交版本历史,占位符为具体的noteId，noteId的schema对应为groupId_noteId
   */
  String NOTE_COMMITTER_PERMISSION_FORMAT = "note:committer:%s";

  /**
   * 向组委会提交notes的权利,该权利由队长独有,队员不具备,占位符是note的id,如果要单独控制per-note的submit权限，可以使用。目前暂时未使用
   */
  String NOTE_SUBMIT_PERMISSION_FORMAT = "note:submitters:%s";

  /**
   * 队员的权限列表,可以:读写提交版本控制,占位符为形如groupX_*,noteId的schema对应为groupId_noteId
   */
  String[] GROUP_MEMBER_PERMISSION_FORMATS = {NOTE_READER_PERMISSION_FORMAT, NOTE_WRITER_PERMISSION_FORMAT, NOTE_OWNER_PERMISSION_FORMAT, NOTE_COMMITTER_PERMISSION_FORMAT};

  /**
   * 参赛队队员的role的名称,同一个对内部所有的note都可以读写,不再对队内部的note区分谁是owner
   */
  String GROUP_MEMBER_ROLE_NAME_FORMAT = "group_member_%s";

  /**
   * 控制哪些用户能使用哪些interpreter的角色名字,占位符为interpreter的id
   */
  String INTERPRETER_ROLE_NAME_FORMAT = "interpreter_user_%s";

  /**
   * 管理员的role name
   */
  String ROLE_ADMIN = "admin";

  /**
   * 是否是某个参赛队的队员
   *
   * @param subject shiro subject
   * @param groupId 队id
   */
  boolean isGroupMember(Subject subject, String groupId);

  /**
   * 是否是参赛队的队长
   *
   * @param groupId 队id
   */
  boolean isGroupLeader(Subject subject, String groupId);

  /**
   * 是否是管理员
   */
  boolean isAdmin(Subject subject);

  /**
   * 添加队员
   */
  void addGroupMember(String groupId, String userName);

  /**
   * 添加队长
   */
  void addGroupLeader(String groupId, String userName);

  /**
   * 添加参赛队
   * TODO:有不添加任何队员,而单独添加参赛队的场景吗?
   *
   * @param groupId 参赛队id
   */
  void addGroup(String groupId);

  /**
   * 给user授权角色(批量),实现方需要处理判断去重
   */
  void grantRolesToUser(String userName, String... roleNames);

  /**
   * 给角色添加权限(批量),实现方需要处理判断去重
   */
  void grantPermissionsToRole(String roleName, String... permissions);

  /**
   * 获取该队伍的所有的队员
   *
   * @param groupId 队伍id
   * @return 该队伍的所有的队员的userName
   */
  List<String> getUsersForGroup(String groupId);

  /**
   * 判断用户是否有权限访问指定的interpreter
   *
   * @param userName      用户名
   * @param interpreterId 解释器id
   * @return 是否有权限访问
   */
  boolean canUseInterpreter(final String userName, final String interpreterId);
}
