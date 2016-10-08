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

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.VFS;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.commit.SubmitLeftOver;
import org.apache.zeppelin.scheduler.Job.Status;
import org.apache.zeppelin.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class VFSNotebookRepo implements NotebookRepo {
  Logger LOG = LoggerFactory.getLogger(VFSNotebookRepo.class);

  private FileSystemManager fsManager;
  private URI filesystemRoot;
  private ZeppelinConfiguration conf;

  public VFSNotebookRepo(ZeppelinConfiguration conf) throws IOException {
    this.conf = conf;

    try {
      if (conf.isWindowsPath(conf.getNotebookDir())) {
        filesystemRoot = new File(conf.getNotebookDir()).toURI();
      } else {
        filesystemRoot = new URI(conf.getNotebookDir());
      }
    } catch (URISyntaxException e1) {
      throw new IOException(e1);
    }

    if (filesystemRoot.getScheme() == null) { // it is local path
      try {
        this.filesystemRoot = new URI(new File(
                conf.getRelativeDir(filesystemRoot.getPath())).getAbsolutePath());
      } catch (URISyntaxException e) {
        throw new IOException(e);
      }
    }

    fsManager = VFS.getManager();
    FileObject file = fsManager.resolveFile(filesystemRoot.getPath());
    if (!file.exists()) {
      LOG.info("Notebook dir doesn't exist, create.");
      file.createFolder();
    }
  }

  private String getPath(String path) {
    if (path == null || path.trim().length() == 0) {
      return filesystemRoot.toString();
    }
    if (path.startsWith("/")) {
      return filesystemRoot.toString() + path;
    } else {
      return filesystemRoot.toString() + "/" + path;
    }
  }

  private boolean isDirectory(FileObject fo) throws IOException {
    if (fo == null) return false;
    if (fo.getType() == FileType.FOLDER) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public List<NoteInfo> list(String principalø) throws IOException {
    FileObject rootDir = getRootDir();

    FileObject[] children = rootDir.getChildren();

    List<NoteInfo> infos = new LinkedList<NoteInfo>();
    for (FileObject f : children) {
      String fileName = f.getName().getBaseName();
      if (f.isHidden()
              || fileName.startsWith(".")
              || fileName.startsWith("#")
              || fileName.startsWith("~")) {
        // skip hidden, temporary files
        continue;
      }

      if (!isDirectory(f)) {
        // currently single note is saved like, [NOTE_ID]/note.json.
        // so it must be a directory
        continue;
      }

      NoteInfo info = null;

      try {
        info = getNoteInfo(f);
        if (info != null) {
          infos.add(info);
        }
      } catch (Exception e) {
        LOG.error("Can't read note " + f.getName().toString(), e);
      }
    }

    return infos;
  }

  private Note getNote(FileObject noteDir) throws IOException {
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    FileObject noteJson = noteDir.resolveFile("note.json", NameScope.CHILD);
    if (!noteJson.exists()) {
      throw new IOException(noteJson.getName().toString() + " not found");
    }

    FileContent content = noteJson.getContent();
    InputStream ins = content.getInputStream();
    String json = IOUtils.toString(ins, conf.getString(ConfVars.ZEPPELIN_ENCODING));
    ins.close();

    Note note = GsonUtil.fromJson(json, Note.class);

    Date lastUpdated = null;
    for (Paragraph p : note.getParagraphs()) {
      if (p.getStatus() == Status.PENDING || p.getStatus() == Status.RUNNING) {
        p.setStatus(Status.ABORT);
      }

      List<ApplicationState> appStates = p.getAllApplicationStates();
      if (appStates != null) {
        for (ApplicationState app : appStates) {
          if (app.getStatus() != ApplicationState.Status.ERROR) {
            app.setStatus(ApplicationState.Status.UNLOADED);
          }
        }
      }
    }

    return note;
  }

  private NoteInfo getNoteInfo(FileObject noteDir) throws IOException {
    Note note = getNote(noteDir);
    return new NoteInfo(note);
  }

  @Override
  public Note get(String noteId, String principal) throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));
    FileObject noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);

    return getNote(noteDir);
  }

  protected FileObject getRootDir() throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));

    if (!rootDir.exists()) {
      throw new IOException("Root path does not exists");
    }

    if (!isDirectory(rootDir)) {
      throw new IOException("Root path is not a directory");
    }

    return rootDir;
  }

  @Override
  public synchronized void save(Note note, String principal) throws IOException {
    String json = GsonUtil.toJson(note);

    FileObject rootDir = getRootDir();
    FileObject noteDir = rootDir.resolveFile(note.getId(), NameScope.CHILD);

    if (!noteDir.exists()) {
      noteDir.createFolder();
    }
    if (!isDirectory(noteDir)) {
      throw new IOException(noteDir.getName().toString() + " is not a directory");
    }

    FileObject noteJson = noteDir.resolveFile(".note.json", NameScope.CHILD);
    // false means not appending. creates file if not exists
    OutputStream out = noteJson.getContent().getOutputStream(false);
    out.write(json.getBytes(conf.getString(ConfVars.ZEPPELIN_ENCODING)));
    out.close();
    noteJson.moveTo(noteDir.resolveFile("note.json", NameScope.CHILD));
  }

  @Override
  public void remove(String noteId, String principal) throws IOException {
    FileObject rootDir = fsManager.resolveFile(getPath("/"));
    FileObject noteDir = rootDir.resolveFile(noteId, NameScope.CHILD);

    if (!noteDir.exists()) {
      // nothing to do
      return;
    }

    if (!isDirectory(noteDir)) {
      // it is not look like zeppelin note savings
      throw new IOException("Can not remove " + noteDir.getName().toString());
    }

    noteDir.delete(Selectors.SELECT_SELF_AND_CHILDREN);
  }

  @Override
  public void close() {
    //no-op    
  }

  @Override
  public Revision checkpoint(Note note, String checkpointMsg, String principal)
          throws IOException {
    // no-op
    LOG.info("Checkpoint feature isn't supported in {}", this.getClass().toString());
    return null;
  }

  @Override
  public Note get(String noteId, String revId, String principal) throws IOException {
    LOG.info("get revision feature isn't supported in {}", this.getClass().toString());
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId, String principal) {
    LOG.info("revisionHistory feature isn't supported in {}", this.getClass().toString());
    return null;
  }

  /**
   * TODO:这里应该做成push 到remote 远程git repo上
   *
   * @param noteId     当前提交的note id，在dbms中由于revisionId是主键，故没有使用noteid
   * @param revisionId 待提交的版本
   */
  @Override
  public SubmitLeftOver submit(String noteId, String revisionId) {
    LOG.info("submit feature isn't supported in {}", this.getClass().toString());
    return null;
  }

  @Override
  public int currentSubmitTimes(String team, String projectId) {
    LOG.info("query current submit times feature isn't supported in {}", this.getClass().toString());
    return -1;
  }

}
