package org.apache.zeppelin.notebook.repo;

import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.notebook.Note;

import java.io.IOException;
import java.util.List;

/**
 * wrap了GitNotebookRepo来实现单独的接口
 */
public class GitRevisionService implements RevisionService {

  private GitNotebookRepo gitNoteRepo;

  public GitRevisionService(GitNotebookRepo gitNoteRepo) {
    this.gitNoteRepo = gitNoteRepo;
  }

  @Override
  public NotebookRepo.Revision checkpoint(String noteId, String checkpointMsg, Subject subject) throws IOException {
    return this.gitNoteRepo.checkpoint(noteId, checkpointMsg, subject);
  }

  @Override
  public Note get(String noteId, String revId, Subject subject) throws IOException {
    return this.gitNoteRepo.get(noteId, revId, subject);
  }

  @Override
  public List<NotebookRepo.Revision> revisionHistory(String noteId, Subject subject) {
    return this.gitNoteRepo.revisionHistory(noteId, subject);
  }
}
