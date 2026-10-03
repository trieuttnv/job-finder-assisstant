package io.github.trieunv.assistant.application.port.out;

import io.github.trieunv.assistant.domain.knowledge.RetrievedEntry;
import java.util.List;

public interface RetrieverPort {

  /**
   * The best-matching Entries for the question, highest score first; empty when nothing matches.
   */
  List<RetrievedEntry> retrieve(String question);
}
