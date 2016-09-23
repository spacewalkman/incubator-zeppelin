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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class JdbcNotebookRepoTest {
  JdbcNotebookRepo jdbcNotebookRepo;
  PrincipalCollection principals;
  String principal = "wangyuda";

  final String path1 = "/Users/qianyong/src/qy_zeppelin.git/notebook/2BRUFSGFM/note.json";
  final String path2 = "/Users/qianyong/src/qy_zeppelin.git/notebook/2BWFXNMUK/note.json";

  ZeppelinConfiguration conf = ZeppelinConfiguration.create();

  @Before
  public void init() throws Exception {
    jdbcNotebookRepo = new JdbcNotebookRepo(conf);
    principals = new SimplePrincipalCollection("wangyuda", "test");

  }

  @After
  public void destroy() throws Exception {
    jdbcNotebookRepo.close();
  }

  @Test
  public void list() throws Exception {
    save(path1, "2BRUFSGFM");
    save(path2, "2BWFXNMUK");


    List<NoteInfo> noteInfos = jdbcNotebookRepo.list(principal);
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

  public Note save(String path, String id) throws Exception {
    FileSystemManager fsManager = VFS.getManager();
    FileObject file = fsManager.resolveFile(path);
    Assert.assertTrue(file.exists() && file.getType() == FileType.FILE);
    FileContent content = file.getContent();
    InputStream ins = content.getInputStream();
    String json = IOUtils.toString(ins, ZeppelinConfiguration.create().getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ENCODING));
    ins.close();

    Note note = GsonUtil.fromJson(json, Note.class);

    jdbcNotebookRepo.save(note, principal);

    return note;
  }

  public void remove(String noteId) throws Exception {
    jdbcNotebookRepo.remove(noteId, null);
  }

  @Test
  public void checkpoint() throws Exception {
    Note note = save(path1, "2BRUFSGFM");
    NotebookRepo.Revision revision = jdbcNotebookRepo.checkpoint(note, "这是一次commit", principal);

    assertTrue(revision != null);
    assertTrue(revision.message.equals("这是一次commit"));
    assertTrue(revision.time <= System.currentTimeMillis());
    assertTrue(revision.committer.equals("wangyuda"));

    remove("2BRUFSGFM");

    //TODO:清理刚才插入的revision
  }

  @Test
  public void getRevision() throws Exception {
    Note note = save(path1, "2BRUFSGFM");
    NotebookRepo.Revision revision = jdbcNotebookRepo.checkpoint(note, "这是一次commit", principal);

    assertTrue(revision != null);
    assertTrue(revision.message.equals("这是一次commit"));
    assertTrue(revision.time <= System.currentTimeMillis());
    assertTrue(revision.committer.equals("wangyuda"));

    remove("2BRUFSGFM");

    Note note2 = jdbcNotebookRepo.get("2BRUFSGFM", revision.id, principal);
    assertTrue(note2 != null);
    assertTrue(note2.getId().equals("2BRUFSGFM"));
    assertEquals("2BRUFSGFM", note2.getId());
  }

  SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

  @Test
  public void revisionHistory() throws Exception {
    Note note2 = save(path1, "2BRUFSGFM");
    NotebookRepo.Revision revision = jdbcNotebookRepo.checkpoint(note2, "这是一次commit", principal);
    List<NotebookRepo.Revision> revisions = jdbcNotebookRepo.revisionHistory("2BRUFSGFM", principal);

    assertTrue(revisions != null && revisions.size() >= 1);

    assertTrue(revision.id.equals(revisions.get(0).id));//按照时间降序排序，最后一次应该在最上面
    assertTrue(revisions.get(0).message.equals("这是一次commit"));
    Thread.sleep(500);//TODO:如果不sleep，从mysql中获取回来的revision的commit_date居然比系统currentTime要新
    Date now = new Date();
    System.out.println(String.format("revision创建时间%s, 当前时间:%s", simpleDateFormat.format(new Date(revisions.get(0).time)), simpleDateFormat.format(now)));
    assertTrue(revisions.get(0).time <= now.getTime());
    assertTrue(revisions.get(0).committer.equals("wangyuda"));

    remove("2BRUFSGFM");
  }
}