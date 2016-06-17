package org.apache.zeppelin.notebook.repo;


import com.google.common.base.Joiner;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.util.GsonUtil;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ElasticSearch-backed Repo
 */
public class ElasticSearchRepo implements NotebookRepo, SearchService {

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
  private static final String CONFIG_REPO_ES_NOTE_TYPE_NAME = "zeppelin.es.search.repo.index.type";
  private static final String CONFIG_REPO_ES_PARAGRAPH_TYPE_NAME = "zeppelin.es.search.repo.paragraph.type";

  private Client client;
  private String indexName;
  private String noteTypeName;
  private String paragraphTypeName;//note-to-pargraph modeled as parent-to-child relation in ES


  public ElasticSearchRepo(ZeppelinConfiguration conf) throws IOException {
    String esHost = conf.getString(CONFIG_REPO_ES_HOST, "localhost");
    int esPort = conf.getInt(CONFIG_REPO_ES_PORT, 9300);

    this.indexName = conf.getString(CONFIG_REPO_ES_INDEX_NAME, "zeppelin");
    this.noteTypeName = conf.getString(CONFIG_REPO_ES_NOTE_TYPE_NAME, "note");
    this.paragraphTypeName = conf.getString(CONFIG_REPO_ES_PARAGRAPH_TYPE_NAME, "paragraph");

    client = TransportClient.builder().build()
            .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort));

  }


  @Override
  public List<NoteInfo> list() throws IOException {
    SearchResponse response = client.prepareSearch(indexName)
            .setTypes(noteTypeName)
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .execute()
            .actionGet();

    List<NoteInfo> results = null;
    SearchHits hits = response.getHits();
    long count = hits.getTotalHits();
    if (count > 0) {
      results = new ArrayList<NoteInfo>((int) count);
      for (SearchHit hit : hits.getHits()) {
        Note noteParsed = GsonUtil.fromJson(hit.getSourceAsString(), Note.class);
        results.add(new NoteInfo(noteParsed));
      }

    }

    return results;
  }

  @Override
  public Note get(String noteId) throws IOException {
    if (null == noteId || noteId.isEmpty()) {
      LOG.error("noteId can't be null");
      return null;
    }

    GetResponse response = client.prepareGet(indexName, noteTypeName, noteId).execute()
            .actionGet();

    if (!response.isExists()) {
      return null;
    }

    return GsonUtil.fromJson(response.getSourceAsString(), Note.class);

  }

  @Override
  public void save(Note note) throws IOException {
    IndexResponse noteResponse = client.prepareIndex(this.indexName, this.noteTypeName, note.id()).setOpType(IndexRequest.OpType.INDEX)
            .setSource(GsonUtil.toJson(note, true))
            .get();
    LOG.debug("note indexed success,id={}", noteResponse.getId());

    List<Paragraph> paras = note.getParagraphs();
    if (paras != null && paras.size() > 0) {
      BulkRequestBuilder bulkIndex = client.prepareBulk();

      for (Paragraph para : paras) {
        String paraJson = GsonUtil.toJson(para);
        IndexRequestBuilder paragraphIndexRequest = client.prepareIndex(this.indexName, this.paragraphTypeName, formatId(note.getId(), para)).setOpType(IndexRequest.OpType.INDEX).setParent(note.getId()).setSource(paraJson);
        bulkIndex.add(paragraphIndexRequest);
      }

      BulkResponse bulkResponse = bulkIndex.execute().actionGet();
      BulkItemResponse[] bulkItemResponses = bulkResponse.getItems();

      int successCount = 0;
      for (BulkItemResponse res : bulkItemResponses) {
        if (res.getFailure() == null) {
          successCount++;
        }
      }

      LOG.debug("paragraphs index success={},failed={}", successCount, (bulkItemResponses.length - successCount));
    }

  }


  /**
   * TODO: duplicate code in LuceneSearch
   */
  static String formatId(String noteId, Paragraph p) {
    String id = noteId;
    if (null != p) {
      id = Joiner.on('/').join(id, PARAGRAPH, p.getId());
    }
    return id;
  }


  @Override
  public void remove(String noteId) throws IOException {
    DeleteResponse response = client.prepareDelete(this.indexName, this.noteTypeName, noteId).get();

    if (response.isFound()) {
      LOG.debug("note id={},delete successfully", noteId);
    } else {
      LOG.warn("note id={},not found", noteId);
    }
  }

  /**
   * TODO: 1)实现aggregation,按照createdBy、lastModifiedTime和tag
   *
   * @param queryStr queryString in search box
   */
  @Override
  public List<Map<String, String>> query(String queryStr) {
    return null;
  }

  @Override
  public void updateIndexDoc(Note note) throws IOException {

  }

  @Override
  public void addIndexDocs(Collection<Note> collection) {

  }

  @Override
  public void addIndexDoc(Note note) {
    try {
      this.save(note);
    } catch (IOException e) {
      LOG.error("index failed", e);
    }
  }

  /**
   * user <code>PrefixQueryBuilder</code> to delete parent note, son paragraph delete tooo? TODO
   */
  @Override
  public void deleteIndexDocs(Note note) {

  }

  /**
   * modeled as parent-child relation,so paragraph can be delete/update alone
   * https://www.elastic.co/guide/en/elasticsearch/guide/master/parent-child.html
   */
  @Override
  public void deleteIndexDoc(Note note, Paragraph p) {

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
