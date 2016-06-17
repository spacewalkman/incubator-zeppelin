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
import org.elasticsearch.index.query.HasParentQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
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
  private static final String SEARCH_FIELD_TEXT = "text";
  private static final String SEARCH_FIELD_MODIFIED = "modifed";

  static final String PARAGRAPH = "paragraph";
  static final String ID_FIELD = "_id";

  private static final String CONFIG_REPO_ES_HOST = "zeppelin.es.search.repo.host";
  private static final String CONFIG_REPO_ES_PORT = "zeppelin.es.search.repo.port";
  private static final String CONFIG_REPO_ES_INDEX_NAME = "zeppelin.es.search.repo.index.name";
  private static final String CONFIG_REPO_ES_NOTE_TYPE_NAME = "zeppelin.es.search.repo.index.type";
  private static final String CONFIG_REPO_ES_PARAGRAPH_TYPE_NAME = "zeppelin.es.search.repo.paragraph.type";
  private static final String CONFIG_REPO_ES_PAGE_SIZE_NAME = "zeppelin.es.search.repo.page.size";

  private Client client;
  private String indexName;
  private String noteTypeName;
  private String paragraphTypeName;//note-to-pargraph modeled as parent-to-child relation in ES
  private int pageSize;


  public ElasticSearchRepo(ZeppelinConfiguration conf) throws IOException {
    String esHost = conf.getString(CONFIG_REPO_ES_HOST, "localhost");
    int esPort = conf.getInt(CONFIG_REPO_ES_PORT, 9300);

    this.indexName = conf.getString(CONFIG_REPO_ES_INDEX_NAME, "zeppelin");
    this.noteTypeName = conf.getString(CONFIG_REPO_ES_NOTE_TYPE_NAME, "note");
    this.paragraphTypeName = conf.getString(CONFIG_REPO_ES_PARAGRAPH_TYPE_NAME, "paragraph");
    this.pageSize = conf.getInt(CONFIG_REPO_ES_PAGE_SIZE_NAME, DEFUALT_PAGE_SIZE);

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

    Note note = GsonUtil.fromJson(response.getSourceAsString(), Note.class);

    //find children(paragraphs) by parent(note) ,mantain paragraphs order
    HasParentQueryBuilder hasParentQueryBuilder = QueryBuilders.hasParentQuery(noteTypeName, QueryBuilders.prefixQuery(ID_FIELD, noteId));
    SearchResponse paragraphsResponse = client.prepareSearch(indexName).setTypes(paragraphTypeName).setQuery(hasParentQueryBuilder).addSort(ID_FIELD, SortOrder.ASC).execute().actionGet();

    SearchHits hits = paragraphsResponse.getHits();
    long count = hits.getTotalHits();
    if (count > 0) {
      for (SearchHit hit : hits.getHits()) {
        Paragraph paragraph = GsonUtil.fromJson(hit.getSourceAsString(), Paragraph.class);
        note.addParagraph(paragraph);
      }
    }

    return note;
  }

  @Override
  public void save(Note note) throws IOException {
    IndexResponse noteResponse = client.prepareIndex(this.indexName, this.noteTypeName, note.id()).setOpType(IndexRequest.OpType.INDEX)
            .setSource(GsonUtil.toJson(note, true))
            .get();
    LOG.debug("note indexed success,id={}", noteResponse.getId());

    List<Paragraph> paras = note.getParagraphs();
    if (paras != null && paras.size() > 0) {
      int size = paras.size();
      BulkRequestBuilder bulkIndex = client.prepareBulk();

      int padLeft = getPadding(size);
      String padding = padLeft > 0 ? "%0" + (padLeft + 1) + "d" : "%d";

      for (int i = 0; i < size; i++) {
        Paragraph para = paras.get(i);
        String paraJson = GsonUtil.toJson(para);

        String savedParagraphId = formatId(note.getId(), String.format(padding, i));
        IndexRequestBuilder paragraphIndexRequest = client.prepareIndex(this.indexName, this.paragraphTypeName, savedParagraphId).setOpType(IndexRequest.OpType.INDEX).setParent(note.getId()).setSource(paraJson);
        bulkIndex.add(paragraphIndexRequest);
      }

      BulkResponse bulkResponse = bulkIndex.execute().actionGet();
      BulkItemResponse[] bulkItemResponses = bulkResponse.getItems();

      int successCount = getSuccessCount(bulkItemResponses);
      LOG.debug("paragraphs index success={},failed={}", successCount, (bulkItemResponses.length - successCount));
    }

  }

  private int getSuccessCount(BulkItemResponse[] bulkItemResponses) {
    int successCount = 0;
    for (BulkItemResponse res : bulkItemResponses) {
      if (res.getFailure() == null) {
        successCount++;
      }
    }
    return successCount;
  }

  /**
   * left padding with zeros
   */
  private int getPadding(int size) {
    int padding = 0;
    int mod10 = size / 10;
    while (mod10 > 0) {
      padding++;
      mod10 /= 10;
    }
    return padding;
  }


  /**
   * paragraph id={noteId}/paragraph/{index}, so when search paragraphs,can be sorted by
   * id,paragraph.id is not used
   */
  static String formatId(String noteId, String paddedIndex) {
    String id = noteId;
    return Joiner.on('_').join(id, PARAGRAPH, paddedIndex);
  }


  @Override
  public void remove(String noteId) throws IOException {
    //query note's pargraphs and delete
    HasParentQueryBuilder qb = QueryBuilders.hasParentQuery(noteTypeName, QueryBuilders.prefixQuery(ID_FIELD, noteId));

    SearchResponse paragraphsResponse = client.prepareSearch(indexName).setTypes(paragraphTypeName).setQuery(qb).setNoFields().execute().actionGet();
    SearchHits hits = paragraphsResponse.getHits();

    if (hits.getTotalHits() > 0) {
      BulkRequestBuilder bulkDeleteParagraphs = client.prepareBulk();
      for (SearchHit hit : paragraphsResponse.getHits()) {
        String paragraphId = (String) (hit.getSource().get(ID_FIELD));
        bulkDeleteParagraphs.add(client.prepareDelete(this.indexName, this.paragraphTypeName, paragraphId));
      }

      BulkResponse paraDeleteBuldResponse = bulkDeleteParagraphs.execute().actionGet();
      BulkItemResponse[] bulkItemResponses = paraDeleteBuldResponse.getItems();
      int successCount = getSuccessCount(bulkItemResponses);
      LOG.debug("paragraphs delete success={},failed={}", successCount, (bulkItemResponses.length - successCount));
    }

    //then, delete note
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
    HasParentQueryBuilder hasParentQueryBuilder = QueryBuilders.hasParentQuery(noteTypeName, QueryBuilders.multiMatchQuery(queryStr, SEARCH_FILED_TITLE, SEARCH_FIELD_TEXT));
    SearchResponse paragraphsResponse = client.prepareSearch(indexName).setTypes(paragraphTypeName).setQuery(hasParentQueryBuilder).addFields(SEARCH_FILED_TITLE, SEARCH_FIELD_TEXT).addHighlightedField(SEARCH_FILED_TITLE).addHighlightedField(SEARCH_FIELD_TEXT).execute().actionGet();

    SearchHits hits = paragraphsResponse.getHits();
    long count = hits.getTotalHits();
    if (count > 0) {
      for (SearchHit hit : hits.getHits()) {
        String hitString = hit.getSourceAsString();
        Map<String, HighlightField> highlightFieldMap = hit.getHighlightFields();
        LOG.debug("hitString={}", hitString);
        LOG.debug("highlightFieldMap={}", highlightFieldMap);
      }
    }
    return null;
  }


  @Override
  public void updateIndexDoc(Note note) throws IOException {
    this.save(note); //just replace,no partial update
  }

  //TODO:none-transactional
  @Override
  public void addIndexDocs(Collection<Note> collection) {
    if (collection != null && collection.size() > 0) {
      for (Note note : collection) {
        try {
          this.save(note);
        } catch (IOException e) {
          LOG.error("Failed to index note,id={}", note.getId(), e);
        }
      }
    }
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
   * use <code>PrefixQueryBuilder</code> to delete parent note, son paragraph delete too? TODO
   */
  @Override
  public void deleteIndexDocs(Note note) {
    try {
      this.remove(note.getId());
    } catch (IOException e) {
      LOG.error("delete note id={} failed", note.getId(), e);
    }
  }

  /**
   * modeled as parent-child relation,so paragraph can be delete/update alone
   */
  @Override
  public void deleteIndexDoc(Note note, Paragraph p) {
    //format paragraph id to {noteId}_paragraph_{index}
    List<Paragraph> paragraphs = note.getParagraphs();
    if (paragraphs.size() > 0) {
      int index = -1;
      for (int i = 0; i < paragraphs.size(); i++) {
        if (paragraphs.get(i).getId().equals(p.getId())) {
          index = i;
          break;
        }
      }

      if (index == -1) {
        return;
      }

      String indexParagraphId = Joiner.on('_').join(note.getId(), PARAGRAPH, index);

      DeleteResponse response = client.prepareDelete(this.indexName, this.paragraphTypeName, indexParagraphId).get();

      if (response.isFound()) {
        LOG.debug("paragraph indexed_id={},actual_id={},delete successfully", indexParagraphId, p.getId());
      } else {
        LOG.warn("paragraph indexed_id={},actual_id={},not found", indexParagraphId, p.getId());
      }
    }
  }

  @Override
  public void close() {
    if (null != client) {
      client.close();
    }
  }

  //TODO:(qy) below are really interface pollution
  @Override
  public Revision checkpoint(String noteId, String checkpointMsg) throws IOException {
    return null;//no checkpoint feature in ES
  }

  @Override
  public Note get(String noteId, Revision rev) throws IOException {
    return null;
  }

  @Override
  public List<Revision> revisionHistory(String noteId) {
    return null;
  }
}
