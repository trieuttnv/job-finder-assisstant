package io.github.trieunv.assistant.application.dto;

import io.github.trieunv.assistant.domain.answer.AnswerMode;
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
  private final AnswerMode mode;

  /** Why a fallback was given (error code or exception type, and message); null when answered. */
  private final String failure;
  private final List<RetrievedTrace> retrieved;
  private final String promptVersion;
  private final String promptHash;
  private final String provider;
  private final String modelId;
  private final String rawOutput;
  private final boolean notInSources;
  private final List<ClaimTrace> keptClaims;
  private final List<ClaimTrace> droppedClaims;

  /** Ids of the Conflicts whose notice was shown. */
  private final List<String> conflictIds;
  private final int inputTokens;
  private final int outputTokens;

  /** Token usage times the configured price; null when either price is not configured. */
  private final Double costUsd;
  private final long retrievalLatencyMs;
  private final long modelLatencyMs;

  /** Output Guardrail, citation numbering and Conflict notices, after the model replied. */
  private final long guardrailLatencyMs;
  private final long totalLatencyMs;
  private final AskConfig config;

  @Builder
  @Getter
  @AllArgsConstructor
  public static class RetrievedTrace {

    private final String entryId;
    private final double score;

    /** Came with a matched Entry (a bullet's role) rather than matching itself. */
    private final boolean expanded;
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
