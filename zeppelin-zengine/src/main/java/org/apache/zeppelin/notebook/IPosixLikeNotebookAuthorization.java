package org.apache.zeppelin.notebook;

import java.util.Set;

/**
 * Reader/Writer/Owner based的Posix file sytem like NotebookAuthorization
 */
public interface PosixLikeNotebookAuthorization {

  /**
   * const identifier used in permission expression,such as "groupX:readers:*"
   */
  String READERS = "readers";
  String WRITERS = "writers";
  String OWNERS = "owners";

  /**
   * add principal to note's owner
   *
   * @param principal current principal
   */
  void addOwner(String noteId, String principal);

  /**
   * add principal to note's reader
   *
   * @param noteId    note's id
   * @param principal current principal
   */
  void addReader(String noteId, String principal);

  void addWriters(String noteId, String principal);

  void setOwners(String noteId, Set<String> entities);

  void setWriters(String noteId, Set<String> entities);

  void setReaders(String noteId, Set<String> entities);

  Set<String> getOwners(String noteId);

  Set<String> getReaders(String noteId);

  Set<String> getWriters(String noteId);

  boolean isOwner(String noteId, Set<String> entities);

  boolean isWriter(String noteId, Set<String> entities);

  boolean isReader(String noteId, Set<String> entities);

  /**
   * remove note's owner role,which is groupX:owner:noteId
   */
  void removeNote(String noteId);

}
