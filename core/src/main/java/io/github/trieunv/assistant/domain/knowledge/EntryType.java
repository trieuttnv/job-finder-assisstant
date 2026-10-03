package io.github.trieunv.assistant.domain.knowledge;

import java.util.Arrays;
import java.util.Optional;

public enum EntryType {
  ROLE("role"),
  BULLET("bullet"),
  SKILL_GROUP("skill-group"),
  EDUCATION("education"),
  SUMMARY("summary"),
  PROJECT("project");

  private final String code;

  EntryType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** Empty when {@code code} is not in the fixed list. */
  public static Optional<EntryType> fromCode(String code) {
    return Arrays.stream(values()).filter(type -> type.code.equals(code)).findFirst();
  }
}
