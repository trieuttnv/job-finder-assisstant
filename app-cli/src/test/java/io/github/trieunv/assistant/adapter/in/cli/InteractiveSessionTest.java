package io.github.trieunv.assistant.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.TraceId;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The session {@code ./ask} opens with no argument, driven by typed lines. */
class InteractiveSessionTest {

  private final List<String> asked = new ArrayList<>();
  private final AskQuestionUseCase useCase =
      command -> {
        asked.add(command.getQuestion());
        return new AnswerResult(
            AnswerMode.NOT_IN_SOURCES, List.of(), List.of(), List.of(), 0, new TraceId("t-1"));
      };

  @Test
  void shouldShowFiveSuggestedQuestionsOnePerCategory_whenTheSessionOpens() {
    var output = run("exit\n");

    assertThat(output)
        .contains("1. When did you work at Atlassian?")
        .contains("2. What languages do you write code in?")
        .contains("3. Tell me about your biggest project.")
        .contains("4. What is your salary expectation?")
        .contains("5. You led 50 engineers at Tamara, right?");
    assertThat(asked).isEmpty();
  }

  @Test
  void shouldAskTheSuggestedQuestion_whenItsNumberIsTyped() {
    var output = run("2\nexit\n");

    assertThat(asked).containsExactly("What languages do you write code in?");
    assertThat(output).contains("> What languages do you write code in?");
    assertThat(output).contains(AnswerMode.NOT_IN_SOURCES.fallbackMessage().orElseThrow());
  }

  @Test
  void shouldAskEachTypedQuestionInTurn_untilTheInputEnds() {
    run("Where are you based?\n\n   \nHow big was your team at Tamara?\n");

    assertThat(asked).containsExactly("Where are you based?", "How big was your team at Tamara?");
  }

  @Test
  void shouldAskAgainWithoutCallingTheModel_whenANumberHasNoSuggestion() {
    var output = run("7\nquit\n");

    assertThat(asked).isEmpty();
    assertThat(output).contains("Pick 1 to 5, or type a question.");
  }

  @Test
  void shouldShowTheFixedMessageAndKeepGoing_whenTheUseCaseThrows() {
    AskQuestionUseCase broken =
        command -> {
          throw new IllegalStateException("boom");
        };

    var output = run(broken, "first\nsecond\nexit\n");

    var message = AnswerMode.ERROR.fallbackMessage().orElseThrow();
    assertThat(output).contains(message).doesNotContain("boom");
    assertThat(output.split(message, -1)).hasSize(3);
  }

  private String run(String input) {
    return run(useCase, input);
  }

  private static String run(AskQuestionUseCase askQuestion, String input) {
    var bytes = new ByteArrayOutputStream();
    var out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    new InteractiveSession(new CliAnswers(askQuestion, new AnswerRenderer()))
        .run(new BufferedReader(new StringReader(input)), out);
    return bytes.toString(StandardCharsets.UTF_8);
  }
}
