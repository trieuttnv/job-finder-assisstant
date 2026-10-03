package io.github.trieunv.assistant.application.port.in;

import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.domain.knowledge.InvalidKnowledgeException;

/** Loads and checks the knowledge base at startup. */
public interface LoadKnowledgeUseCase {

  /**
   * @throws InvalidKnowledgeException on the first Entry or Snapshot that fails a check, naming it
   *     and the reason
   */
  LoadedKnowledge load();
}
