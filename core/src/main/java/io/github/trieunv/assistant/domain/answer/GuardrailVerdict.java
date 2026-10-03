package io.github.trieunv.assistant.domain.answer;

import java.util.List;

public record GuardrailVerdict(List<Claim> kept, List<DroppedClaim> dropped) {

  public GuardrailVerdict {
    kept = List.copyOf(kept);
    dropped = List.copyOf(dropped);
  }
}
