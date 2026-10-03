package io.github.trieunv.assistant.application.dto;

import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The parsed model answer plus what a trace needs to reproduce it: raw output, model and tokens.
 */
@Builder
@Getter
@AllArgsConstructor
public class ModelReply {

  private final String provider;
  private final String modelId;
  private final String rawOutput;
  private final ModelAnswer answer;
  private final int inputTokens;
  private final int outputTokens;
}
