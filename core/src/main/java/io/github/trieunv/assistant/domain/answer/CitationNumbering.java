package io.github.trieunv.assistant.domain.answer;

import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbers cited Entries from 1 in order of first citation, so the citation numbers on the claims
 * and the numbered Sources block always agree and no Source is listed that no claim cites.
 */
public class CitationNumbering {

  private final Map<EntryId, Integer> numbers = new LinkedHashMap<>();

  public List<Integer> numberAll(List<EntryId> entryIds) {
    return entryIds.stream()
        .distinct()
        .map(id -> numbers.computeIfAbsent(id, ignored -> numbers.size() + 1))
        .toList();
  }

  /** Cited Entries in citation-number order. */
  public List<EntryId> citedInOrder() {
    return List.copyOf(numbers.keySet());
  }
}
