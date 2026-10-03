package io.github.trieunv.assistant.application.port.out;

import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.domain.answer.AnswerPrompt;

public interface ModelPort {

  /**
   * Makes exactly one model call, at temperature 0 with capped output.
   *
   * @throws AssistantApplicationException with {@code MODEL_UNAVAILABLE} when the provider fails or
   *     times out, or {@code MODEL_OUTPUT_UNUSABLE} when its output does not fit the claims schema
   */
  ModelReply generate(AnswerPrompt prompt);
}
