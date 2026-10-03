package io.github.trieunv.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.adapter.in.cli.AnswerRenderer;
import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AskConfig;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.exception.ModelOutputUnusableException;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seam 1, failure paths: every way an answer can fail ends in a fixed message written by code, and
 * a fallback shows no Sources, no model output and no error text.
 */
class AskFallbacksTest {

  private static final String ANSWERABLE = "Which card products did you build at Tamara?";
  private static final String PROVIDER_TEXT = "upstream said: invalid x-api-key sk-ant-123";

  @TempDir Path traceDir;
  @TempDir Path stateDir;

  private final AtomicInteger modelCalls = new AtomicInteger();
  private final AnswerRenderer renderer = new AnswerRenderer();

  @Test
  void shouldSayNotInSourcesWithoutCallingTheModel_whenNothingMatchesTheQuestion() {
    var answer = ask(AskFixture.CONFIG, "What is your favourite football club?", replying(false));

    assertNotInSources(answer);
    assertThat(modelCalls).hasValue(0);
  }

  @Test
  void shouldSayNotInSourcesWithoutCallingTheModel_whenNoEntryScoresAboveTheFloor() {
    var answer = ask(withFloor(1000), ANSWERABLE, replying(false));

    assertNotInSources(answer);
    assertThat(modelCalls).hasValue(0);
  }

  @Test
  void shouldSayNotInSourcesInsteadOfTheModelsClaims_whenTheModelSetsNotInSources() {
    var answer = ask(AskFixture.CONFIG, ANSWERABLE, replying(true));

    assertNotInSources(answer);
    assertThat(modelCalls).hasValue(1);
    assertThat(renderer.render(answer)).doesNotContain("card products");
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {
        "MODEL_TIMEOUT",
        "MODEL_UNAVAILABLE",
        "MODEL_OUTPUT_UNUSABLE",
        "MODEL_NOT_CONFIGURED"
      })
  void shouldSayCantAnswerRightNow_whenTheModelCallFails(ErrorCode errorCode) {
    var answer = ask(AskFixture.CONFIG, ANSWERABLE, failingWith(errorCode));

    assertThat(answer.getMode()).isEqualTo(AnswerMode.UNAVAILABLE);
    assertThat(renderer.render(answer))
        .isEqualTo("The assistant can't answer right now. Please try again.\n");
    assertThat(modelCalls).hasValue(1);
  }

  @Test
  void shouldSaySomethingWentWrongWithTheTraceId_whenAnythingUnexpectedFails() {
    var answer = ask(AskFixture.CONFIG, ANSWERABLE, throwingUnexpected());

    assertThat(answer.getMode()).isEqualTo(AnswerMode.ERROR);
    assertThat(renderer.render(answer))
        .isEqualTo(
            "Something went wrong. Please try again. (trace id: %s)\n"
                .formatted(answer.getTraceId().value()));
  }

  @Test
  void shouldShowNoSourcesClaimsOrErrorText_whenAnyFallbackIsRendered() {
    var answers =
        List.of(
            ask(AskFixture.CONFIG, "What is your favourite football club?", replying(false)),
            ask(AskFixture.CONFIG, ANSWERABLE, replying(true)),
            ask(AskFixture.CONFIG, ANSWERABLE, failingWith(ErrorCode.MODEL_UNAVAILABLE)),
            ask(AskFixture.CONFIG, ANSWERABLE, throwingUnexpected()));

    for (var answer : answers) {
      assertThat(answer.getClaims()).isEmpty();
      assertThat(answer.getSources()).isEmpty();
      assertThat(renderer.render(answer))
          .doesNotContain("Sources")
          .doesNotContain(PROVIDER_TEXT)
          .doesNotContain("Exception")
          .doesNotContain("\tat ");
    }
  }

  @Test
  void shouldKeepTheModelsRawOutputInTheTrace_whenItsOutputIsUnusable() throws Exception {
    ModelPort unusable =
        prompt -> {
          throw new ModelOutputUnusableException(
              "no schema", "Sure! I led 50 engineers.", 300, 20, null);
        };

    var answer = ask(AskFixture.CONFIG, ANSWERABLE, unusable);

    assertThat(answer.getMode()).isEqualTo(AnswerMode.UNAVAILABLE);
    var trace = onlyTrace();
    assertThat(trace.get("rawOutput").asString()).isEqualTo("Sure! I led 50 engineers.");
    assertThat(trace.get("inputTokens").asInt()).isEqualTo(300);
    assertThat(trace.get("provider").asString()).isEqualTo("stub");
    // The provider billed the call even though its output was unusable.
    assertThat(trace.get("costUsd").asDouble()).isPositive();
    assertThat(renderer.render(answer)).doesNotContain("50");
  }

  @Test
  void shouldStillAnswer_whenTheTraceCannotBeWritten() throws Exception {
    Files.writeString(traceDir.resolve("blocker"), "a file where the trace folder should be");
    var unwritable = traceDir.resolve("blocker");

    var answer =
        AskFixture.service(replying(false), AskFixture.CONFIG, unwritable, stateDir)
            .ask(AskQuestionCommand.builder().question(ANSWERABLE).build());

    assertThat(answer.getMode()).isEqualTo(AnswerMode.ANSWERED);
    assertThat(answer.getClaims()).hasSize(1);
  }

  @Test
  void shouldTraceTheModeAndFailure_whenAFallbackIsGiven() throws Exception {
    var answer = ask(AskFixture.CONFIG, ANSWERABLE, failingWith(ErrorCode.MODEL_TIMEOUT));

    var trace = onlyTrace();
    assertThat(trace.get("traceId").asString()).isEqualTo(answer.getTraceId().value());
    assertThat(trace.get("mode").asString()).isEqualTo("UNAVAILABLE");
    assertThat(trace.get("failure").asString()).startsWith("MODEL_TIMEOUT");
  }

  private JsonNode onlyTrace() throws IOException {
    try (var files = Files.list(traceDir)) {
      var lines = Files.readAllLines(files.toList().getFirst());
      assertThat(lines).hasSize(1);
      return JsonMapper.builder().build().readTree(lines.getFirst());
    }
  }

  private AnswerResult ask(AskConfig config, String question, ModelPort model) {
    return AskFixture.service(model, config, traceDir, stateDir)
        .ask(AskQuestionCommand.builder().question(question).build());
  }

  private static AskConfig withFloor(double minScore) {
    var base = AskFixture.CONFIG;
    return AskConfig.builder()
        .price(base.getPrice())
        .provider(base.getProvider())
        .modelId(base.getModelId())
        .retrievalTopK(base.getRetrievalTopK())
        .retrievalMinScore(minScore)
        .guardMaxDropRatio(base.getGuardMaxDropRatio())
        .promptVersion(base.getPromptVersion())
        .build();
  }

  private ModelPort throwingUnexpected() {
    return prompt -> {
      modelCalls.incrementAndGet();
      throw new IllegalStateException(PROVIDER_TEXT);
    };
  }

  private ModelPort replying(boolean notInSources) {
    return prompt -> {
      modelCalls.incrementAndGet();
      return ModelReply.builder()
          .provider("stub")
          .modelId("stub-model")
          .rawOutput("{\"raw\":true}")
          .answer(
              new ModelAnswer(
                  List.of(
                      new Claim(
                          "I built two card products.",
                          List.of(EntryId.of("fixture.role.tamara.b1")))),
                  notInSources))
          .build();
    };
  }

  private ModelPort failingWith(ErrorCode errorCode) {
    return prompt -> {
      modelCalls.incrementAndGet();
      throw new AssistantApplicationException(
          errorCode, PROVIDER_TEXT, new RuntimeException(PROVIDER_TEXT));
    };
  }

  private void assertNotInSources(AnswerResult answer) {
    assertThat(answer.getMode()).isEqualTo(AnswerMode.NOT_IN_SOURCES);
    assertThat(renderer.render(answer)).isEqualTo("That isn't in my sources.\n");
  }
}
