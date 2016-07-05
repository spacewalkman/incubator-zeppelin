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

import org.apache.commons.io.FileUtils;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.ParagraphJobListener;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class ElasticSearchRepoTest implements JobListenerFactory {
  private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchRepoTest.class);
  private ZeppelinConfiguration conf;
  private SchedulerFactory schedulerFactory;
  private Notebook notebook;
  private NotebookRepo notebookRepo;
  private InterpreterFactory factory;
  private DependencyResolver depResolver;

  private File mainZepDir;
  private File mainNotebookDir;

  @Before
  public void setUp() throws Exception {
    String zpath = System.getProperty("java.io.tmpdir") + "/ZeppelinLTest_" + System.currentTimeMillis();
    mainZepDir = new File(zpath);
    mainZepDir.mkdirs();
    new File(mainZepDir, "conf").mkdirs();
    String mainNotePath = zpath + "/notebook";
    mainNotebookDir = new File(mainNotePath);
    mainNotebookDir.mkdirs();

    conf = ZeppelinConfiguration.create();

    this.schedulerFactory = new SchedulerFactory();

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");

    this.schedulerFactory = new SchedulerFactory();
    depResolver = new DependencyResolver(mainZepDir.getAbsolutePath() + "/local-repo");
    factory = new InterpreterFactory(conf, null, null, null, depResolver);

    SearchService search = mock(SearchService.class);
    notebookRepo = new ElasticSearchRepo(conf);
    notebook = new Notebook(conf, notebookRepo, schedulerFactory, factory, this, search, null, null);
  }

  @After
  public void tearDown() throws Exception {
    if (!FileUtils.deleteQuietly(mainZepDir)) {
      LOG.error("Failed to delete {} ", mainZepDir.getName());
    }

    notebookRepo.close();
  }


  /**
   * populate ES note repo with VFSNoteRepo
   */
  @Test
  public void testSyncVFSRepo2ElasticSearchRepo() throws IOException, InterruptedException {
    NotebookRepo vfsNoteRepo = new VFSNotebookRepo(ZeppelinConfiguration.create());
    Random rand = new Random();
    String[] users = {"user1", "user2"};

    int count = 0;
    for (NoteInfo info : vfsNoteRepo.list()) {
      Note note = vfsNoteRepo.get(info.getId());
      if (note.getCreatedBy() == null || note.getCreatedBy().isEmpty()) {
        note.setCreatedBy(users[rand.nextInt(2)]);
      }

      notebookRepo.save(note);
      count++;
      if (count == 1) {
        break;
      }
    }
  }

  @Test
  public void testGet() throws IOException, InterruptedException {
    List<NoteInfo> noteInfos = notebookRepo.list();
    if (noteInfos != null && noteInfos.size() > 0) {
      for (NoteInfo noteinfo :
              noteInfos) {
        Note note = notebookRepo.get(noteinfo.getId());

        assertNotNull(note);
        assertNotNull(note.getCreatedBy());
      }
    }
  }

  @Test
  public void testSearch() throws IOException, InterruptedException {
    if (notebookRepo instanceof ElasticSearchRepo) {
      ElasticSearchRepo esRepo = (ElasticSearchRepo) notebookRepo;
      List<Map<String, String>> results = esRepo.query("scala windows", -1, -1);
      LOG.debug("results={}", results);
    }
  }

  @Test
  public void testList() throws IOException, InterruptedException {
    List<NoteInfo> noteInfos = notebookRepo.list();
    LOG.debug("total count={}", noteInfos.size());

    assertNotNull(noteInfos);
  }

  @Test
  public void testSaveNotebook() throws IOException, InterruptedException {
    Note note = notebook.createNote("user1");

    Paragraph p1 = note.addParagraph();
    Map<String, Object> config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("%mock1 hello world");

    note.setName("testSaveNotebook");
    note.setTopic("商品画像");

    List<String> tags = new ArrayList<String>();
    tags.add("小贷");
    tags.add("zeppelin");
    tags.add("分析");
    note.setTags(tags);
    notebookRepo.save(note);
    assertEquals(note.getName(), "testSaveNotebook");
  }

  @Override
  public ParagraphJobListener getParagraphJobListener(Note note) {
    return null;
  }
}
