package io.github.trieunv.assistant.domain;

import java.util.regex.Pattern;

public final class WholeWords {

  private WholeWords() {}

  /** Finds {@code phrase} only where it isn't part of a longer word or number. */
  public static Pattern pattern(String phrase, int flags) {
    return Pattern.compile(
        "(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])", flags);
  }
}
