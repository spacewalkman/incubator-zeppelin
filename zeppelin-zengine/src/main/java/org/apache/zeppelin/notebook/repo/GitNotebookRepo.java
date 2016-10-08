/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.notebook.repo;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.NameScope;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

/**
 * NotebookRepo that hosts all the notebook FS in a single Git repo
 *
 * This impl intended to be simple and straightforward:
 * - does not handle branches
 * - only basic local git file repo, no remote Github push\pull yet
 *
 * TODO(bzz): add default .gitignore
 */
public class GitNotebookRepo extends VFSNotebookRepo {
  private static final Logger LOG = LoggerFactory.getLogger(GitNotebookRepo.class);
  private static final String GIT_IGNORE = ".gitignore";//默认的.gitignore文件

  private String localPath;
  private Git git;

  public GitNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    super(conf);
    localPath = getRootDir().getName().getPath();
    LOG.info("Opening a git repo at '{}'", localPath);
    Repository localRepo = new FileRepository(Joiner.on(File.separator).join(localPath, ".git"));
    if (!localRepo.getDirectory().exists()) {
      LOG.info("Git repo {} does not exist, creating a new one", localRepo.getDirectory());
      localRepo.create();
    }
    git = new Git(localRepo);

    this.commitDefaultIgnore();
  }

  /**
   * 创建默认的.gitignore文件，作为第一次commit，默认ignore *.swp和*.note.json文件
   */
  private void commitDefaultIgnore() throws IOException {
    FileObject gitIgnore = getRootDir().resolveFile(GIT_IGNORE, NameScope.CHILD);
    if (!gitIgnore.exists()) {
      gitIgnore.createFile();
      OutputStream defaultIgnoreOutput = gitIgnore.getContent().getOutputStream();

      String line = System.getProperty("line.separator");
      defaultIgnoreOutput.write(("*.swp" + line).getBytes("UTF-8"));//其他的ignore项
      defaultIgnoreOutput.write(("*.note.json" + line).getBytes("UTF-8"));//默认note保存时的临时文件
      defaultIgnoreOutput.write(("#*" + line).getBytes("UTF-8"));//如下是其他2各临时文件
      defaultIgnoreOutput.write(("~*" + line).getBytes("UTF-8"));

      defaultIgnoreOutput.close();
    }
    checkpoint(GIT_IGNORE, "ignore *.swp and *.note.json by default");//如果不commit，则每次jgit diff的时候都会显示这次.gitignore在diff中
  }

  void checkpoint(String file, String commitMessage) {
    try {
      List<DiffEntry> gitDiff = git.diff().setPathFilter(PathFilter.create(file)).call();//仅仅显示当前note的diff,避免diff整个notebook目录，gitDiff总不会为空，造成commit没有note内容，仅仅有message
      if (!gitDiff.isEmpty()) {
        LOG.debug("Changes found for file '{}': {}", file, gitDiff);
        DirCache added = git.add().addFilepattern(file).call();
        LOG.debug("{} changes are about to be committed", added.getEntryCount());
        git.commit().setMessage(commitMessage).setAuthor("admin", "")
                .setCommitter("admin", "").call();//TODO:大赛组委会如何联系？
      } else {
        LOG.debug("No changes found for file {}", file);
      }
    } catch (GitAPIException e) {
      LOG.error("Failed to add+commit {} to Git", file, e);
    }
  }

  @Override
  public synchronized void save(Note note, String principal) throws IOException {
    super.save(note, principal);
  }

  /**
   * implemented as git add+commit
   *
   * @param note          note
   * @param commitMessage is a commit message (checkpoint message) (non-Javadoc)
   */
  @Override
  public Revision checkpoint(Note note, String commitMessage,
                             String principal) {
    Revision revision = null;
    try {
      List<DiffEntry> gitDiff = git.diff().setPathFilter(PathFilter.create(note.getId())).call();//仅仅显示当前note的diff,避免diff整个notebook目录，gitDiff总不会为空，造成commit没有note内容，仅仅有message
      if (!gitDiff.isEmpty()) {
        LOG.debug("Changes found for pattern '{}': {}", note.getId(), gitDiff);
        DirCache added = git.add().addFilepattern(note.getId()).call();
        LOG.debug("{} changes are about to be committed", added.getEntryCount());
        RevCommit commit = git.commit().setMessage(commitMessage).setAuthor(principal, "")
                .setCommitter(principal, "").call();//TODO:email要如何获取？
        revision = new Revision(commit.getName(), note.getId(), note.getName(), commit.getShortMessage(), commit.getCommitTime(), commit.getAuthorIdent().getName(), principal, note.getGroup(), note.getProjectId());
      } else {
        LOG.debug("No changes found {}", note.getId());
      }
    } catch (GitAPIException e) {
      LOG.error("Failed to add+commit {} to Git", note.getId(), e);
    }
    return revision;
  }

  /**
   * the idea is to:
   * 1. stash current changes
   * 2. remember head commit and checkout to the desired revision
   * 3. get note and checkout back to the head
   * 4. apply stash on top and remove it
   */
  @Override
  public synchronized Note get(String noteId, String revId, String principal)
          throws IOException {
    Note note = null;
    RevCommit stash = null;
    try {
      List<DiffEntry> gitDiff = git.diff().setPathFilter(PathFilter.create(noteId)).call();
      boolean modified = !gitDiff.isEmpty();
      if (modified) {
        // stash changes
        stash = git.stashCreate().call();
        Collection<RevCommit> stashes = git.stashList().call();
        LOG.debug("Created stash : {}, stash size : {}", stash, stashes.size());
      }
      ObjectId head = git.getRepository().resolve(Constants.HEAD);
      // checkout to target revision
      git.checkout().setStartPoint(revId).addPath(noteId).call();
      // get the note
      note = super.get(noteId, principal);
      // checkout back to head
      git.checkout().setStartPoint(head.getName()).addPath(noteId).call();
      if (modified && stash != null) {
        // unstash changes
        ObjectId applied = git.stashApply().setStashRef(stash.getName()).call();
        ObjectId dropped = git.stashDrop().setStashRef(0).call();
        Collection<RevCommit> stashes = git.stashList().call();
        LOG.debug("Stash applied as : {}, and dropped : {}, stash size: {}", applied, dropped,
                stashes.size());
      }
    } catch (GitAPIException e) {
      LOG.error("Failed to return note from revision \"{}\"", revId, e);
    }
    return note;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, String principal) {
    List<Revision> history = Lists.newArrayList();
    LOG.debug("Listing history for {}:", noteId);
    try {
      Iterable<RevCommit> logs = git.log().addPath(noteId).call();
      for (RevCommit log : logs) {
        history.add(new Revision(log.getName(), noteId, "", log.getShortMessage(), log.getCommitTime(),
                log.getAuthorIdent() == null ? "" : log.getAuthorIdent().getName(),
                log.getCommitterIdent() == null ? "" : log.getCommitterIdent().getName(), "", ""));//TODO：git实现的无法获取note的title,team和projectId
        LOG.debug(" - ({},{},{})", log.getName(), log.getCommitTime(), log.getFullMessage());
      }
    } catch (NoHeadException e) {
      //when no initial commit exists
      LOG.warn("No Head found for {}, {}", noteId, e.getMessage());
    } catch (GitAPIException e) {
      LOG.error("Failed to get logs for {}", noteId, e);
    }
    return history;
  }

  @Override
  public void close() {
    git.getRepository().close();
  }

  //DI replacements for Tests
  Git getGit() {
    return git;
  }

  void setGit(Git git) {
    this.git = git;
  }

}
