package io.github.trieunv.assistant.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The settings an answer was produced under, copied into every trace so a run can be reproduced.
 */
@Builder
@Getter
@AllArgsConstructor
public class AskConfig {

  private final String provider;
  private final String modelId;
  private final long timeoutMs;
  private final int maxOutputTokens;
  private final int retrievalTopK;
  private final String promptVersion;
}
