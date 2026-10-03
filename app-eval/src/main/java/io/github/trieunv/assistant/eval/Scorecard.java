package io.github.trieunv.assistant.eval;

import java.util.List;
import java.util.Map;

/**
 * Every metric per scope ("all", "dev", "heldout", or a category such as "cv-direct"), and the Hard
 * Lines the run failed, if any.
 */
public record Scorecard(Map<String, Map<Metric, Stat>> stats, List<String> hardLineFailures) {

  public static final List<String> SPLIT_SCOPES = List.of("all", "dev", "heldout");

  public Scorecard {
    stats = Map.copyOf(stats);
    hardLineFailures = List.copyOf(hardLineFailures);
  }

  public Stat get(String scope, Metric metric) {
    return stats.getOrDefault(scope, Map.of()).getOrDefault(metric, Stat.of(List.of()));
  }
}
