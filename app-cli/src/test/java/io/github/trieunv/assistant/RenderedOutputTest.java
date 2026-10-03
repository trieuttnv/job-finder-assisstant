package io.github.trieunv.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.adapter.in.cli.AnswerRenderer;
import io.github.trieunv.assistant.adapter.in.cli.CliAnswers;
import io.github.trieunv.assistant.adapter.in.cli.InteractiveSession;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Seam 1, rendered output: whatever the question and the model's reply, the text the Interviewer
 * reads keeps three promises. Citations match the Sources block, a fallback never shows one, and
 * the removed-claims line appears exactly when claims were dropped.
 */
class RenderedOutputTest {

  private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");
  private static final Pattern SOURCE_LINE = Pattern.compile("(?m)^ \\[(\\d+)] ");
  private static final String TEAM_QUESTION = "How big was the engineering team at Tamara?";

  @TempDir Path traceDir;
  @TempDir Path stateDir;

  private final AnswerRenderer renderer = new AnswerRenderer();

  /** A question, the model's scripted reply, and the answer shape it must produce. */
  record Scenario(
      String name,
      String question,
      ModelPort model,
      AnswerMode mode,
      int removedClaims,
      int conflicts) {

    @Override
    public String toString() {
      return name;
    }
  }

  static List<Scenario> scenarios() {
    return List.of(
        new Scenario(
            "one cited claim",
            "Which card products did you build at Tamara?",
            replying(claim("I built two card products from scratch.", "fixture.role.tamara.b1")),
            AnswerMode.ANSWERED,
            0,
            0),
        new Scenario(
            "claims sharing a citation",
            TEAM_QUESTION,
            replying(
                claim("I grew the team to 13 engineers.", "fixture.role.tamara.b2"),
                claim("That was at Tamara.", "fixture.role.tamara.b2", "fixture.role.tamara")),
            AnswerMode.ANSWERED,
            0,
            0),
        new Scenario(
            "a dropped claim",
            TEAM_QUESTION,
            replying(
                claim("I grew the team to 13 engineers.", "fixture.role.tamara.b2"),
                claim("I grew it at Goldman.", "fixture.role.tamara.b2")),
            AnswerMode.ANSWERED,
            1,
            0),
        new Scenario(
            "a Conflict notice after a claim",
            "What languages do you write code in?",
            replying(claim("My CV lists Java and Spring Boot.", "fixture.skills.languages")),
            AnswerMode.ANSWERED,
            0,
            1),
        new Scenario(
            "not in sources without a model call",
            "What is your favourite football club?",
            replying(),
            AnswerMode.NOT_IN_SOURCES,
            0,
            0),
        new Scenario(
            "not in sources from the model",
            TEAM_QUESTION,
            prompt -> reply(new ModelAnswer(List.of(), true)),
            AnswerMode.NOT_IN_SOURCES,
            0,
            0),
        new Scenario(
            "withheld as unverified",
            TEAM_QUESTION,
            replying(claim("I grew the team to 50 engineers.", "fixture.role.tamara.b2")),
            AnswerMode.UNVERIFIED,
            0,
            0),
        new Scenario(
            "provider unavailable",
            TEAM_QUESTION,
            prompt -> {
              throw new AssistantApplicationException(ErrorCode.MODEL_TIMEOUT, "slow", null);
            },
            AnswerMode.UNAVAILABLE,
            0,
            0),
        new Scenario(
            "unexpected failure",
            TEAM_QUESTION,
            prompt -> {
              throw new IllegalStateException("boom");
            },
            AnswerMode.ERROR,
            0,
            0));
  }

  @ParameterizedTest
  @MethodSource("scenarios")
  void shouldKeepTheRenderedOutputConsistent_whateverShapeTheAnswerHas(Scenario scenario) {
    var answer =
        AskFixture.service(scenario.model(), AskFixture.CONFIG, traceDir, stateDir)
            .ask(AskQuestionCommand.builder().question(scenario.question()).build());
    var rendered = renderer.render(answer);

    assertThat(answer.getMode()).isEqualTo(scenario.mode());
    assertThat(answer.getRemovedClaims()).isEqualTo(scenario.removedClaims());
    assertThat(answer.getConflicts()).hasSize(scenario.conflicts());
    if (answer.getMode() == AnswerMode.ANSWERED) {
      var sourcesAt = rendered.indexOf("\nSources\n");
      var cited = numbers(CITATION, rendered.substring(0, sourcesAt));
      var listed = numbers(SOURCE_LINE, rendered.substring(sourcesAt));
      assertThat(cited).as("citations match the Sources block").isEqualTo(listed);
      assertThat(listed).as("Sources numbered from 1 with no gap").isEqualTo(oneTo(listed.size()));
    } else {
      assertThat(rendered).as("a fallback is one line").doesNotContain("Sources").hasLineCount(1);
    }
    assertThat(rendered.contains("removed: not supported by my sources."))
        .as("removed line appears exactly when claims were dropped")
        .isEqualTo(answer.getRemovedClaims() > 0);
  }

  @Test
  void shouldPrintEachAnswerWithItsSources_whenAskedThroughTheInteractiveSession() {
    var service =
        AskFixture.service(
            replying(claim("I built two card products from scratch.", "fixture.role.tamara.b1")),
            AskFixture.CONFIG,
            traceDir,
            stateDir);
    var bytes = new ByteArrayOutputStream();
    var input = "Which card products did you build at Tamara?\nexit\n";

    new InteractiveSession(new CliAnswers(service, renderer))
        .run(
            new BufferedReader(new StringReader(input)),
            new PrintStream(bytes, true, StandardCharsets.UTF_8));

    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains("I built two card products from scratch [1].")
        .contains(" [1] fixture.role.tamara.b1  fixture  \"Built two card products from scratch\"");
  }

  private static Set<Integer> numbers(Pattern pattern, String text) {
    return pattern
        .matcher(text)
        .results()
        .map(match -> Integer.parseInt(match.group(1)))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Set<Integer> oneTo(int size) {
    var set = new TreeSet<Integer>();
    for (var n = 1; n <= size; n++) {
      set.add(n);
    }
    return set;
  }

  private static ModelPort replying(Claim... claims) {
    return prompt -> reply(new ModelAnswer(List.of(claims), false));
  }

  private static ModelReply reply(ModelAnswer answer) {
    return ModelReply.builder()
        .provider("stub")
        .modelId("stub-model")
        .rawOutput("{}")
        .answer(answer)
        .build();
  }

  private static Claim claim(String text, String... entryIds) {
    return new Claim(text, Arrays.stream(entryIds).map(EntryId::of).toList());
  }
}
