package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import lombok.RequiredArgsConstructor;

/** Asks one question and renders the reply, as both {@code ./ask} modes print it. */
@RequiredArgsConstructor
public class CliAnswers {

  private final AskQuestionUseCase askQuestion;
  private final AnswerRenderer renderer;

  public String answer(String question) {
    try {
      return renderer.render(
          askQuestion.ask(AskQuestionCommand.builder().question(question).build()));
    } catch (RuntimeException e) {
      // The use case already turns failures into fallbacks; this is the last line before a stack
      // trace would reach the Interviewer.
      return AnswerMode.ERROR.fallbackMessage().orElseThrow() + "\n";
    }
  }
}
