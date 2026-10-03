package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
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
    var answer = askQuestion.ask(AskQuestionCommand.builder().question(question).build());
    out.println();
    out.print(renderer.render(answer));
  }
}
