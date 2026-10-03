package io.github.trieunv.assistant.application.dto;

import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.IdChanges;
import java.util.List;
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
}
