package io.github.trieunv.assistant.application.exception;

import lombok.Getter;

/**
 * The model replied, but its output doesn't fit the claims schema or is empty. It carries the raw
 * output so the trace keeps exactly what the model said, which is when it matters most, and the
 * tokens the provider billed for it, so cost per question isn't understated.
 */
@Getter
public class ModelOutputUnusableException extends AssistantApplicationException {

  private final String rawOutput;
  private final int inputTokens;
  private final int outputTokens;

  public ModelOutputUnusableException(
      String message, String rawOutput, int inputTokens, int outputTokens, Throwable cause) {
    super(ErrorCode.MODEL_OUTPUT_UNUSABLE, message, cause);
    this.rawOutput = rawOutput;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
  }
}
