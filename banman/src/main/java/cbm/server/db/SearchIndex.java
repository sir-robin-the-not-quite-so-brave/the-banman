package cbm.server.db;

import cbm.server.model.Ban;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SearchIndex implements Closeable {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Analyzer analyzer = new StandardAnalyzer(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
    private final Directory directory;

    public SearchIndex(Path path) throws IOException {
        this.directory = new MMapDirectory(path);
    }

    @Override
    public void close() throws IOException {
        directory.close();
    }

    public void index(Map<String, Ban> bans) throws IOException {
        final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);

        try (final IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
            bans.forEach((id, ban) -> {
                if (ban.getPlayerName() == null && ban.getReason() == null)
                    return;
                LOGGER.debug("Indexing {}", ban);
                final Document document = toDocument(id, ban);
                try {
                    writer.addDocument(document);
                } catch (IOException e) {
                    LOGGER.warn("Failed to index ban: " + ban, e);
                }
            });
        }
    }

    public @NotNull SearchResponse<String> search(@NotNull SearchRequest request) throws ParseException, IOException {
        LOGGER.info("Request: {}", request);

        final Query query = new QueryParser("_text", analyzer)
                                    .parse(request.getQueryString());
        LOGGER.debug("Parsed query: {}", query);

        try (IndexReader indexReader = DirectoryReader.open(directory)) {
            final ScoreDoc after;
            final long from;
            if (request.getContinueAfter() != null) {
                final Tuple2<ScoreDoc, Long> tuple = parseContinueAfter(request.getContinueAfter());
                after = tuple.getT1();
                from = tuple.getT2();
            } else {
                after = null;
                from = 0;
            }
            final IndexSearcher searcher = new IndexSearcher(indexReader);
            final TopDocs topDocs = searcher.searchAfter(after, query, SearchRequest.PAGE_SIZE);
            final List<String> ids = new ArrayList<>(SearchRequest.PAGE_SIZE);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                final Document doc = searcher.doc(scoreDoc.doc);
                final String id = doc.get("id");
                ids.add(id);
            }

            final String continueAfter = continueAfter(from, topDocs);

            final SearchResponse<String> response =
                    new SearchResponse<>(from, topDocs.totalHits.value, ids, continueAfter);

            LOGGER.debug("Response: {}", response);

            return response;
        }
    }

    @Nullable
    private String continueAfter(long from, TopDocs topDocs) {
        final long nextFrom = from + topDocs.scoreDocs.length;
        if (topDocs.scoreDocs.length == 0 || nextFrom >= topDocs.totalHits.value)
            return null;

        final ScoreDoc last = topDocs.scoreDocs[topDocs.scoreDocs.length - 1];
        return String.format("%d:%d:%f", nextFrom, last.doc, last.score);
    }

    private @NotNull Tuple2<ScoreDoc, Long> parseContinueAfter(@NotNull String string) throws ParseException {
        final String[] split = string.split(":");
        if (split.length != 3)
            throw new ParseException();

        try {
            final long from = Long.parseLong(split[0]);
            final int doc = Integer.parseInt(split[1]);
            final float score = Float.parseFloat(split[2]);
            return Tuples.of(new ScoreDoc(doc, score), from);
        } catch (NumberFormatException e) {
            throw new ParseException(e.getMessage());
        }
    }

    private static Document toDocument(String id, Ban ban) {
        final String text = Stream.of(ban.getPlayerName(), ban.getReason())
                                  .filter(Objects::nonNull)
                                  .collect(Collectors.joining(" "));

        final Document document = new Document();

        document.add(new StringField("id", id, Field.Store.YES));
        if (ban.getPlayerName() != null)
            document.add(new TextField("name", ban.getPlayerName(), Field.Store.NO));
        if (ban.getReason() != null)
            document.add(new TextField("reason", ban.getReason(), Field.Store.NO));
        document.add(new TextField("_text", text, Field.Store.NO));

        return document;
    }
}
