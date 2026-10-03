package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * {@code ./ask "<question>"} answers one question and prints it with its Sources; {@code ./ask}
 * alone opens an {@link InteractiveSession}.
 */
@Component
public class AskCommandAdapter implements CommandLineRunner {

  private final CliAnswers answers;
  private final PrintStream out = System.out;

  public AskCommandAdapter(AskQuestionUseCase askQuestion) {
    this.answers = new CliAnswers(askQuestion, new AnswerRenderer());
  }

  @Override
  public void run(String... args) {
    var question = String.join(" ", args).strip();
    if (question.isEmpty()) {
      var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      new InteractiveSession(answers).run(in, out);
      return;
    }
    out.println();
    out.print(answers.answer(question));
  }
}
