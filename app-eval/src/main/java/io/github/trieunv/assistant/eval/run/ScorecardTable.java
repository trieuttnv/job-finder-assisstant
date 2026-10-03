package io.github.trieunv.assistant.eval.run;

import io.github.trieunv.assistant.eval.EvalItem.Category;
import io.github.trieunv.assistant.eval.Metric;
import io.github.trieunv.assistant.eval.Scorecard;
import io.github.trieunv.assistant.eval.Scorer;
import io.github.trieunv.assistant.eval.Stat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Prints a Scorecard as two text tables, by split and then by category. Each cell is the mean, and
 * the min–max over repetitions when they differ.
 */
public final class ScorecardTable {

  private ScorecardTable() {}

  public static String render(Scorecard card) {
    var categories =
        Arrays.stream(Category.values()).map(Scorer::scopeName).toList();
    var text = new StringBuilder();
    text.append(table(card, Scorecard.SPLIT_SCOPES)).append('\n');
    text.append(table(card, categories));
    if (card.hardLineFailures().isEmpty()) {
      text.append("\nHard Lines: passed.\n");
    } else {
      text.append("\nHard Lines FAILED:\n");
      card.hardLineFailures().forEach(failure -> text.append("  ").append(failure).append('\n'));
    }
    return text.toString();
  }

  private static String table(Scorecard card, List<String> scopes) {
    var rows = new ArrayList<List<String>>();
    var header = new ArrayList<String>();
    header.add("Metric");
    header.addAll(scopes);
    rows.add(header);
    for (var metric : Metric.values()) {
      var row = new ArrayList<String>();
      row.add(metric.label());
      scopes.forEach(scope -> row.add(cell(metric, card.get(scope, metric))));
      rows.add(row);
    }
    var widths = new int[header.size()];
    rows.forEach(
        row -> {
          for (var column = 0; column < row.size(); column++) {
            widths[column] = Math.max(widths[column], row.get(column).length());
          }
        });
    var text = new StringBuilder();
    for (var row : rows) {
      for (var column = 0; column < row.size(); column++) {
        var value = row.get(column);
        text.append(column == 0 ? pad(value, widths[column]) : padLeft(value, widths[column]));
        text.append(column + 1 < row.size() ? "  " : "\n");
      }
    }
    return text.toString();
  }

  static String cell(Metric metric, Stat stat) {
    if (stat.isEmpty()) {
      return "–";
    }
    var mean = format(metric, stat.mean());
    return stat.repetitions() == 1 || stat.min() == stat.max()
        ? mean
        : mean + " (" + format(metric, stat.min()) + "–" + format(metric, stat.max()) + ")";
  }

  private static String format(Metric metric, double value) {
    return switch (metric.unit()) {
      case SHARE -> String.format(Locale.ROOT, "%.0f%%", value * 100);
      case MILLISECONDS -> String.format(Locale.ROOT, "%.0f ms", value);
      case US_DOLLARS -> String.format(Locale.ROOT, "$%.4f", value);
    };
  }

  private static String pad(String value, int width) {
    return value + " ".repeat(width - value.length());
  }

  private static String padLeft(String value, int width) {
    return " ".repeat(width - value.length()) + value;
  }
}
