package io.github.trieunv.assistant.application.port.in;

import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.domain.knowledge.InvalidKnowledgeException;

/** Loads and checks the knowledge base at startup. */
public interface LoadKnowledgeUseCase {

  /**
   * Checks the knowledge base and reports id changes against the previous run; records nothing.
   *
   * @throws InvalidKnowledgeException on the first Entry or Snapshot that fails a check, naming it
   *     and the reason
   */
  LoadedKnowledge load();

  /**
   * Makes {@code loaded} the previous run that the next report compares with. Called only once
   * startup has fully succeeded, so a run that fails later reports the same changes again.
   */
  void recordAsPreviousRun(LoadedKnowledge loaded);
}
