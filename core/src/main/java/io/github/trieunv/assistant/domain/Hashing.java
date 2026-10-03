package io.github.trieunv.assistant.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Hashing {

  private Hashing() {}

  /** Lower-case hex SHA-256 of the UTF-8 bytes of {@code text}. */
  public static String sha256(String text) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JDK", e);
    }
  }
}
