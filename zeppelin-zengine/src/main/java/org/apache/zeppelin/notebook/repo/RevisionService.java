package org.apache.zeppelin.notebook.repo;

import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.notebook.Note;

import java.io.IOException;
import java.util.List;

/**
 * note的版本控制服务，目前Jgit实现
 */
public interface RevisionService {
  /**
   * chekpoint (set revision) for notebook.
   *
   * @param noteId        Id of the Notebook
   * @param checkpointMsg message description of the checkpoint
   * @return Rev
   */
  @ZeppelinApi
  NotebookRepo.Revision checkpoint(String noteId, String checkpointMsg, Subject subject) throws IOException;


  /**
   * Get particular revision of the Notebook.
   *
   * @param noteId Id of the Notebook
   * @param revId  revision of the Notebook
   * @return a Notebook
   */

  @ZeppelinApi
  Note get(String noteId, String revId, Subject subject)
          throws IOException;

  /**
   * List of revisions of the given Notebook.
   *
   * @param noteId id of the Notebook
   * @return list of revisions
   */
  @ZeppelinApi
  List<NotebookRepo.Revision> revisionHistory(String noteId, Subject subject);

}
