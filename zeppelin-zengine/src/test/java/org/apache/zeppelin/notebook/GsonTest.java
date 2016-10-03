package org.apache.zeppelin.notebook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.DependencyResolver;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.mock.MockInterpreter1;
import org.apache.zeppelin.interpreter.mock.MockInterpreter2;
import org.apache.zeppelin.notebook.repo.ElasticSearchRepo;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.user.Credentials;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class GsonTest implements JobListenerFactory {

  private Notebook notebook;
  private Gson gson;

  @Before
  public void setUp() throws Exception {
    getNotebook();
    getGson();
  }

  private void getNotebook() throws Exception {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();

    MockInterpreter1.register("mock1", "org.apache.zeppelin.interpreter.mock.MockInterpreter1");
    MockInterpreter2.register("mock2", "org.apache.zeppelin.interpreter.mock.MockInterpreter2");

    DependencyResolver depResolver = new DependencyResolver(".tmp/local-repo");
    InterpreterFactory factory = new InterpreterFactory(conf, null, null, null, depResolver);

    SearchService search = mock(SearchService.class);
    NotebookRepo notebookRepo = new ElasticSearchRepo(conf);
    NotebookAuthorization notebookAuthorization = NotebookAuthorization.getInstance();
    Credentials credentials = new Credentials(conf.credentialsPersist(), conf.getCredentialsPath());

    notebook = new Notebook(conf, notebookRepo, new SchedulerFactory(), factory, this, search,
            notebookAuthorization, credentials);
  }

  private void getGson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    gsonBuilder.setDateFormat("yyy-MM-dd HH:mm:ss");
    gson = gsonBuilder.create();
  }

  /**
   * test Date gson serialization
   */
  @Test
  public void testGsonNoteSearilization() throws IOException {
    Note note = notebook.createNote(null);

    note.setName("testGsonNoteSearilization");
    note.setTopic("用户画像");

    List<String> tags = new ArrayList<String>();
    tags.add("主题1");
    tags.add("主题2");
    tags.add("主题3");

    note.setTags(tags);
    Paragraph p1 = note.addParagraph();
    Map config = p1.getConfig();
    config.put("enabled", true);
    p1.setConfig(config);
    p1.setText("hello world");

    String jsoned = gson.toJson(note);
    System.out.println(jsoned);
  }

  @Override
  public ParagraphJobListener getParagraphJobListener(Note note) {
    return null;
  }
}
