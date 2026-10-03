package io.github.trieunv.assistant.domain.knowledge;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * The comparison key for "word for word": NFKC, a line break after {@code -} or {@code /} joined
 * (PDF text wraps {@code high-level} and {@code SLO/SLI}), every other whitespace run made one
 * space. ADR 0009's key is NFKC plus whitespace collapsing; the joining is added here for PDF
 * text. The key is only ever compared, never stored: a Quote keeps its own text.
 */
final class QuoteText {

  private static final Pattern WRAP_AFTER_JOINER = Pattern.compile("(?<=[-/])[ \\t]*\\R\\s*");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private QuoteText() {}

  static String key(String text) {
    var normalised = Normalizer.normalize(text, Normalizer.Form.NFKC);
    var joined = WRAP_AFTER_JOINER.matcher(normalised).replaceAll("");
    return WHITESPACE.matcher(joined).replaceAll(" ").strip();
  }

  /**
   * Whether {@code part} occurs in {@code whole} starting and ending on word boundaries, so a cut
   * word ({@code ngineering}) or a cut number ({@code 3 engineers} out of {@code 13 engineers})
   * never matches.
   */
  static boolean containsWords(String whole, String part) {
    if (part.isEmpty()) {
      return false;
    }
    for (var at = whole.indexOf(part); at >= 0; at = whole.indexOf(part, at + 1)) {
      var end = at + part.length();
      if (isBoundary(whole, at - 1, part.charAt(0))
          && isBoundary(whole, end, part.charAt(part.length() - 1))) {
        return true;
      }
    }
    return false;
  }

  /** The character at {@code index} doesn't continue the word that {@code edge} belongs to. */
  private static boolean isBoundary(String text, int index, char edge) {
    return index < 0
        || index >= text.length()
        || !Character.isLetterOrDigit(edge)
        || !Character.isLetterOrDigit(text.charAt(index));
  }
}
