package org.apache.zeppelin.notebook.repo;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Paragraph;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
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
        return null;
    }

    @Override
    public Note get(String noteId) throws IOException {
        return null;
    }

    @Override
    public void save(Note note) throws IOException {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder()
                    .startObject();

            builder.field(SEARCH_FILED_TITLE, note.getName()).field(INDEX_FILED_USER, org.apache.shiro.SecurityUtils.getSubject());
            for (Paragraph para : note.getParagraphs()) {
                if (para == null || para.getText() == null) {
                    LOG.debug("Skipping empty paragraph");
                    continue;
                }
                builder.field(SEARCH_FIELD_TEXT, para.getText());

                if (para.getTitle() != null) {
                    builder.field(SEARCH_FILED_TITLE, para.getTitle());
                }

                Date date = para.getDateStarted() != null ? para.getDateStarted() : para.getDateCreated();
                builder.field(SEARCH_FIELD_MODIFIED, date.getTime());
            }

            builder.endObject();

            IndexResponse response = client.prepareIndex(this.indexName, this.typeName)
                    .setSource(builder.string())
                    .get();

            String idGenerated = response.getId();
            LOG.debug("index success,id={}", idGenerated);
        } catch (IOException e) {
            LOG.error("Failed to add note {} to index", note, e);
        }
    }

    @Override
    public void remove(String noteId) throws IOException {

    }

    @Override
    public void close() {
        if (null != client) {
            client.close();
        }
    }

    @Override
    public void checkpoint(String noteId, String checkPointName) throws IOException {

    }
}
