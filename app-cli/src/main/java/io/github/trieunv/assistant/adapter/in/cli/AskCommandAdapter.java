package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import java.io.PrintStream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** {@code ./ask "<question>"}: answers one question and prints it with its Sources. */
@Component
@RequiredArgsConstructor
public class AskCommandAdapter implements CommandLineRunner {

  private final AskQuestionUseCase askQuestion;
  private final AnswerRenderer renderer = new AnswerRenderer();
  private final PrintStream out = System.out;

  @Override
  public void run(String... args) {
    var question = String.join(" ", args).strip();
    if (question.isEmpty()) {
      out.println("Usage: ./ask \"<question>\"");
      return;
    }
    String rendered;
    try {
      rendered =
          renderer.render(
              askQuestion.ask(AskQuestionCommand.builder().question(question).build()));
    } catch (RuntimeException e) {
      // The use case already turns failures into fallbacks; this is the last line before a stack
      // trace would reach the Interviewer.
      rendered = AnswerMode.ERROR.fallbackMessage().orElseThrow() + "\n";
    }
    out.println();
    out.print(rendered);
  }
}
