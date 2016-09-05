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

package org.apache.zeppelin.notebook;

import org.apache.commons.lang.StringUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.Credentials;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class NoteTest {
  @Mock
  NotebookRepo repo;

  @Mock
  JobListenerFactory jobListenerFactory;

  @Mock
  SearchService index;

  @Mock
  Credentials credentials;

  @Mock
  Interpreter interpreter;

  @Mock
  Scheduler scheduler;

  @Mock
  NoteEventListener noteEventListener;

  @Mock
  InterpreterFactory interpreterFactory;

  @Test
  public void runNormalTest() {
    when(interpreterFactory.getInterpreter(anyString(), eq("spark"))).thenReturn(interpreter);
    when(interpreter.getScheduler()).thenReturn(scheduler);

    String pText = "%spark sc.version";
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, "anonymous", noteEventListener);
    Paragraph p = note.addParagraph();
    p.setText(pText);
    note.run(p.getId());

    ArgumentCaptor<Paragraph> pCaptor = ArgumentCaptor.forClass(Paragraph.class);
    verify(scheduler, only()).submit(pCaptor.capture());
    verify(interpreterFactory, only()).getInterpreter(anyString(), eq("spark"));

    assertEquals("Paragraph text", pText, pCaptor.getValue().getText());
  }

  @Test
  public void putDefaultReplNameIfInterpreterSettingAbsent() {
    when(interpreterFactory.getDefaultInterpreterSetting(anyString()))
            .thenReturn(null);
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, "anonymous", noteEventListener);
    note.putDefaultReplName();

    assertEquals(StringUtils.EMPTY, note.getLastReplName());
    assertEquals(StringUtils.EMPTY, note.getLastInterpreterName());
  }

  @Test
  public void putDefaultReplNameIfInterpreterSettingPresent() {
    InterpreterSetting interpreterSetting = Mockito.mock(InterpreterSetting.class);
    when(interpreterSetting.getName()).thenReturn("spark");
    when(interpreterFactory.getDefaultInterpreterSetting(anyString()))
            .thenReturn(interpreterSetting);

    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, "anonymous", noteEventListener);
    note.putDefaultReplName();

    assertEquals("spark", note.getLastReplName());
    assertEquals("%spark", note.getLastInterpreterName());
  }

  @Test
  public void addParagraphWithLastReplName() {
    InterpreterSetting interpreterSetting = Mockito.mock(InterpreterSetting.class);
    when(interpreterSetting.getName()).thenReturn("spark");
    when(interpreterFactory.getDefaultInterpreterSetting(anyString()))
            .thenReturn(interpreterSetting);
    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, "anonymous", noteEventListener);
    note.putDefaultReplName(); //set lastReplName
    when(interpreterFactory.getInterpreter(note.getId(), "spark")).thenReturn(new MockInterpreter2(null));

    Paragraph p = note.addParagraph();

    assertEquals("%spark ", p.getText());
  }

  @Test
  public void insertParagraphWithLastReplName() {
    InterpreterSetting interpreterSetting = Mockito.mock(InterpreterSetting.class);
    when(interpreterSetting.getName()).thenReturn("spark");
    when(interpreterFactory.getDefaultInterpreterSetting(anyString()))
            .thenReturn(interpreterSetting);

    Note note = new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, "anonymous", noteEventListener);
    note.putDefaultReplName(); //set lastReplName
    when(interpreterFactory.getInterpreter(note.getId(), "spark")).thenReturn(new MockInterpreter2(null));

    Paragraph p = note.insertParagraph(note.getParagraphs().size());

    assertEquals("%spark ", p.getText());
  }

  @Test
  public void setLastReplName() {
    String paragraphId = "HelloWorld";
    Note note = Mockito.spy(new Note(repo, interpreterFactory, jobListenerFactory, index, credentials, "anonymous", noteEventListener));
    Paragraph mockParagraph = Mockito.mock(Paragraph.class);
    when(note.getParagraph(paragraphId)).thenReturn(mockParagraph);
    when(mockParagraph.getRequiredReplName()).thenReturn("spark");

    note.setLastReplName(paragraphId);

    assertEquals("spark", note.getLastReplName());
  }

  @Test
  public void isBindingTest() {
    Note note = spy(new Note());
    when(note.getId()).thenReturn("test1");
    InterpreterFactory mockInterpreterFactory = mock(InterpreterFactory.class);
    note.setInterpreterFactory(mockInterpreterFactory);

    //when is not binding
    assertFalse(note.isBinding("spark"));

    //when is binding
    when(mockInterpreterFactory.getInterpreter("test1", "spark")).
        thenReturn(new MockInterpreter2(null));
    assertTrue(note.isBinding("spark"));
  }
}
