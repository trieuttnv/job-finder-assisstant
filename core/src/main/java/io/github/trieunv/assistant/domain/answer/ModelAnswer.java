package io.github.trieunv.assistant.domain.answer;

import java.util.List;

/**
 * What the model proposes for one question: claims, each citing Entries, and whether it judged the
 * question not answerable from the Entries. Nothing here is shown before the Output Guardrail.
 */
public record ModelAnswer(List<Claim> claims, boolean notInSources) {

  public ModelAnswer {
    claims = List.copyOf(claims);
  }
}
