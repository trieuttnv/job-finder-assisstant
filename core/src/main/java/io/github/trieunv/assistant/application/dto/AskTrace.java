package io.github.trieunv.assistant.application.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One trace line per question: enough to reproduce and explain the answer. */
@Builder
@Getter
@AllArgsConstructor
public class AskTrace {

  private final String traceId;
  private final Instant timestamp;
  private final String question;
  private final List<RetrievedTrace> retrieved;
  private final String promptVersion;
  private final String promptHash;
  private final String provider;
  private final String modelId;
  private final String rawOutput;
  private final boolean notInSources;
  private final List<ClaimTrace> keptClaims;
  private final List<ClaimTrace> droppedClaims;
  private final int inputTokens;
  private final int outputTokens;
  private final long retrievalLatencyMs;
  private final long modelLatencyMs;
  private final long totalLatencyMs;

  @Builder
  @Getter
  @AllArgsConstructor
  public static class RetrievedTrace {

    private final String entryId;
    private final double score;
  }

  @Builder
  @Getter
  @AllArgsConstructor
  public static class ClaimTrace {

    private final String text;
    private final List<String> entryIds;

    /** Why the Output Guardrail dropped the claim; null for a kept claim. */
    private final String dropReason;
  }
}
