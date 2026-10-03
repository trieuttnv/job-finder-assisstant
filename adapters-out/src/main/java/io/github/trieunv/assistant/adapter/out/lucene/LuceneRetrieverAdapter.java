package io.github.trieunv.assistant.adapter.out.lucene;

import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.port.out.RetrieverPort;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.RetrievedEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * In-memory Lucene BM25 over each Entry's Quote and typed fields (ADR 0002). The index is built
 * once at startup; the knowledge base is small and read-only.
 */
public class LuceneRetrieverAdapter implements RetrieverPort {

  private static final String TEXT = "text";
  private static final String ORDINAL = "ordinal";

  private final List<Entry> entries;
  private final int topK;
  private final Analyzer analyzer = new EnglishAnalyzer();
  private final IndexSearcher searcher;

  public LuceneRetrieverAdapter(List<Entry> entries, int topK) {
    this.entries = List.copyOf(entries);
    this.topK = topK;
    this.searcher = new IndexSearcher(index(this.entries));
  }

  @Override
  public List<RetrievedEntry> retrieve(String question) {
    if (question.isBlank()) {
      return List.of();
    }
    try {
      var query = new QueryParser(TEXT, analyzer).parse(QueryParser.escape(question));
      var storedFields = searcher.storedFields();
      var retrieved = new ArrayList<RetrievedEntry>();
      for (var hit : searcher.search(query, topK).scoreDocs) {
        var ordinal = storedFields.document(hit.doc).getField(ORDINAL).numericValue().intValue();
        retrieved.add(new RetrievedEntry(entries.get(ordinal), hit.score));
      }
      return retrieved;
    } catch (ParseException e) {
      // An escaped question only fails to parse when nothing searchable is left (all stop words).
      return List.of();
    } catch (IOException e) {
      throw new AssistantApplicationException(
          ErrorCode.RETRIEVAL_FAILED, "In-memory index search failed", e);
    }
  }

  private DirectoryReader index(List<Entry> entries) {
    var directory = new ByteBuffersDirectory();
    try (var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
      for (var ordinal = 0; ordinal < entries.size(); ordinal++) {
        var document = new Document();
        document.add(new StoredField(ORDINAL, ordinal));
        document.add(new TextField(TEXT, searchableText(entries.get(ordinal)), Field.Store.NO));
        writer.addDocument(document);
      }
    } catch (IOException e) {
      throw new AssistantApplicationException(
          ErrorCode.RETRIEVAL_FAILED, "Cannot build the in-memory index", e);
    }
    try {
      return DirectoryReader.open(directory);
    } catch (IOException e) {
      throw new AssistantApplicationException(
          ErrorCode.RETRIEVAL_FAILED, "Cannot open the in-memory index", e);
    }
  }

  private static String searchableText(Entry entry) {
    return Stream.of(entry.quote(), entry.employer(), entry.title(), entry.start(), entry.end())
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }
}
