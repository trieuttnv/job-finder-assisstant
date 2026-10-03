package io.github.trieunv.assistant.eval;

import java.util.List;

/**
 * One metric over repetitions: mean and min–max, or empty when no repetition had a denominator.
 */
public record Stat(double mean, double min, double max, int repetitions) {

  public static Stat of(List<Double> values) {
    if (values.isEmpty()) {
      return new Stat(Double.NaN, Double.NaN, Double.NaN, 0);
    }
    var stats = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
    return new Stat(stats.getAverage(), stats.getMin(), stats.getMax(), values.size());
  }

  public boolean isEmpty() {
    return repetitions == 0;
  }
}
