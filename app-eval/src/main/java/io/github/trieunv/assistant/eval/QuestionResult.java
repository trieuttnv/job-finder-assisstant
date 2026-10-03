package io.github.trieunv.assistant.eval;

import io.github.trieunv.assistant.domain.answer.AnswerMode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What one evaluation question got in one repetition: the answer's mode and shown claims (from the
 * answer value), and what its trace line recorded: the ranked retrieval hits, the claims the Output
 * Guardrail dropped and why, tokens, latency and cost. A committed results file therefore explains
 * each failure on its own, without the git-ignored traces.
 */
public record QuestionResult(
    String itemId,
    int rep,
    AnswerMode mode,
    List<ShownClaim> claims,
    List<RetrievedHit> retrieved,
    int proposedClaims,
    List<DroppedClaim> dropped,
    int inputTokens,
    int outputTokens,
    long latencyMs,
    Double costUsd,
    String traceId) {

  public QuestionResult {
    claims = List.copyOf(claims);
    retrieved = List.copyOf(retrieved);
    dropped = List.copyOf(dropped);
  }

  public boolean answered() {
    return mode == AnswerMode.ANSWERED;
  }

  /** The shown claims only: text code writes (a Conflict notice, a fallback) is never scored. */
  public String shownText() {
    return claims.stream().map(ShownClaim::text).collect(Collectors.joining("\n"));
  }

  public Set<String> citedIds() {
    return claims.stream().flatMap(claim -> claim.entryIds().stream()).collect(Collectors.toSet());
  }

  /** Every retrieved Entry, matched or brought along by a match (a bullet's role, a Conflict). */
  public Set<String> retrievedIds() {
    return retrieved.stream().map(RetrievedHit::entryId).collect(Collectors.toSet());
  }

  /** A claim as shown to the Interviewer, with the Entry ids its citations point at. */
  public record ShownClaim(String text, List<String> entryIds) {

    public ShownClaim {
      entryIds = List.copyOf(entryIds);
    }
  }

  /** One retrieval hit in rank order; {@code expanded} hits came with a matched Entry. */
  public record RetrievedHit(String entryId, double score, boolean expanded) {}

  /** A claim the Output Guardrail removed, with its reason, for the error analysis. */
  public record DroppedClaim(String text, List<String> entryIds, String reason) {

    public DroppedClaim {
      entryIds = List.copyOf(entryIds);
    }
  }
}
