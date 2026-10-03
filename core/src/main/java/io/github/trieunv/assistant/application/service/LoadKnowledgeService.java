package io.github.trieunv.assistant.application.service;

import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.application.port.in.LoadKnowledgeUseCase;
import io.github.trieunv.assistant.application.port.out.KnowledgeBasePort;
import io.github.trieunv.assistant.application.port.out.KnowledgeStatePort;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.IdChanges;
import io.github.trieunv.assistant.domain.knowledge.KnowledgeCheck;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Checks the Snapshots and Entries, then reports the id changes against the previous successful
 * run. A failed load saves nothing, so the next report still compares with the last good run.
 */
@RequiredArgsConstructor
public class LoadKnowledgeService implements LoadKnowledgeUseCase {

  private final KnowledgeBasePort knowledgeBase;
  private final KnowledgeStatePort state;

  @Override
  public LoadedKnowledge load() {
    var entries =
        KnowledgeCheck.check(knowledgeBase.loadSnapshots(), knowledgeBase.loadEntryDrafts());
    var fingerprints =
        entries.stream()
            .collect(
                Collectors.toMap(
                    entry -> entry.id().value(),
                    Entry::fingerprint,
                    (first, second) -> first,
                    LinkedHashMap::new));
    var changes = IdChanges.between(state.previousFingerprints(), fingerprints);
    state.save(fingerprints);
    return LoadedKnowledge.builder().entries(entries).changes(changes).build();
  }
}
