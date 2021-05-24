package cbm.server.db;

import cbm.server.model.Ban;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SearchIndex implements Closeable {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Analyzer analyzer = new StandardAnalyzer();
    private final Directory directory;

    public SearchIndex(Path path) throws IOException {
        this.directory = new MMapDirectory(path);
    }

    @Override
    public void close() throws IOException {
        directory.close();
    }

    public void index(Stream<Ban> bans) throws IOException {
        final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);

        try (final IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
            bans.filter(ban -> ban.getPlayerName() != null || ban.getReason() != null)
                .peek(ban -> LOGGER.debug("Indexing {} ...", ban))
                .forEach(ban -> {
                    final Document document = toDocument(ban);
                    try {
                        writer.addDocument(document);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to index ban: " + ban, e);
                    }
                });
        }
    }

    public List<Ban> query(String queryString, int maxHits) throws ParseException, IOException {
        LOGGER.info("Query: {}", queryString);

        final Query query = new QueryParser("_text", analyzer)
                .parse(queryString);

        LOGGER.debug("Parsed query: {}", query);

        try (IndexReader indexReader = DirectoryReader.open(directory)) {
            final IndexSearcher searcher = new IndexSearcher(indexReader);
            final TopDocs topDocs = searcher.search(query, maxHits);
            final List<Ban> bans = new ArrayList<>(maxHits);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                final Document doc = searcher.doc(scoreDoc.doc);
                final String id = doc.get("id");
                final String name = doc.get("name");
                final String reason = doc.get("reason");
                final Ban ban =
                        new Ban.Builder()
                                .setId(id)
                                .setPlayerName(name)
                                .setReason(reason)
                                .build();
                bans.add(ban);
            }
            return bans;
        }
    }

    private static Document toDocument(Ban ban) {
        final String text = Stream.of(ban.getPlayerName(), ban.getReason())
                                  .filter(Objects::nonNull)
                                  .collect(Collectors.joining(" "));

        final Document document = new Document();

        document.add(new StringField("id", ban.getId(), Field.Store.YES));
        if (ban.getPlayerName() != null)
            document.add(new TextField("name", ban.getPlayerName(), Field.Store.YES));
        if (ban.getReason() != null)
            document.add(new TextField("reason", ban.getReason(), Field.Store.YES));
        document.add(new TextField("_text", text, Field.Store.NO));

        return document;
    }
}
