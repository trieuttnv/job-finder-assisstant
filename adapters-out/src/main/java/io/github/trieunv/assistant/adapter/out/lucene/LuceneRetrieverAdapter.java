package io.github.trieunv.assistant.adapter.out.lucene;

import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.port.out.RetrieverPort;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.QuestionScope;
import io.github.trieunv.assistant.domain.knowledge.RetrievalVocabulary;
import io.github.trieunv.assistant.domain.knowledge.RetrievedEntry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;

/**
 * In-memory Lucene BM25 over each Entry's Quote and typed fields (ADR 0002). A question that names
 * an employer is filtered to that employer's Entries; one that names a technology or an Entry type
 * ranks matching Entries higher. Each matched bullet then brings its role, so the
 * model always sees the employer and dates. The index is built once at startup.
 */
public class LuceneRetrieverAdapter implements RetrieverPort {

  private static final String TEXT = "text";
  private static final String EMPLOYER = "employer";
  private static final String TYPE = "type";
  private static final String ORDINAL = "ordinal";
  /** Extra weight for an Entry that matches the question and also a technology or type it names. */
  private static final float NAMED_SCOPE_BOOST = 2.0f;

  private final List<Entry> entries;
  private final Map<EntryId, Entry> entriesById;
  private final RetrievalVocabulary vocabulary;
  private final int topK;
  private final Analyzer analyzer = new EnglishAnalyzer();
  private final IndexSearcher searcher;

  public LuceneRetrieverAdapter(List<Entry> entries, RetrievalVocabulary vocabulary, int topK) {
    this.entries = List.copyOf(entries);
    this.entriesById =
        this.entries.stream()
            .collect(
                Collectors.toMap(
                    Entry::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    this.vocabulary = vocabulary;
    this.topK = topK;
    this.searcher = new IndexSearcher(index(this.entries));
  }

  @Override
  public List<RetrievedEntry> retrieve(String question) {
    if (question.isBlank()) {
      return List.of();
    }
    try {
      var matched = new ArrayList<RetrievedEntry>();
      var storedFields = searcher.storedFields();
      for (var hit : searcher.search(query(question), topK).scoreDocs) {
        var ordinal = storedFields.document(hit.doc).getField(ORDINAL).numericValue().intValue();
        matched.add(RetrievedEntry.matched(entries.get(ordinal), hit.score));
      }
      return withRoles(matched);
    } catch (ParseException e) {
      // An escaped question only fails to parse when nothing searchable is left (all stop words).
      return List.of();
    } catch (IOException e) {
      throw new AssistantApplicationException(
          ErrorCode.RETRIEVAL_FAILED, "In-memory index search failed", e);
    }
  }

  /**
   * The question's words must match, and a named technology or type only ranks those matches
   * higher, so a type word can neither pull in unrelated Entries nor lift an unrelated question
   * over the Input Guardrail floor. A question that asks for a type and nothing else ("Tell me
   * about your education") matches that type's Entries instead. A named employer filters.
   */
  private Query query(String question) throws ParseException {
    var scope = vocabulary.scopeOf(question);
    var parser = new QueryParser(TEXT, analyzer);
    var query = new BooleanQuery.Builder();
    if (scope.typeOnly()) {
      var anyType = new BooleanQuery.Builder();
      for (var type : scope.types()) {
        anyType.add(new TermQuery(new Term(TYPE, type.code())), Occur.SHOULD);
      }
      query.add(anyType.build(), Occur.MUST);
    } else {
      query.add(parser.parse(QueryParser.escape(question)), Occur.MUST);
      for (var technology : scope.technologies()) {
        var phrase = parser.parse('"' + QueryParser.escape(technology) + '"');
        query.add(new BoostQuery(phrase, NAMED_SCOPE_BOOST), Occur.SHOULD);
      }
      for (var type : scope.types()) {
        var typeQuery = new TermQuery(new Term(TYPE, type.code()));
        query.add(new BoostQuery(typeQuery, NAMED_SCOPE_BOOST), Occur.SHOULD);
      }
    }
    employerFilter(scope).ifPresent(filter -> query.add(filter, Occur.FILTER));
    return query.build();
  }

  private static Optional<Query> employerFilter(QuestionScope scope) {
    if (scope.employers().isEmpty()) {
      return Optional.empty();
    }
    var anyEmployer = new BooleanQuery.Builder();
    for (var employer : scope.employers()) {
      anyEmployer.add(new TermQuery(new Term(EMPLOYER, employer)), Occur.SHOULD);
    }
    return Optional.of(anyEmployer.build());
  }

  /** Matches in rank order, each bullet followed by its role unless the role is already there. */
  private List<RetrievedEntry> withRoles(List<RetrievedEntry> matched) {
    var matchedIds = matched.stream().map(hit -> hit.entry().id()).collect(Collectors.toSet());
    var expandedIds = new HashSet<EntryId>();
    var result = new ArrayList<RetrievedEntry>();
    for (var hit : matched) {
      result.add(hit);
      Optional.ofNullable(hit.entry().parent())
          .map(entriesById::get)
          .filter(role -> !matchedIds.contains(role.id()) && expandedIds.add(role.id()))
          .ifPresent(role -> result.add(RetrievedEntry.expandedFrom(role, hit)));
    }
    return result;
  }

  private DirectoryReader index(List<Entry> entries) {
    var directory = new ByteBuffersDirectory();
    try (var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
      for (var ordinal = 0; ordinal < entries.size(); ordinal++) {
        var entry = entries.get(ordinal);
        var document = new Document();
        document.add(new StoredField(ORDINAL, ordinal));
        document.add(new TextField(TEXT, searchableText(entry), Field.Store.NO));
        document.add(new StringField(TYPE, entry.type().code(), Field.Store.NO));
        for (var employer : employersOf(entry)) {
          document.add(new StringField(EMPLOYER, employer, Field.Store.NO));
        }
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

  /**
   * A role's own employer, or a bullet's role's employer. An Entry with neither (a summary, a
   * skill group) belongs to every employer its Quote names, so a filter on one keeps it.
   */
  private Set<String> employersOf(Entry entry) {
    return Optional.ofNullable(entry.employer())
        .or(() -> Optional.ofNullable(entry.parent()).map(entriesById::get).map(Entry::employer))
        .map(Set::of)
        .orElseGet(() -> vocabulary.employersNamedIn(entry.quote()));
  }

  private static String searchableText(Entry entry) {
    return Stream.of(entry.quote(), entry.employer(), entry.title(), entry.start(), entry.end())
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }
}
