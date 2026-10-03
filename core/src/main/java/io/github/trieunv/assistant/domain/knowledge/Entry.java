package io.github.trieunv.assistant.domain.knowledge;

import java.util.Objects;

/**
 * One typed, self-contained statement from a Source, with the verbatim Quote it came from. The
 * typed fields (employer, title, start, end) are optional and only set where the Quote states them;
 * {@code parent} links a bullet to its role.
 */
public record Entry(
    EntryId id,
    EntryType type,
    String source,
    EntryId parent,
    String employer,
    String title,
    String start,
    String end,
    String quote) {

  public Entry {
    Objects.requireNonNull(id, "Entry id cannot be null");
    Objects.requireNonNull(type, "Entry type cannot be null");
    Objects.requireNonNull(source, "Entry source cannot be null");
    Objects.requireNonNull(quote, "Entry quote cannot be null");
  }
}
