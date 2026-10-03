package io.github.trieunv.assistant.domain.knowledge;

import io.github.trieunv.assistant.domain.exception.AssistantDomainException;
import java.util.Arrays;

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

  public static EntryType fromCode(String code) {
    return Arrays.stream(values())
        .filter(type -> type.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new AssistantDomainException("Unknown Entry type: " + code));
  }
}
