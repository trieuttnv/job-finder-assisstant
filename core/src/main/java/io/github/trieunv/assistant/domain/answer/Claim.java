package io.github.trieunv.assistant.domain.answer;

import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.util.List;

/** One statement the model proposes, with the Entries it cites as support. */
public record Claim(String text, List<EntryId> entryIds) {

  public Claim {
    entryIds = List.copyOf(entryIds);
  }
}
