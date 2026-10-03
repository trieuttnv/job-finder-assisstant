package io.github.trieunv.assistant.eval.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.trieunv.assistant.adapter.out.lucene.LuceneRetrieverAdapter;
import io.github.trieunv.assistant.adapter.out.state.JsonKnowledgeStateAdapter;
import io.github.trieunv.assistant.adapter.out.trace.JsonlTraceSinkAdapter;
import io.github.trieunv.assistant.adapter.out.yaml.YamlKnowledgeBasePersistenceAdapter;
import io.github.trieunv.assistant.application.dto.AskConfig;
import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.dto.TokenPrice;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.application.service.AskQuestionService;
import io.github.trieunv.assistant.application.service.LoadKnowledgeService;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ConflictNotices;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import io.github.trieunv.assistant.domain.answer.OutputGuardrail;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.eval.EvalItem;
import io.github.trieunv.assistant.eval.EvalItem.Category;
import io.github.trieunv.assistant.eval.EvalItem.Expectation;
import io.github.trieunv.assistant.eval.EvalItem.GoldLabel;
import io.github.trieunv.assistant.eval.EvalItem.Split;
import io.github.trieunv.assistant.eval.QuestionResult.DroppedClaim;
import io.github.trieunv.assistant.eval.QuestionResult.ShownClaim;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The runner against the real Ask pipeline and the real trace writer, on the real knowledge: every
 * number the scorer uses is read from the trace line the CLI's pipeline writes.
 */
class EvalRunnerTest {

  private static final EvalItem ATLASSIAN =
      new EvalItem(
          "q01",
          Split.DEV,
          Category.CV_DIRECT,
          null,
          "When did you work at Atlassian?",
          Expectation.ANSWER,
          new GoldLabel(List.of("cv.role.atlassian"), List.of(List.of("2014")), List.of()),
          null,
          true);

  @TempDir Path traceDir;
  @TempDir Path stateDir;

  @Test
  void shouldReadRetrievalDropsTokensLatencyAndCostFromTheTraceLine_whenRunningAnItem() {
    var claims =
        List.of(
            new Claim("I was there from Sep 2014 to Jul 2015.", ids("cv.role.atlassian")),
            new Claim("I was CTO at Goldman.", ids("cv.role.atlassian")));

    var run =
        new EvalRunner(useCase(claims), new TraceLines(traceDir)).run(List.of(ATLASSIAN), 2);

    assertThat(run.results()).hasSize(2);
    var result = run.results().getFirst();
    assertThat(result.mode()).isEqualTo(AnswerMode.ANSWERED);
    assertThat(result.claims())
        .containsExactly(
            new ShownClaim("I was there from Sep 2014 to Jul 2015.", List.of("cv.role.atlassian")));
    assertThat(result.retrievedIds()).contains("cv.role.atlassian");
    assertThat(result.retrieved().getFirst().score()).isPositive();
    assertThat(result.proposedClaims()).isEqualTo(2);
    assertThat(result.dropped())
        .containsExactly(
            new DroppedClaim(
                "I was CTO at Goldman.", List.of("cv.role.atlassian"), "NAME_NOT_IN_SOURCES"));
    assertThat(result.inputTokens()).isEqualTo(1000);
    assertThat(result.outputTokens()).isEqualTo(100);
    assertThat(result.costUsd()).isEqualTo(0.003);
    assertThat(run.results().get(1).rep()).isEqualTo(1);
    assertThat(run.config().path("modelId").asString()).isEqualTo("stub-model");
    assertThat(run.promptHash()).isNotBlank();
  }

  @Test
  void shouldStopTheRun_whenAnAnswerHasNoTraceLine() {
    var elsewhere = traceDir.resolve("other");

    assertThatThrownBy(
            () ->
                new EvalRunner(useCase(List.of()), new TraceLines(elsewhere))
                    .run(List.of(ATLASSIAN), 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No trace line for q01");
  }

  private AskQuestionUseCase useCase(List<Claim> claims) {
    var knowledge =
        new LoadKnowledgeService(
                new YamlKnowledgeBasePersistenceAdapter(Path.of("../knowledge")),
                new JsonKnowledgeStateAdapter(stateDir))
            .load();
    var config =
        AskConfig.builder()
            .provider("stub")
            .modelId("stub-model")
            .retrievalTopK(6)
            .retrievalMinScore(0.5)
            .guardMaxDropRatio(0.5)
            .price(new TokenPrice(2.0, 10.0))
            .promptVersion("answer-test")
            .build();
    return new AskQuestionService(
        new LuceneRetrieverAdapter(
            knowledge.getEntries(), knowledge.getVocabulary(), knowledge.getConflicts(), 6),
        prompt ->
            ModelReply.builder()
                .provider("stub")
                .modelId("stub-model")
                .rawOutput("{}")
                .answer(new ModelAnswer(claims, claims.isEmpty()))
                .inputTokens(1000)
                .outputTokens(100)
                .build(),
        new JsonlTraceSinkAdapter(traceDir),
        new SystemPrompt("answer-test", "Answer from Entries."),
        config,
        Clock.systemUTC(),
        new OutputGuardrail(knowledge.getEntries(), knowledge.getKnownNames(), 0.5),
        new ConflictNotices(knowledge.getConflicts(), knowledge.getEntries()));
  }

  private static List<EntryId> ids(String... ids) {
    return Arrays.stream(ids).map(EntryId::of).toList();
  }
}
