package io.github.trieunv.assistant.domain.knowledge;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entry ids added, changed or removed since the previous run, each sorted. A changed id kept its id
 * but not its content, which may break a Gold Label that cites it.
 */
public record IdChanges(List<String> added, List<String> changed, List<String> removed) {

  /** Compares two runs, each given as Entry id to {@link Entry#fingerprint()}. */
  public static IdChanges between(Map<String, String> previous, Map<String, String> current) {
    return new IdChanges(
        current.keySet().stream().filter(id -> !previous.containsKey(id)).sorted().toList(),
        current.keySet().stream()
            .filter(previous::containsKey)
            .filter(id -> !Objects.equals(previous.get(id), current.get(id)))
            .sorted()
            .toList(),
        previous.keySet().stream().filter(id -> !current.containsKey(id)).sorted().toList());
  }

  public boolean isEmpty() {
    return added.isEmpty() && changed.isEmpty() && removed.isEmpty();
  }
}
