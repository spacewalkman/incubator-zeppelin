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
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterOption;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.notebook.*;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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
        factory = new InterpreterFactory(conf, new InterpreterOption(false), null, null, depResolver);

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

    @Test
    public void testGet() throws IOException, InterruptedException {
        //Note note = notebookRepo.get("AVUk-7PnAYiCyGTG0gya");
        Note note = notebookRepo.get("2BQFAE8A7");

        assertNotNull(note);
    }

    @Test
    public void testList() throws IOException, InterruptedException {
        List<NoteInfo> noteInfos= notebookRepo.list();
        LOG.debug("total count={}",noteInfos.size());

        assertNotNull(noteInfos);
    }

    @Test
    public void testSaveNotebook() throws IOException, InterruptedException {
        Note note = notebook.createNote();
        note.getNoteReplLoader().setInterpreters(factory.getDefaultInterpreterSettingList());

        Paragraph p1 = note.addParagraph();
        Map<String, Object> config = p1.getConfig();
        config.put("enabled", true);
        p1.setConfig(config);
        p1.setText("%mock1 hello world");

        note.setName("testSaveNotebook");
        notebookRepo.save(note);
        assertEquals(note.getName(), "testSaveNotebook");
    }

    class NotebookWriter implements Runnable {
        Note note;

        public NotebookWriter(Note note) {
            this.note = note;
        }

        @Override
        public void run() {
            try {
                notebookRepo.save(note);
            } catch (IOException e) {
                LOG.error(e.toString(), e);
            }
        }
    }

    @Override
    public ParagraphJobListener getParagraphJobListener(Note note) {
        return null;
    }
}
