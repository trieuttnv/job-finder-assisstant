package io.github.trieunv.assistant.domain.answer;

import io.github.trieunv.assistant.domain.Hashing;
import java.util.Objects;

/**
 * A versioned system prompt. Its SHA-256 hash goes in every trace, so a trace shows exactly which
 * prompt text produced an answer even if a version's file was edited.
 */
public record SystemPrompt(String version, String text) {

  public SystemPrompt {
    Objects.requireNonNull(version, "Prompt version cannot be null");
    Objects.requireNonNull(text, "Prompt text cannot be null");
  }

  public String hash() {
    return Hashing.sha256(text);
  }
}
