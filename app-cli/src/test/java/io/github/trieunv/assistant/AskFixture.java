package io.github.trieunv.assistant;

import io.github.trieunv.assistant.adapter.out.lucene.LuceneRetrieverAdapter;
import io.github.trieunv.assistant.adapter.out.state.JsonKnowledgeStateAdapter;
import io.github.trieunv.assistant.adapter.out.trace.JsonlTraceSinkAdapter;
import io.github.trieunv.assistant.adapter.out.yaml.YamlKnowledgeBasePersistenceAdapter;
import io.github.trieunv.assistant.application.dto.AskConfig;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.application.service.AskQuestionService;
import io.github.trieunv.assistant.application.service.LoadKnowledgeService;
import io.github.trieunv.assistant.domain.answer.ConflictNotices;
import io.github.trieunv.assistant.domain.answer.OutputGuardrail;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Wires the Ask use case for tests: the Synthetic fixture knowledge files, real BM25 retrieval, the
 * JSONL trace sink and whatever stub {@link ModelPort} the test scripts.
 */
final class AskFixture {

  static final Path FIXTURE_KNOWLEDGE = Path.of("src/test/resources/knowledge");
  static final SystemPrompt PROMPT = new SystemPrompt("answer-test", "Answer from Entries.");

  static final AskConfig CONFIG =
      AskConfig.builder()
          .provider("stub")
          .modelId("stub-model")
          .timeoutMs(20000)
          .maxOutputTokens(800)
          .retrievalTopK(6)
          .retrievalMinScore(0.1)
          .guardMaxDropRatio(0.5)
          .promptVersion("answer-test")
          .build();

  private AskFixture() {}

  static AskQuestionService service(
      ModelPort model, AskConfig config, Path traceDir, Path stateDir) {
    var knowledge =
        new LoadKnowledgeService(
                new YamlKnowledgeBasePersistenceAdapter(FIXTURE_KNOWLEDGE),
                new JsonKnowledgeStateAdapter(stateDir))
            .load();
    return new AskQuestionService(
        new LuceneRetrieverAdapter(
            knowledge.getEntries(),
            knowledge.getVocabulary(),
            knowledge.getConflicts(),
            config.getRetrievalTopK()),
        model,
        new JsonlTraceSinkAdapter(traceDir),
        PROMPT,
        config,
        Clock.systemUTC(),
        new OutputGuardrail(
            knowledge.getEntries(), knowledge.getKnownNames(), config.getGuardMaxDropRatio()),
        new ConflictNotices(knowledge.getConflicts(), knowledge.getEntries()));
  }
}
