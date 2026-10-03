package io.github.trieunv.assistant.application.service;

import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.application.port.in.LoadKnowledgeUseCase;
import io.github.trieunv.assistant.application.port.out.KnowledgeBasePort;
import io.github.trieunv.assistant.application.port.out.KnowledgeStatePort;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.IdChanges;
import io.github.trieunv.assistant.domain.knowledge.KnowledgeCheck;
import io.github.trieunv.assistant.domain.knowledge.KnownNames;
import io.github.trieunv.assistant.domain.knowledge.RetrievalVocabulary;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Checks the Snapshots and Entries, then reports the id changes against the previous successful
 * run. Only {@link #recordAsPreviousRun} saves, so a failed startup leaves the last good run as the
 * comparison point.
 */
@RequiredArgsConstructor
public class LoadKnowledgeService implements LoadKnowledgeUseCase {

  private final KnowledgeBasePort knowledgeBase;
  private final KnowledgeStatePort state;

  @Override
  public LoadedKnowledge load() {
    var snapshots = knowledgeBase.loadSnapshots();
    var entries = KnowledgeCheck.check(snapshots, knowledgeBase.loadEntryDrafts());
    var fingerprints =
        entries.stream()
            .collect(
                Collectors.toMap(
                    entry -> entry.id().value(),
                    Entry::fingerprint,
                    (first, second) -> first,
                    LinkedHashMap::new));
    var changes = IdChanges.between(state.previousFingerprints(), fingerprints);
    var nameList = knowledgeBase.loadNameList();
    var conflicts = knowledgeBase.loadConflicts();
    KnowledgeCheck.checkConflicts(conflicts, entries);
    return LoadedKnowledge.builder()
        .entries(entries)
        .changes(changes)
        .knownNames(KnownNames.from(entries, nameList))
        .vocabulary(RetrievalVocabulary.from(entries, nameList))
        .conflicts(conflicts)
        .fingerprints(fingerprints)
        .quoteProvenance(KnowledgeCheck.quoteProvenance(snapshots, entries))
        .build();
  }

  @Override
  public void recordAsPreviousRun(LoadedKnowledge loaded) {
    state.save(loaded.getFingerprints());
  }
}
