package io.github.trieunv.assistant.domain.knowledge;

import java.util.Objects;

/** Readable, stable id of an {@link Entry}, such as {@code cv.role.tamara.b1}. */
public record EntryId(String value) {

  public EntryId {
    Objects.requireNonNull(value, "Entry id cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Entry id cannot be blank");
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
