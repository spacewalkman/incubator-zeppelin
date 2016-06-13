package org.apache.zeppelin.notebook.repo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * ElasticSearch-backed Repo
 */
public class ElasticSearchRepo implements NotebookRepo {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchRepo.class);

  private static final String SEARCH_FILED_TITLE = "title";
  private static final String INDEX_FILED_USER = "user";
  private static final String SEARCH_FIELD_TEXT = "contents";
  private static final String SEARCH_FIELD_MODIFIED = "modifed";

  static final String PARAGRAPH = "paragraph";
  static final String ID_FIELD = "id";

  private static final String CONFIG_REPO_ES_HOST = "zeppelin.es.search.repo.host";
  private static final String CONFIG_REPO_ES_PORT = "zeppelin.es.search.repo.port";
  private static final String CONFIG_REPO_ES_INDEX_NAME = "zeppelin.es.search.repo.index.name";
  private static final String CONFIG_REPO_ES_TYPE_NAME = "zeppelin.es.search.repo.index.type";

  private Client client;
  private String indexName;
  private String typeName;


  public ElasticSearchRepo(ZeppelinConfiguration conf) throws IOException {
    String esHost = conf.getString(CONFIG_REPO_ES_HOST, "localhost");
    int esPort = conf.getInt(CONFIG_REPO_ES_PORT, 9300);

    this.indexName = conf.getString(CONFIG_REPO_ES_INDEX_NAME, "zeppelin");
    this.typeName = conf.getString(CONFIG_REPO_ES_TYPE_NAME, "note");

    client = TransportClient.builder().build()
            .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort));

  }


  @Override
  public List<NoteInfo> list() throws IOException {
    SearchResponse response = client.prepareSearch(indexName)
            .setTypes(typeName)
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .execute()
            .actionGet();

    List<NoteInfo> results = null;
    SearchHits hits = response.getHits();
    long count = hits.getTotalHits();
    if (count > 0) {
      results = new ArrayList<NoteInfo>((int) count);
      for (SearchHit hit : hits.getHits()) {
        Note noteParsed = getGson().fromJson(hit.getSourceAsString(), Note.class);
        results.add(new NoteInfo(noteParsed));
      }

    }

    return results;
  }

  @Override
  public Note get(String noteId) throws IOException {
    if (null == noteId || noteId.isEmpty()) {
      LOG.error("noteId cannot be null");
      return null;
    }

    GetResponse response = client.prepareGet(indexName, typeName, noteId).execute()
            .actionGet();

    if (!response.isExists()) {
      return null;
    }

    Gson gson = getGson();
    return gson.fromJson(response.getSourceAsString(), Note.class);

  }

  @Override
  public void save(Note note) throws IOException {
    IndexResponse response = client.prepareIndex(this.indexName, this.typeName, note.id())
            .setSource(getGson().toJson(note))
            .get();

    String idGenerated = response.getId();
    LOG.debug("index success,id={}", idGenerated);

  }

  private Gson getGson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.setPrettyPrinting();
    return gsonBuilder.create();
  }

  @Override
  public void remove(String noteId) throws IOException {
    DeleteResponse response = client.prepareDelete(this.indexName, this.typeName, noteId).get();

    if (response.isFound()) {
      LOG.debug("note id={},delete successfully", noteId);
    } else {
      LOG.warn("note id={},not found", noteId);
    }
  }

  @Override
  public void close() {
    if (null != client) {
      client.close();
    }
  }

  @Override
  public void checkpoint(String noteId, String checkPointName) throws IOException {
    return;//no checkpoint feature in ES
  }
}
