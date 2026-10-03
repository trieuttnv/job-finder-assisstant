package io.github.trieunv.assistant.domain.answer;

import java.math.BigDecimal;
import java.time.Month;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The normalisations the Output Guardrail compares with, applied the same way to a claim and to
 * its cited Entries: number words become digits, thousands separators and units are stripped
 * ("four percent" and "4%" are both 4), a scale is read into the value ("60 million" and "60M"
 * are the same, "60K" is not), and month names are read as months.
 */
final class ClaimText {

  private static final Map<String, Integer> NUMBER_WORDS =
      Map.ofEntries(
          Map.entry("zero", 0),
          Map.entry("one", 1),
          Map.entry("two", 2),
          Map.entry("three", 3),
          Map.entry("four", 4),
          Map.entry("five", 5),
          Map.entry("six", 6),
          Map.entry("seven", 7),
          Map.entry("eight", 8),
          Map.entry("nine", 9),
          Map.entry("ten", 10),
          Map.entry("eleven", 11),
          Map.entry("twelve", 12),
          Map.entry("thirteen", 13),
          Map.entry("fourteen", 14),
          Map.entry("fifteen", 15),
          Map.entry("sixteen", 16),
          Map.entry("seventeen", 17),
          Map.entry("eighteen", 18),
          Map.entry("nineteen", 19),
          Map.entry("twenty", 20),
          Map.entry("thirty", 30),
          Map.entry("forty", 40),
          Map.entry("fifty", 50),
          Map.entry("sixty", 60),
          Map.entry("seventy", 70),
          Map.entry("eighty", 80),
          Map.entry("ninety", 90),
          Map.entry("hundred", 100),
          Map.entry("dozen", 12));

  private static final Pattern NUMBER_WORD =
      Pattern.compile(
          "(?<![\\p{L}])("
              + String.join("|", NUMBER_WORDS.keySet())
              + ")(?:-(\\p{L}+))?(?![\\p{L}])",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern THOUSANDS = Pattern.compile("(?<=\\d),(?=\\d{3}(?!\\d))");

  /**
   * A number, with a scale written after it ("60M", "60 million") read into its value. Any other
   * letters glued to it (a unit such as "ms" or "TB", or "123Pay") are ignored, so the number is
   * always checked.
   */
  static final Pattern NUMBER =
      Pattern.compile(
          "(?<![\\p{L}\\p{N}.])(\\d+(?:\\.\\d+)?)"
              + "(?:\\s?(thousand|million|billion|bn|k|m|b)(?![\\p{L}]))?",
          Pattern.CASE_INSENSITIVE);

  private static final Map<String, BigDecimal> SCALES =
      Map.of(
          "thousand", BigDecimal.valueOf(1_000),
          "k", BigDecimal.valueOf(1_000),
          "million", BigDecimal.valueOf(1_000_000),
          "m", BigDecimal.valueOf(1_000_000),
          "billion", BigDecimal.valueOf(1_000_000_000),
          "bn", BigDecimal.valueOf(1_000_000_000),
          "b", BigDecimal.valueOf(1_000_000_000));

  /** Capitalised only, so "may" the verb is not May. */
  private static final Pattern MONTH =
      Pattern.compile(
          "(?<![\\p{L}])(January|February|March|April|May|June|July|August|September|October"
              + "|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sept|Sep|Oct|Nov|Dec)(?![\\p{L}])");

  private ClaimText() {}

  /** Every number in {@code text} as its value, so "60M" and "60 million" are both 60000000. */
  static Set<String> numbers(String text) {
    var digits = THOUSANDS.matcher(numberWordsAsDigits(text)).replaceAll("");
    var found = new LinkedHashSet<String>();
    var matcher = NUMBER.matcher(digits);
    while (matcher.find()) {
      var scale =
          Optional.ofNullable(matcher.group(2))
              .map(word -> SCALES.get(word.toLowerCase(Locale.ROOT)))
              .orElse(BigDecimal.ONE);
      found.add(new BigDecimal(matcher.group(1)).multiply(scale).stripTrailingZeros().toPlainString());
    }
    return found;
  }

  static Set<Month> months(String text) {
    var found = new LinkedHashSet<Month>();
    var matcher = MONTH.matcher(text);
    while (matcher.find()) {
      found.add(monthOf(matcher.group(1)));
    }
    return found;
  }

  static boolean isMonth(String word) {
    return MONTH.matcher(word).matches();
  }

  private static String numberWordsAsDigits(String text) {
    return NUMBER_WORD
        .matcher(text)
        .replaceAll(
            match -> {
              var value = NUMBER_WORDS.get(match.group(1).toLowerCase(Locale.ROOT));
              // "twenty-five" is 25; "ten-year" stays 10-year.
              var second = match.group(2);
              var ones =
                  Optional.ofNullable(second)
                      .map(word -> NUMBER_WORDS.get(word.toLowerCase(Locale.ROOT)))
                      .orElse(null);
              return Objects.isNull(ones) || value < 20 || ones >= 10
                  ? value + Optional.ofNullable(second).map(word -> "-" + word).orElse("")
                  : String.valueOf(value + ones);
            });
  }

  private static Month monthOf(String name) {
    for (var month : Month.values()) {
      if (month.name().substring(0, 3).equalsIgnoreCase(name.substring(0, 3))) {
        return month;
      }
    }
    throw new IllegalArgumentException("Not a month: " + name);
  }
}
