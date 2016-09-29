package org.apache.zeppelin.notebook.repo;


import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.commit.SubmitLeftOver;
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
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.HasParentQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * ElasticSearch-backed NoteRepo and Service implementation
 */
public class ElasticSearchRepo implements NotebookRepo, SearchService {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchRepo.class);

  private static final String SEARCH_FIELD_TITLE = "title";
  private static final String SEARCH_FIELD_TEXT = "text";

  private static final String PARAGRAPH = "paragraph";
  private static final String ID_FIELD = "_id";
  private static final String PARAGRAPH_INDEX_FIELD = "paraIndex";

  private static final String AGGREGATION_FILED_TAGS = "tags";
  private static final String AGGREGATION_NAME_TAGS = "tags_aggs";

  private static final String AGGREGATION_FILED_AUTHOR = "createdBy";
  private static final String AGGREGATION_NAME_AUTHOR = "createdBy_agg";

  private static final String AGGREGATION_FILED_LAST_UPDATED = "lastUpdated";
  private static final String AGGREGATION_NAME_LAST_UPDATED = "lastUpdated_agg";

  private static final String DATE_RANGE_FORMAT = "yyyy-MM";

  private Client client;
  private String indexName;
  private String noteTypeName;
  /**
   * note-to-pargraph modeled as parent-to-child relation in ES
   */
  private String paragraphTypeName;
  private int defaultPageSize;
  private int defaultTermsAggSize;
  private int defaultScrollTimeOut;//es croll时的timeout
  private int defatlScrollMaxPerShard;//es scroll时的每个shard返回的最大结果集 TODO:可能存在bug，Deep pagination时可能造成paragraph被shard忽略掉，list时造成note列表显示不全（超级管理员能看所有，会有问题），get时造成note的paragraphs不全


  public ElasticSearchRepo(ZeppelinConfiguration conf) throws IOException {
    String esHost = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_ES_HOST);
    int esPort = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_ES_PORT);

    this.indexName = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_ES_INDEX_NAME);
    this.noteTypeName = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_ES_NOTE_TYPE_NAME);
    this.paragraphTypeName = conf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_ES_PARAGRAPH_TYPE_NAME);
    this.defaultPageSize = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_SEARCH_PAGE_SIZE);
    this.defaultTermsAggSize = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_REPO_ES_TERMS_AGGREGATION_SIZE);
    this.defaultScrollTimeOut = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_SEARCH_SCROLL_TIME_OUT);
    this.defatlScrollMaxPerShard = conf.getInt(ZeppelinConfiguration.ConfVars.ZEPPELIN_NOTE_SEARCH_SCROLL_MAX_RESULT_PER_SHARD);

//    Settings settings = Settings.settingsBuilder()
//            .put("cluster.name", "zeppelin").build();


    TransportClient transportClient = null;
    try {
      transportClient = TransportClient.builder().build()
              .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(esHost), esPort));
    } catch (Exception e) {
      e.printStackTrace();
    }

    client = transportClient;

    if (transportClient.connectedNodes() == null || transportClient.connectedNodes().size() == 0) {
      throw new IOException(String.format("There are no active nodes available for the transport@%s:%s", esHost, esPort));
    }
  }


  /**
   * use ES scroll api to handler large dataset
   *
   * @param principal contains user information.
   * @return notes from all shards within scroll time range
   */
  @Override
  public List<NoteInfo> list(String principal) throws IOException {
    SearchResponse scrollResp = client.prepareSearch(indexName).setTypes(noteTypeName)
            .addSort(AGGREGATION_FILED_LAST_UPDATED, SortOrder.DESC)
            .setScroll(new TimeValue(defaultScrollTimeOut))
            .setQuery(principal == null ? QueryBuilders.matchAllQuery() : QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery(AGGREGATION_FILED_AUTHOR, principal)))
//            .addAggregation(AggregationBuilders.terms(AGGREGATION_NAME_TAGS).field(AGGREGATION_FILED_TAGS).size(defaultTermsAggSize))
//            .addAggregation(AggregationBuilders.terms(AGGREGATION_NAME_AUTHOR).field(AGGREGATION_FILED_AUTHOR).size(defaultTermsAggSize))
//            .addAggregation(AggregationBuilders.dateHistogram(AGGREGATION_NAME_LAST_UPDATED).field(AGGREGATION_FILED_LAST_UPDATED).interval(DateHistogramInterval.MONTH).format(DATE_RANGE_FORMAT))
            .setSize(defatlScrollMaxPerShard).execute().actionGet();

    List<NoteInfo> results = new LinkedList<NoteInfo>();
    //Scroll until no hits are returned
    while (true) {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        //handle hitted note
        Note noteParsed = GsonUtil.fromJson(hit.getSourceAsString(), Note.class);
        results.add(new NoteInfo(noteParsed));

        //aggregation parse
        //TODO: search with aggs,fields tags/topic?(qy)
//        Aggregations aggregations = scrollResp.getAggregations();
//        termsAggregationParse(aggregations, AGGREGATION_NAME_TAGS);
//        termsAggregationParse(aggregations, AGGREGATION_NAME_AUTHOR);
//        dateHistogramAggregationParse(aggregations, AGGREGATION_NAME_LAST_UPDATED);
      }
      scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(new TimeValue(defaultScrollTimeOut)).execute().actionGet();
      //Break condition: No hits are returned
      if (scrollResp.getHits().getHits().length == 0) {
        break;
      }
    }

    return results;
  }

  /**
   * parse date histogram aggregation
   */
  private void dateHistogramAggregationParse(Aggregations aggregations, String aggName) {
    if (aggregations != null) {
      Histogram agg = aggregations.get(aggName);
      // For each entry
      for (Histogram.Bucket entry : agg.getBuckets()) {
        DateTime key = (DateTime) entry.getKey();    // Key
        String keyAsString = entry.getKeyAsString(); // Key as String
        long docCount = entry.getDocCount();         // Doc count

        LOG.info("key [{}], date [{}], doc_count [{}]", keyAsString, key.getYear(), docCount);
      }

    }
  }

  private void termsAggregationParse(Aggregations aggregations, String aggName) {
    if (aggregations != null) {
      StringTerms terms = aggregations.get(aggName);
      Collection<Terms.Bucket> buckets = terms.getBuckets();

      for (Terms.Bucket buck : buckets) {
        LOG.debug("{}={}", buck.getKeyAsString(), buck.getDocCount());
      }
    }
  }

  @Override
  public Note get(String noteId, String principal) throws IOException {
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

    //find children(paragraphs) by parent(note) ,maintain paragraphs order
    HasParentQueryBuilder hasParentQueryBuilder = QueryBuilders.hasParentQuery(noteTypeName, QueryBuilders.prefixQuery(ID_FIELD, noteId));
    SearchResponse scrollResp = client.prepareSearch(indexName).setTypes(paragraphTypeName).setQuery(hasParentQueryBuilder)
            .addSort(PARAGRAPH_INDEX_FIELD, SortOrder.ASC).addSort(ID_FIELD, SortOrder.ASC)  //第一排序是paraIndex，第二排序是paragraph的id
            .setScroll(new TimeValue(defaultScrollTimeOut)).setSize(defatlScrollMaxPerShard).execute().actionGet();

    //Scroll until no hits are returned
    while (true) {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        //handle hitted note
        Paragraph paragraph = GsonUtil.fromJson(hit.getSourceAsString(), Paragraph.class);
        note.addParagraph(paragraph);
      }
      scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(new TimeValue(defaultScrollTimeOut)).execute().actionGet();
      //Break condition: No hits are returned
      if (scrollResp.getHits().getHits().length == 0) {
        break;
      }
    }

    return note;
  }

  @Override
  public void save(Note note, String principal) throws IOException {
    indexNoteAndParagraphs(note);
  }

  /**
   * index note and paragraphs associate with it
   */
  private void indexNoteAndParagraphs(Note note) {
    doIndexNoteOnly(note);
    doIndexParagraphs(note);
  }

  public void doIndexNoteOnly(Note note) {
    IndexResponse noteResponse = client.prepareIndex(this.indexName, this.noteTypeName, note.getId()).setOpType(IndexRequest.OpType.INDEX)
            .setSource(GsonUtil.toJson(note, true))
            .get();
    LOG.debug("note indexed success,id={}", noteResponse.getId());
  }

  /**
   * index paragraphs only
   */
  private void doIndexParagraphs(Note note) {
    List<Paragraph> paras = note.getParagraphs();
    if (paras != null && paras.size() > 0) {
      int size = paras.size();
      BulkRequestBuilder bulkIndex = client.prepareBulk();

      //由于没有记录之前的paragraph的个数，而原来是按照paragraph id排序的，更新的时候可能para的个数可能从2位数变成1位数，对ES来说会增加新的paragraph。故需要先删除原来的note
      this.deleteChildren(note.getId());

      for (int i = 0; i < size; i++) {
        Paragraph para = paras.get(i);
        para.setParaIndex(i);//保持paragraph之间的相对顺序
        String paraJson = GsonUtil.toJson(para);
        String esParagraphId = formatId(note.getId(), i + "");

        IndexRequestBuilder paragraphIndexRequest = client.prepareIndex(this.indexName, this.paragraphTypeName, esParagraphId)
                .setOpType(IndexRequest.OpType.INDEX)
                .setParent(note.getId()).setSource(paraJson);
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
   * left padding with zeros,to ensure that we can sort on _id field asc, to maintain paragraphs'
   * order
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
  static final String formatId(String noteId, String paddedIndex) {
    String id = noteId;
    return Joiner.on('_').join(id, PARAGRAPH, paddedIndex);
  }


  @Override
  public void remove(String noteId, String principal) throws IOException {
    this.deleteChildren(noteId);

    //then, delete note
    DeleteResponse response = client.prepareDelete(this.indexName, this.noteTypeName, noteId).get();
    if (response.isFound()) {
      LOG.debug("note id={},delete successfully", noteId);
    } else {
      LOG.warn("note id={}, not found", noteId);
    }
  }

  /**
   * 删除所有的note的children paragraphs
   *
   * @param noteId note的id
   */
  private void deleteChildren(String noteId) {
    //query note's pargraphs and delete
    HasParentQueryBuilder qb = QueryBuilders.hasParentQuery(noteTypeName, QueryBuilders.prefixQuery(ID_FIELD, noteId));

    SearchResponse paragraphsResponse = client.prepareSearch(indexName).setTypes(paragraphTypeName).setQuery(qb).setNoFields().setSize(defatlScrollMaxPerShard).execute().actionGet();
    SearchHits hits = paragraphsResponse.getHits();

    if (hits.getTotalHits() > 0) {
      BulkRequestBuilder bulkDeleteParagraphs = client.prepareBulk();
      for (SearchHit hit : hits) {
        bulkDeleteParagraphs.add(client.prepareDelete(this.indexName, this.paragraphTypeName, hit.getId()).setParent(noteId));
      }

      BulkResponse paraDeleteBuldResponse = bulkDeleteParagraphs.execute().actionGet();
      BulkItemResponse[] bulkItemResponses = paraDeleteBuldResponse.getItems();
      int successCount = getSuccessCount(bulkItemResponses);
      LOG.debug("paragraphs delete success={},failed={}", successCount, (bulkItemResponses.length - successCount));
    }
  }

  /**
   * TODO: 1)实现aggregation,按照createdBy、lastModifiedTime和tag
   *
   * query paragraphs
   *
   * @param queryStr queryString in search box
   */
  @Override
  public List<Map<String, String>> query(String queryStr, int size, int from) {
     /*
      {
          "query" : {
            "multi_match" : {
              "query" : "Hortonworks",
               "fields" : [ "text", "title" ]
            }
          },
          "highlight" : {
            "fields" : {
              "text" : {},
              "title": {}
            }
          }
      }
    */
    if (size <= 0) {
      size = this.defaultPageSize;
    }
    if (from < 0) {
      from = 0;
    }

    MultiMatchQueryBuilder multiMatchQueryBuilder = QueryBuilders.multiMatchQuery(queryStr, SEARCH_FIELD_TITLE, SEARCH_FIELD_TEXT);
    SearchResponse paragraphsResponse = client.prepareSearch(indexName)
            .setTypes(paragraphTypeName) //TODO:add sorted,同一个note的在一起，lastUpdatedTime降序
            .setQuery(multiMatchQueryBuilder)
            .addFields(SEARCH_FIELD_TITLE, SEARCH_FIELD_TEXT)
            .addHighlightedField(SEARCH_FIELD_TITLE)
            .addHighlightedField(SEARCH_FIELD_TEXT)
            .setSize(size)
            .setFrom(from)
            .execute()
            .actionGet();

    SearchHits hits = paragraphsResponse.getHits();
    long count = hits.getTotalHits();
    if (count < 0) {
      return null;
    }

    List<Map<String, String>> matchingParagraphs = Lists.newArrayList();
    for (SearchHit hit : hits.getHits()) {
      String id = hit.getId();
      SearchHitField titleField = hit.getFields().get(SEARCH_FIELD_TITLE);
      String title = null;
      if (titleField != null) {
        title = titleField.getValue();
      }

      SearchHitField textField = hit.getFields().get(SEARCH_FIELD_TEXT);
      String text = null;
      if (textField != null) {
        text = textField.getValue();
      }

      //when multi_match, which field match can't be decided upfront, must iterate
      Map<String, HighlightField> highlightFieldMap = hit.getHighlightFields();
      HighlightField titleHighLightField = highlightFieldMap.get(SEARCH_FIELD_TITLE);
      Text[] titleFragments = null;
      if (titleHighLightField != null) {
        titleFragments = titleHighLightField.getFragments();
      }
      LOG.debug("title highlight fragments found={}", (Object[]) titleFragments);

      HighlightField textHighLightField = highlightFieldMap.get(SEARCH_FIELD_TEXT);
      Text[] textFragments = null;
      if (textHighLightField != null) {
        textFragments = textHighLightField.getFragments();
      }
      LOG.debug("text highlight fragments found={}", (Object[]) textFragments);

      String showFrament = "";//highlight fragment showing in UI
      if (titleFragments != null) {
        showFrament = titleFragments[0].string();
      }

      if (textFragments != null) {
        if (showFrament != null && showFrament.isEmpty()) {
          showFrament += "\n";
        }
        showFrament += textFragments[0].string();
      }

      matchingParagraphs.add(ImmutableMap.of("id", id,
              "name", title == null ? "" : title, "snippet", showFrament, "text", text == null ? "" : text, "header", title == null ? "" : title));
    }

    return matchingParagraphs;

  }

  @Override
  public void updateIndexDoc(Note note) throws IOException {
    this.indexNoteAndParagraphs(note); //just replace as a whole
  }

  /**
   * 只单独更新note的一个paragraph
   */
  @Override
  public void updateIndexParagraph(Note note, Paragraph para) throws IOException {
    this.doIndexNoteOnly(note);//update lastUpdated field

    String paraJson = GsonUtil.toJson(para);
    String noteId = note.getId();
    String savedParagraphId = formatId(noteId, note.getParagraphIndex(para) + "");
    IndexRequestBuilder paragraphIndexRequest = client.prepareIndex(this.indexName, this.paragraphTypeName, savedParagraphId)
            .setOpType(IndexRequest.OpType.INDEX)
            .setParent(noteId).setSource(paraJson).setParent(noteId);
    IndexResponse paraIndexResponse = paragraphIndexRequest.execute().actionGet();

    if (paraIndexResponse.isCreated()) {
      LOG.debug("paragraph={} created success", savedParagraphId);
    } else {
      LOG.debug("paragraph={} update success,now version={}", savedParagraphId, paraIndexResponse.getVersion());
    }
  }

  //TODO:none-transactional
  @Override
  public void addIndexDocs(Collection<Note> collection) {
    if (collection != null && collection.size() > 0) {
      for (Note note : collection) {
        try {
          this.save(note, null);
        } catch (IOException e) {
          LOG.error("Failed to index note, id={}", note.getId(), e);
        }
      }
    }
  }

  @Override
  public void addIndexDoc(Note note) {
    try {
      this.save(note, null);
    } catch (IOException e) {
      LOG.error("index failed", e);
    }
  }

  /**
   * use <code>PrefixQueryBuilder</code> to delete parent note, children paragraph delete too
   */
  @Override
  public void deleteIndexDocs(Note note) {
    try {
      this.remove(note.getId(), null);
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

      DeleteResponse response = client.prepareDelete(this.indexName, this.paragraphTypeName, indexParagraphId).setParent(note.getId()).get();

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
  public List<Revision> revisionHistory(String noteId, String principalt) {
    LOG.info("revisionHistory feature isn't supported in {}", this.getClass().toString());
    return null;
  }

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
