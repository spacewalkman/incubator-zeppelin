package org.apache.zeppelin.notebook;

import org.apache.shiro.subject.Subject;

import java.util.List;
import java.util.Set;

/**
 * Adaptor设计模式解决2种不同的Notebook授权方式的问题接口的兼容性问题
 */
public abstract class NotebookAuthorizationAdaptor implements IPosixLikeNotebookAuthorization,
        IShiroNotebookAuthorization {

  @Override
  public boolean isGroupMember(Subject subject, String groupId) {
    return false;
  }

  @Override
  public boolean isGroupLeader(Subject subject, String groupId) {
    return false;
  }

  @Override
  public boolean isAdmin(Subject subject) {
    return false;
  }

  @Override
  public boolean isSubmitter(Subject subject, String groupId, String noteId) {
    return false;
  }

  @Override
  public boolean isCommitter(Subject subject, String groupId, String noteId) {
    return false;
  }

  @Override
  public boolean isReader(Subject subject, String groupId, String noteId) {
    return false;
  }

  @Override
  public boolean isWriter(Subject subject, String groupId, String noteId) {
    return false;
  }

  @Override
  public boolean isOwner(Subject subject, String groupId, String noteId) {
    return false;
  }

  @Override
  public boolean isExecutor(Subject subject, String groupId, String noteId) {
    return false;
  }

  @Override
  public void addGroupMember(String groupId, String userName) {
  }

  @Override
  public void addGroupLeader(String groupId, String userName) {
  }

  @Override
  public void addGroup(String groupId) {
  }

  @Override
  public void grantRolesToUser(String userName, String... roleNames) {
  }

  @Override
  public void grantRoleTemplateReader(String userName) {
  }

  @Override
  public void grantPermissionsToRole(String roleName, String... permissions) {
  }

  @Override
  public List<String> getUsersForGroup(String groupId) {
    return null;
  }

  //以下是原Zeppelin NoteAuthorization的接口内容
  @Override
  public void addOwner(String noteId, String principal) {

  }

  @Override
  public void addReader(String noteId, String principal) {

  }

  @Override
  public void addWriters(String noteId, String principal) {

  }

  @Override
  public void setOwners(String noteId, Set<String> entities) {

  }

  @Override
  public void setWriters(String noteId, Set<String> entities) {

  }

  @Override
  public void setReaders(String noteId, Set<String> entities) {

  }

  @Override
  public Set<String> getOwners(String noteId) {
    return null;
  }

  @Override
  public Set<String> getReaders(String noteId) {
    return null;
  }

  @Override
  public Set<String> getWriters(String noteId) {
    return null;
  }

  @Override
  public boolean isOwner(String noteId, Set<String> entities) {
    return false;
  }

  @Override
  public boolean isWriter(String noteId, Set<String> entities) {
    return false;
  }

  @Override
  public boolean isReader(String noteId, Set<String> entities) {
    return false;
  }

  @Override
  public void removeNote(String noteId) {
  }

  //TODO:默认不限制用户使用特定的interpreter是否合适
  @Override
  public boolean canUseInterpreter(final String userName, final String interpreterId) {
    return true;
  }

}
