package io.github.trieunv.assistant.application.dto;

import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.IdChanges;
import java.util.List;
import java.util.Map;
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

  /** Entry id to {@link Entry#fingerprint()}, recorded once startup succeeds. */
  private final Map<String, String> fingerprints;
}
