package io.github.trieunv.assistant.domain.answer;

import java.util.List;

/**
 * Which claims may be shown. {@code withheld} means the whole answer is replaced by "I couldn't put
 * together a verified answer.": no claim survived, or too many were dropped.
 */
public record GuardrailVerdict(List<Claim> kept, List<DroppedClaim> dropped, boolean withheld) {

  public GuardrailVerdict {
    kept = List.copyOf(kept);
    dropped = List.copyOf(dropped);
  }
}
