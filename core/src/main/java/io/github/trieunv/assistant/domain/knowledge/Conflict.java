package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.WholeWords;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A recorded disagreement between Entries about one topic (CONTEXT.md), reviewed by the Subject.
 * Each side states what one Source says and cites its Entries; {@code terms} decide when a question
 * is about the topic; {@code rule} records, for the reader, how answers must treat it. Code renders
 * the notice from these fields, never the model.
 */
public record Conflict(
    String id,
    String topic,
    List<String> terms,
    List<ConflictSide> sides,
    String note,
    String rule) {

  /** Terms this short ("Go") match only as written, since "go" is a common verb. */
  private static final int ANY_CASE_FROM_LENGTH = 3;

  public Conflict {
    terms = List.copyOf(terms);
    sides = List.copyOf(sides);
  }

  public List<EntryId> entryIds() {
    return sides.stream().flatMap(side -> side.entryIds().stream()).toList();
  }

  public boolean isNamedIn(String question) {
    return terms.stream()
        .anyMatch(
            term ->
                WholeWords.pattern(
                        term,
                        term.length() >= ANY_CASE_FROM_LENGTH ? Pattern.CASE_INSENSITIVE : 0)
                    .matcher(question)
                    .find());
  }

  public boolean involves(EntryId id) {
    return entryIds().contains(id);
  }

  /** What one Source says about the topic, in the Subject's words, with the Entries behind it. */
  public record ConflictSide(String text, List<EntryId> entryIds) {

    public ConflictSide {
      entryIds = List.copyOf(entryIds);
    }
  }
}
