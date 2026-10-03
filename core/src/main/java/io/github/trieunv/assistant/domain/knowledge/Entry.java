package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.Hashing;
import io.github.trieunv.assistant.domain.exception.AssistantDomainException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    if (Objects.isNull(id) || Objects.isNull(type) || Objects.isNull(source)) {
      throw new AssistantDomainException("Entry " + id + " needs an id, a type and a Source");
    }
    if (Objects.isNull(quote) || quote.isBlank()) {
      throw new AssistantDomainException("Entry " + id + " has no Quote");
    }
  }

  /** SHA-256 over every field, so the id report can tell a changed Entry from an unchanged one. */
  public String fingerprint() {
    return Hashing.sha256(
        Stream.of(type.code(), source, parent, employer, title, start, end, quote)
            .map(field -> Objects.toString(field, ""))
            .collect(Collectors.joining("\u0000")));
  }
}
