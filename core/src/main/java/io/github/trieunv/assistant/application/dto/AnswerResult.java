package io.github.trieunv.assistant.application.dto;

import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.TraceId;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The answer shown to the Interviewer: only claims that passed the Output Guardrail, each with
 * citation numbers that index into {@code sources}.
 */
@Builder
@Getter
@AllArgsConstructor
public class AnswerResult {

  private final AnswerMode mode;
  private final List<AnsweredClaim> claims;
  private final List<SourceReference> sources;
  private final int removedClaims;
  private final TraceId traceId;
}
