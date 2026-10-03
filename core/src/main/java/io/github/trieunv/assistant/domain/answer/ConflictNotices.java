package io.github.trieunv.assistant.domain.answer;

import io.github.trieunv.assistant.domain.knowledge.Conflict;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides which Conflict notices an answer shows (README Task 2, Step 4): a Conflict is relevant
 * when the question names one of its topic terms or a surviving claim cites either side. It also
 * holds every side's Entries, so a notice can cite both sides even when only one was retrieved.
 */
public final class ConflictNotices {

  private final List<Conflict> conflicts;
  private final Map<EntryId, Entry> entriesById;

  public ConflictNotices(List<Conflict> conflicts, List<Entry> entries) {
    this.conflicts = List.copyOf(conflicts);
    this.entriesById =
        entries.stream().collect(Collectors.toMap(Entry::id, Function.identity(), (a, b) -> a));
  }

  /** The Conflicts an answer with these surviving claims shows. */
  public List<Conflict> relevantTo(String question, List<Claim> keptClaims) {
    var cited = keptClaims.stream().flatMap(claim -> claim.entryIds().stream()).toList();
    return conflicts.stream()
        .filter(
            conflict ->
                conflict.isNamedIn(question) || cited.stream().anyMatch(conflict::involves))
        .toList();
  }

  /** A side's Entry, which may not have been retrieved for this question. */
  public Optional<Entry> entry(EntryId id) {
    return Optional.ofNullable(entriesById.get(id));
  }
}
