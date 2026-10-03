package io.github.trieunv.assistant.application.dto;

import io.github.trieunv.assistant.domain.knowledge.Conflict;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.IdChanges;
import io.github.trieunv.assistant.domain.knowledge.KnownNames;
import io.github.trieunv.assistant.domain.knowledge.RetrievalVocabulary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** The checked Entries, and which ids changed since the previous run. */
@Builder
@Getter
@AllArgsConstructor
public class LoadedKnowledge {

  private final List<Entry> entries;
  private final IdChanges changes;

  /** Employer, product and technology names the Output Guardrail checks claims for. */
  private final KnownNames knownNames;

  /** Employer, technology and type names a question can be narrowed by. */
  private final RetrievalVocabulary vocabulary;

  private final List<Conflict> conflicts;

  /** Entries whose Quote is in their Snapshot, over all Entries (1.0 once the checks pass). */
  private final double quoteProvenance;

  /**
   * For each Entry id, the Entries whose citation is also correct for it: a bullet's role header
   * and the other side of any Conflict it belongs to.
   */
  public Map<String, Set<String>> companions() {
    var companions = new HashMap<String, Set<String>>();
    for (var entry : entries) {
      var set = companions.computeIfAbsent(entry.id().value(), id -> new HashSet<>());
      Optional.ofNullable(entry.parent()).ifPresent(parent -> set.add(parent.value()));
    }
    for (var conflict : conflicts) {
      var ids = conflict.entryIds().stream().map(EntryId::value).toList();
      ids.forEach(id -> companions.computeIfAbsent(id, key -> new HashSet<>()).addAll(ids));
    }
    return companions;
  }

  /** Entry id to {@link Entry#fingerprint()}, recorded once startup succeeds. */
  private final Map<String, String> fingerprints;
}
