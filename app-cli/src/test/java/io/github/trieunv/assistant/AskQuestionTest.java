package io.github.trieunv.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.trieunv.assistant.adapter.in.cli.AnswerRenderer;
import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AnsweredClaim;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Seam 1: the Ask use case, wired by {@link AskFixture} with a scripted stub {@link ModelPort}. */
class AskQuestionTest {

  private static final SystemPrompt PROMPT = AskFixture.PROMPT;

  private final AnswerRenderer renderer = new AnswerRenderer();

  @TempDir Path traceDir;
  @TempDir Path stateDir;

  @Test
  void shouldShowClaimWithNumberedSource_whenItCitesARetrievedEntry() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built two card products from scratch at Tamara.", "fixture.role.tamara.b1"));

    assertThat(answer.getMode()).isEqualTo(AnswerMode.ANSWERED);
    assertThat(answer.getClaims())
        .extracting(AnsweredClaim::getText, AnsweredClaim::getCitations)
        .containsExactly(tuple("I built two card products from scratch at Tamara.", List.of(1)));
    assertThat(answer.getSources())
        .extracting(
            SourceReference::getNumber,
            SourceReference::getEntryId,
            SourceReference::getSource,
            SourceReference::getQuote)
        .containsExactly(
            tuple(1, "fixture.role.tamara.b1", "fixture", "Built two card products from scratch"));
    assertThat(answer.getRemovedClaims()).isZero();
  }

  @Test
  void shouldNumberSourcesInOrderOfFirstCitation_whenClaimsShareAnEntry() {
    var answer =
        ask(
            "Which card products did you build, and how big was the engineering team at Tamara?",
            claim("I built two card products.", "fixture.role.tamara.b1"),
            claim(
                "I grew the team to 13 engineers at Tamara.",
                "fixture.role.tamara.b2",
                "fixture.role.tamara"),
            claim("Both happened at Tamara.", "fixture.role.tamara.b1", "fixture.role.tamara"));

    assertThat(answer.getClaims())
        .extracting(AnsweredClaim::getCitations)
        .containsExactly(List.of(1), List.of(2, 3), List.of(1, 3));
    assertThat(answer.getSources())
        .extracting(SourceReference::getEntryId)
        .containsExactly("fixture.role.tamara.b1", "fixture.role.tamara.b2", "fixture.role.tamara");
  }

  @Test
  void shouldDropClaim_whenItCitesAnEntryNotRetrievedForTheQuestion() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built two card products from scratch.", "fixture.role.tamara.b1"),
            claim("At Tamara I integrated 100+ banks.", "fixture.role.alipay.b1"));

    assertThat(answer.getClaims())
        .extracting(AnsweredClaim::getText)
        .containsExactly("I built two card products from scratch.");
    assertThat(answer.getSources())
        .extracting(SourceReference::getEntryId)
        .containsExactly("fixture.role.tamara.b1");
    assertThat(answer.getRemovedClaims()).isEqualTo(1);
  }

  @Test
  void shouldDropClaim_whenItCitesNothing() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built two card products from scratch.", "fixture.role.tamara.b1"),
            claim("I am the best engineer Tamara ever had."));

    assertThat(answer.getClaims())
        .extracting(AnsweredClaim::getText)
        .containsExactly("I built two card products from scratch.");
    assertThat(answer.getRemovedClaims()).isEqualTo(1);
  }

  @Test
  void shouldDropAClaimNamingAnEmployerNoCitedEntryContains_whenAnswering() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built two card products from scratch.", "fixture.role.tamara.b1"),
            claim("I built them for Goldman.", "fixture.role.tamara.b1"));

    assertThat(answer.getMode()).isEqualTo(AnswerMode.ANSWERED);
    assertThat(answer.getClaims())
        .extracting(AnsweredClaim::getText)
        .containsExactly("I built two card products from scratch.");
    assertThat(renderer.render(answer))
        .contains("1 statement removed: not supported by my sources.");
  }

  @Test
  void shouldShowNoRemovedLine_whenNoClaimWasDropped() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built two card products from scratch.", "fixture.role.tamara.b1"));

    assertThat(answer.getRemovedClaims()).isZero();
    assertThat(renderer.render(answer)).doesNotContain("removed");
  }

  @Test
  void shouldWithholdTheAnswer_whenNoClaimSurvives() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built three card products.", "fixture.role.tamara.b1"));

    assertThat(answer.getMode()).isEqualTo(AnswerMode.UNVERIFIED);
    assertThat(renderer.render(answer))
        .isEqualTo("I couldn't put together a verified answer.\n");
  }

  @Test
  void shouldWithholdTheAnswer_whenMoreThanHalfOfTheClaimsAreDropped() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built two card products from scratch.", "fixture.role.tamara.b1"),
            claim("I built them for Goldman.", "fixture.role.tamara.b1"),
            claim("I grew the team to 50 engineers.", "fixture.role.tamara.b2"));

    assertThat(answer.getMode()).isEqualTo(AnswerMode.UNVERIFIED);
    assertThat(answer.getClaims()).isEmpty();
    assertThat(answer.getSources()).isEmpty();
    assertThat(renderer.render(answer)).doesNotContain("removed").doesNotContain("Sources");
  }

  @Test
  void shouldAppendOneTraceLine_whenAQuestionIsAsked() throws Exception {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            claim("I built two card products from scratch.", "fixture.role.tamara.b1"),
            claim("At Tamara I integrated 100+ banks.", "fixture.role.alipay.b1"));

    try (var files = Files.list(traceDir)) {
      var traceFiles = files.toList();
      assertThat(traceFiles).hasSize(1);
      var lines = Files.readAllLines(traceFiles.getFirst());
      assertThat(lines).hasSize(1);

      JsonNode trace = JsonMapper.builder().build().readTree(lines.getFirst());
      assertThat(trace.get("traceId").asString()).isEqualTo(answer.getTraceId().value());
      assertThat(trace.get("question").asString())
          .isEqualTo("Which card products did you build at Tamara?");
      assertThat(trace.get("retrieved").valueStream().map(r -> r.get("entryId").asString()))
          .contains("fixture.role.tamara.b1")
          .doesNotContain("fixture.role.alipay.b1");
      assertThat(trace.get("retrieved").get(0).get("score").asDouble()).isPositive();
      assertThat(trace.get("promptVersion").asString()).isEqualTo("answer-test");
      assertThat(trace.get("promptHash").asString()).isEqualTo(PROMPT.hash());
      assertThat(trace.get("modelId").asString()).isEqualTo("stub-model");
      assertThat(trace.get("rawOutput").asString()).isEqualTo("{\"raw\":true}");
      assertThat(trace.get("keptClaims")).hasSize(1);
      assertThat(trace.get("droppedClaims").get(0).get("dropReason").asString())
          .isEqualTo("CITES_ENTRY_NOT_RETRIEVED");
      assertThat(trace.get("inputTokens").asInt()).isEqualTo(120);
      assertThat(trace.get("outputTokens").asInt()).isEqualTo(30);
      assertThat(trace.has("totalLatencyMs")).isTrue();
      var config = trace.get("config");
      assertThat(config.get("provider").asString()).isEqualTo("stub");
      assertThat(config.get("modelId").asString()).isEqualTo("stub-model");
      assertThat(config.get("timeoutMs").asLong()).isEqualTo(20000);
      assertThat(config.get("maxOutputTokens").asInt()).isEqualTo(800);
      assertThat(config.get("retrievalTopK").asInt()).isEqualTo(6);
      assertThat(config.get("promptVersion").asString()).isEqualTo("answer-test");
    }
  }

  private AnswerResult ask(String question, Claim... scriptedClaims) {
    ModelPort stubModel =
        prompt ->
            ModelReply.builder()
                .provider("stub")
                .modelId("stub-model")
                .rawOutput("{\"raw\":true}")
                .answer(new ModelAnswer(List.of(scriptedClaims), false))
                .inputTokens(120)
                .outputTokens(30)
                .build();
    var service = AskFixture.service(stubModel, AskFixture.CONFIG, traceDir, stateDir);
    return service.ask(AskQuestionCommand.builder().question(question).build());
  }

  private static Claim claim(String text, String... entryIds) {
    return new Claim(text, Arrays.stream(entryIds).map(EntryId::of).toList());
  }
}
