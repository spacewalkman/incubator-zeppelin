package org.apache.zeppelin.notebook;

import org.apache.shiro.subject.Subject;

import java.util.List;

/**
 * 基于shiro WildcardPermission的权限验证,只到参赛队级别
 */
public interface IShiroNotebookAuthorization {

  /**
   * 某个note可读,占位符为具体的noteId，后面2个占位符分别为groupId和noteId
   */
  String NOTE_GROUP_READER_PERMISSION_FORMAT = "note:readers:%s:%s";//如果是4维的，则note的id是可以重复的，只要保证每个group内部唯一即可

  /**
   * 单个note的reader,权限项
   */
  String NOTE_READER_PERMISSION_FORMAT = "note:readers:%s";

  /**
   * 某个note可写,占位符为具体的noteId，后面2个占位符分别为groupId和noteId
   */
  String NOTE_GROUP_WRITER_PERMISSION_FORMAT = "note:writers:%s:%s";

  /**
   * 单个note的writer,权限项
   */
  String NOTE_WRITER_PERMISSION_FORMAT = "note:writers:%s";

  /**
   * 某个note的所有者,占位符为具体的noteId，后面2个占位符分别为groupId和noteId
   */
  String NOTE_GROUP_OWNER_PERMISSION_FORMAT = "note:owners:%s:%s";

  /**
   * 单个note的owner,权限项
   */
  String NOTE_OWNER_PERMISSION_FORMAT = "note:owners:%s";

  /**
   * 某个note可以提交版本历史,占位符为具体的noteId，后面2个占位符分别为groupId和noteId
   */
  String NOTE_GROUP_COMMITTER_PERMISSION_FORMAT = "note:committer:%s:%s";

  /**
   * 向组委会提交notes的权利,该权利由队长独有,队员不具备,后面2个占位符分别为groupId和noteId
   */
  String NOTE_GROUP_SUBMITTER_PERMISSION_FORMAT = "note:submitters:%s:%s";

  /**
   * 队员的权限列表,可以:读写提交版本控制,占位符为形如groupX_*,noteId的schema对应为groupId_noteId
   */
  String GROUP_MEMBER_PERMISSIONS_FORMAT = "note:%s:%s:%s";

  /**
   * multi-part权限，对应到NOTE_GROUP_SUBMITTER_PERMISSION_FORMAT的第二个占位符
   */
  String GROUP_MEMBER_PERMISSIONS = "readers,writers,owners,committer";

  /**
   * 参赛队队员的role的名称,同一个对内部所有的note都可以读写,不再对队内部的note区分谁是owner
   */
  String GROUP_MEMBER_ROLE_NAME_FORMAT = "group_member_%s";

  /**
   * 组submitter的角色名称，group_leader=group_member  +  group_sumbmitter
   */
  String GROUP_SUBMITTER_ROLE_NAME_FORMAT = "group_submitter_%s";

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
   * 是否是参赛队的队长,队长具有向组委会提交的权限
   *
   * @param groupId 队id
   */
  boolean isGroupLeader(Subject subject, String groupId);

  /**
   * 是否是管理员
   */
  boolean isAdmin(Subject subject);

  /**
   * 是否能提交note的revision到组委会，只提供note粒度的控制，不提供note's revision级别的控制
   */
  boolean isSubmitter(Subject subject, String groupId, String noteId);

  /**
   * 是否能够提交版本控制
   */
  boolean isCommitter(Subject subject, String groupId, String noteId);

  /**
   * 是否是note的reader
   */
  boolean isReader(Subject subject, String groupId, String noteId);

  /**
   * 是否是note的writer
   */
  boolean isWriter(Subject subject, String groupId, String noteId);

  /**
   * 是否是note的owner
   */
  boolean isOwner(Subject subject, String groupId, String noteId);

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
