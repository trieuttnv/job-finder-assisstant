package io.github.trieunv.assistant.eval;

import java.util.List;

/**
 * How one result fared against its Gold Label: whether it passed, which mustContain groups it
 * missed and which mustNotClaim patterns it matched. Stored next to each result, so a reader sees
 * why a question failed without recomputing.
 */
public record Verdict(
    boolean passed, List<List<String>> missingGroups, List<String> matchedPatterns) {

  public Verdict {
    missingGroups = missingGroups.stream().map(List::copyOf).toList();
    matchedPatterns = List.copyOf(matchedPatterns);
  }
}
