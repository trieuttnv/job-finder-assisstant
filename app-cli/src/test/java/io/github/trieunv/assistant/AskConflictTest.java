package io.github.trieunv.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.adapter.in.cli.AnswerRenderer;
import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AnsweredClaim;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Seam 1, Conflicts: code renders the notice with both sides cited whenever the question or a
 * surviving claim touches the Conflict, whatever the model wrote, and never on unrelated answers.
 */
class AskConflictTest {

  private static final String NOTICE =
      "⚠ My sources differ: the CV lists Java and Spring Boot [%d]; my 2026 learning prototypes"
          + " are written in Go [%d]. The CV does not mention Go.";

  @TempDir Path traceDir;
  @TempDir Path stateDir;

  private final AnswerRenderer renderer = new AnswerRenderer();
  private final AtomicReference<String> promptSeen = new AtomicReference<>();

  @Test
  void shouldShowBothSidesCited_whenTheQuestionIsAboutLanguages() {
    var answer =
        ask(
            "What languages do you write code in?",
            false,
            claim("My CV lists Java and Spring Boot.", "fixture.skills.languages"));

    assertThat(answer.getMode()).isEqualTo(AnswerMode.ANSWERED);
    assertThat(answer.getSources())
        .extracting(SourceReference::getEntryId)
        .containsExactly("fixture.skills.languages", "proto.outbox.go");
    assertThat(renderer.render(answer))
        .contains("My CV lists Java and Spring Boot [1].")
        .contains(NOTICE.formatted(1, 2));
  }

  @Test
  void shouldSendTheOtherSideToTheModel_whenRetrievalFindsOneSide() {
    ask("Which Java frameworks are on your CV?", false);

    assertThat(promptSeen.get())
        .contains("[fixture.skills.languages]")
        .contains("[proto.outbox.go]");
  }

  @Test
  void shouldDropAMergedClaimAndStillShowTheNotice_whenTheModelMergesTheSides() {
    var answer =
        ask(
            "Are you experienced in Go?",
            false,
            claim("My CV lists Java and Spring Boot.", "fixture.skills.languages"),
            claim("I am experienced in Go and Java.", "fixture.skills.languages"));

    assertThat(answer.getClaims())
        .extracting(AnsweredClaim::getText)
        .containsExactly("My CV lists Java and Spring Boot.");
    assertThat(renderer.render(answer))
        .contains("1 statement removed")
        .contains(NOTICE.formatted(1, 2));
  }

  @Test
  void shouldGiveTheFixedMessageAndNoNotice_whenTheModelSaysALanguageQuestionIsNotInSources() {
    var answer = ask("What languages do you speak?", true);

    assertThat(answer.getMode()).isEqualTo(AnswerMode.NOT_IN_SOURCES);
    assertThat(renderer.render(answer)).isEqualTo("That isn't in my sources.\n");
  }

  @Test
  void shouldShowTheNotice_whenASurvivingClaimCitesOneSideOfAnUnrelatedQuestion() {
    var answer =
        ask(
            "Which frameworks are on your CV?",
            false,
            claim("My CV lists Spring Boot.", "fixture.skills.languages"));

    assertThat(answer.getConflicts()).hasSize(1);
  }

  @Test
  void shouldShowNoNotice_whenTheAnswerHasNothingToDoWithTheConflict() {
    var answer =
        ask(
            "Which card products did you build at Tamara?",
            false,
            claim("I built two card products from scratch.", "fixture.role.tamara.b1"));

    assertThat(answer.getConflicts()).isEmpty();
    assertThat(renderer.render(answer)).doesNotContain("⚠");
    assertThat(answer.getClaims()).extracting(AnsweredClaim::getText).hasSize(1);
  }

  private AnswerResult ask(String question, boolean notInSources, Claim... claims) {
    var service =
        AskFixture.service(
            prompt -> {
              promptSeen.set(prompt.userMessage());
              return ModelReply.builder()
                  .provider("stub")
                  .modelId("stub-model")
                  .rawOutput("{}")
                  .answer(new ModelAnswer(List.of(claims), notInSources))
                  .build();
            },
            AskFixture.CONFIG,
            traceDir,
            stateDir);
    return service.ask(AskQuestionCommand.builder().question(question).build());
  }

  private static Claim claim(String text, String... entryIds) {
    return new Claim(text, Arrays.stream(entryIds).map(EntryId::of).toList());
  }
}
