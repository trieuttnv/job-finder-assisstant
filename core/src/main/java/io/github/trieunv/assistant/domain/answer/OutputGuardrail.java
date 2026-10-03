package io.github.trieunv.assistant.domain.answer;

import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides, with code and no model, which proposed claims may be shown (ADR 0004). A claim is
 * dropped when it cites no Entry, or cites an Entry that was not retrieved for this question: the
 * model only saw retrieved Entries, so any other id is invented.
 */
public class OutputGuardrail {

  public GuardrailVerdict check(List<Claim> claims, Set<EntryId> retrievedIds) {
    var kept = new ArrayList<Claim>();
    var dropped = new ArrayList<DroppedClaim>();
    for (var claim : claims) {
      dropReason(claim, retrievedIds)
          .ifPresentOrElse(
              reason -> dropped.add(new DroppedClaim(claim, reason)), () -> kept.add(claim));
    }
    return new GuardrailVerdict(kept, dropped);
  }

  private static Optional<DropReason> dropReason(Claim claim, Set<EntryId> retrievedIds) {
    if (claim.entryIds().isEmpty()) {
      return Optional.of(DropReason.CITES_NOTHING);
    }
    if (!retrievedIds.containsAll(claim.entryIds())) {
      return Optional.of(DropReason.CITES_ENTRY_NOT_RETRIEVED);
    }
    return Optional.empty();
  }
}
