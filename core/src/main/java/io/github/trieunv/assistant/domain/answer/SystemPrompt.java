package io.github.trieunv.assistant.domain.answer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JDK", e);
    }
  }
}
