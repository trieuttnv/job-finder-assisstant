package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.exception.AssistantDomainException;
import java.util.Objects;

/** Readable, stable id of an {@link Entry}, such as {@code cv.role.tamara.b1}. */
public record EntryId(String value) {

  public EntryId {
    if (Objects.isNull(value) || value.isBlank()) {
      throw new AssistantDomainException("Entry id cannot be blank");
    }
  }

  public static EntryId of(String value) {
    return new EntryId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
