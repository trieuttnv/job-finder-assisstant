package io.github.trieunv.assistant.application.port.in;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;

/**
 * The Ask use case: answer one Interviewer question from the knowledge base. The CLI, the eval
 * runner and the designed HTTP API all call it.
 */
public interface AskQuestionUseCase {

  AnswerResult ask(AskQuestionCommand command);
}
