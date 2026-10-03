package io.github.trieunv.assistant.application.port.out;

import io.github.trieunv.assistant.domain.knowledge.Conflict;
import io.github.trieunv.assistant.domain.knowledge.EntryDraft;
import io.github.trieunv.assistant.domain.knowledge.NameList;
import io.github.trieunv.assistant.domain.knowledge.Snapshot;
import java.util.List;

/** Reads the committed Snapshots and Entry files as written; checking them is the core's job. */
public interface KnowledgeBasePort {

  List<Snapshot> loadSnapshots();

  List<EntryDraft> loadEntryDrafts();

  /** The committed technology names and aliases ({@code names.yaml}). */
  NameList loadNameList();

  /** The recorded Conflicts ({@code conflicts.yaml}); empty when the file doesn't exist. */
  List<Conflict> loadConflicts();
}
