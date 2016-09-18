package org.apache.zeppelin.notebook.repo;

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.util.GsonUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

public class JdbcNotebookRepoTest {
  JdbcNotebookRepo jdbcNotebookRepo;

  @Before
  public void init() throws Exception {
    jdbcNotebookRepo = new JdbcNotebookRepo(ZeppelinConfiguration.create());
  }

  @After
  public void destroy() throws Exception {
    jdbcNotebookRepo.close();
  }

  final String path1 = "/Users/qianyong/src/qy_zeppelin.git/notebook/2BRUFSGFM/note.json";
  final String path2 = "/Users/qianyong/src/qy_zeppelin.git/notebook/2BWFXNMUK/note.json";

  @Test
  public void list() throws Exception {
    save(path1, "2BRUFSGFM");
    save(path2, "2BWFXNMUK");
    PrincipalCollection principals = new SimplePrincipalCollection("wangyuda", "test");

    Subject subject = new Subject.Builder(new DefaultSecurityManager()).principals(principals).buildSubject();
    List<NoteInfo> noteInfos = jdbcNotebookRepo.list(subject);
    Assert.assertNotNull(noteInfos);
    Assert.assertTrue(noteInfos.size() >= 2);
    boolean firstHit = false, secondHit = false;

    for (NoteInfo noteInfo : noteInfos) {
      if (noteInfo.getId().equals("2BRUFSGFM")) {
        firstHit = true;
        assertTrue(noteInfo.getName().equals("test"));
        continue;
      }

      if (noteInfo.getId().equals("2BWFXNMUK")) {
        secondHit = true;
        assertTrue(noteInfo.getName().equals("RuleTest"));
        continue;
      }
    }

    Assert.assertTrue(firstHit && secondHit);
    remove("2BRUFSGFM");
    remove("2BWFXNMUK");
  }

  @Test
  public void get() throws Exception {
    save(path1, "2BRUFSGFM");
    Note note = jdbcNotebookRepo.get("2BRUFSGFM", null);
    Assert.assertNotNull(note);
    Assert.assertTrue(note.getId().equalsIgnoreCase("2BRUFSGFM"));
    remove("2BRUFSGFM");
  }

  @Test
  public void save() throws Exception {
    save(path1, "2BRUFSGFM");
    remove("2BRUFSGFM");
  }

  public void save(String path, String id) throws Exception {
    FileSystemManager fsManager = VFS.getManager();
    FileObject file = fsManager.resolveFile(path);
    Assert.assertTrue(file.exists() && file.getType() == FileType.FILE);
    FileContent content = file.getContent();
    InputStream ins = content.getInputStream();
    String json = IOUtils.toString(ins, ZeppelinConfiguration.create().getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
    ins.close();

    Note note = GsonUtil.fromJson(json, Note.class);

    jdbcNotebookRepo.save(note, null);
  }

  public void remove(String noteId) throws Exception {
    jdbcNotebookRepo.remove(noteId, null);
  }

}