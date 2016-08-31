package org.apache.zeppelin.notebook;

/**
 * 基于shiro WildcardPermission的权限验证,只到参赛队级别
 */
public interface IShiroShiroNotebookAuthorization {

  /**
   * 是否是参赛队的队员
   *
   * @param groupId  队id
   * @param userName 用户名
   */
  boolean isInGroupMember(String groupId, String userName);

  /**
   * 是否是参赛队的队长
   *
   * @param groupId  队id
   * @param userName 用户名
   */
  boolean isInGroupLeader(String groupId, String userName);

  /**
   * 是否是管理员
   */
  boolean isAdmin(String userName);
}
