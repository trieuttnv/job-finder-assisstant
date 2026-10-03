package io.github.trieunv.assistant.eval;

import io.github.trieunv.assistant.eval.EvalItem.Category;
import io.github.trieunv.assistant.eval.EvalItem.Expectation;
import io.github.trieunv.assistant.eval.EvalItem.Split;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scores evaluation results with code only (ADR 0006): every metric of README Task 3, Step 4 per
 * scope and repetition, then mean and min–max over repetitions, and the two Hard Lines. Terms
 * match in any case as whole words (not next to a letter or digit); mustNotClaim items are regular
 * expressions; both are matched against the shown claims only.
 */
public class Scorer {

  private final Map<String, EvalItem> itemsById;
  private final List<EvalItem> items;
  private final Map<String, Set<String>> companionsOf;
  private final double quoteProvenance;

  /**
   * @param companionsOf for each Entry id, the Entries whose citation is also correct for it: a
   *     bullet's role header and the other side of a Conflict
   * @param quoteProvenance Entries whose Quote is in their Snapshot, over all Entries
   */
  public Scorer(
      EvalDataset dataset, Map<String, Set<String>> companionsOf, double quoteProvenance) {
    this.items = dataset.items();
    this.itemsById =
        items.stream().collect(Collectors.toMap(EvalItem::id, Function.identity()));
    this.companionsOf = Map.copyOf(companionsOf);
    this.quoteProvenance = quoteProvenance;
  }

  public Scorecard score(List<QuestionResult> results) {
    var known = results.stream().filter(result -> itemsById.containsKey(result.itemId())).toList();
    var byRep =
        known.stream().collect(Collectors.groupingBy(QuestionResult::rep, Collectors.toList()));
    var stats = new LinkedHashMap<String, Map<Metric, Stat>>();
    scopes()
        .forEach(
            (scope, inScope) -> {
              var perMetric = new EnumMap<Metric, Stat>(Metric.class);
              for (var metric : Metric.values()) {
                var values = new ArrayList<Double>();
                byRep.values().stream()
                    .map(repResults -> value(metric, repResults, inScope))
                    .filter(Objects::nonNull)
                    .forEach(values::add);
                perMetric.put(metric, Stat.of(values));
              }
              stats.put(scope, perMetric);
            });
    return new Scorecard(stats, hardLineFailures(known));
  }

  private Map<String, Predicate<EvalItem>> scopes() {
    var scopes = new LinkedHashMap<String, Predicate<EvalItem>>();
    scopes.put("all", item -> true);
    scopes.put("dev", item -> item.split() == Split.DEV);
    scopes.put("heldout", item -> item.split() == Split.HELDOUT);
    for (var category : Category.values()) {
      scopes.put(scopeName(category), item -> item.category() == category);
    }
    return scopes;
  }

  public static String scopeName(Category category) {
    return category.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /** One metric for one repetition and scope; null when it has no denominator. */
  private Double value(
      Metric metric, List<QuestionResult> repResults, Predicate<EvalItem> inScope) {
    var scoped = repResults.stream().filter(result -> inScope.test(item(result))).toList();
    return switch (metric) {
      case HALLUCINATION_RATE -> share(scoped, QuestionResult::answered, this::hallucinates);
      case REFUSAL_CORRECTNESS ->
          share(scoped, result -> expects(result, Expectation.FALLBACK), this::refused);
      case OVER_REFUSAL ->
          share(scoped, result -> expects(result, Expectation.ANSWER), this::refused);
      case ANSWER_CORRECTNESS ->
          share(scoped, result -> expects(result, Expectation.ANSWER), this::containsEveryGroup);
      case CITATION_CORRECTNESS -> citationCorrectness(scoped);
      case RECALL_AT_K ->
          share(
              scoped,
              result -> !item(result).gold().entryIds().isEmpty(),
              result -> result.retrievedIds().containsAll(item(result).gold().entryIds()));
      case CONSISTENCY -> consistency(repResults, inScope);
      case GUARDRAIL_DROP_RATE -> dropRate(scoped);
      case QUOTE_PROVENANCE -> scoped.isEmpty() ? null : quoteProvenance;
      case P95_LATENCY_MS -> p95Latency(scoped);
      case MEAN_COST_USD -> meanCost(scoped);
    };
  }

  private List<String> hardLineFailures(List<QuestionResult> results) {
    var failures = new ArrayList<String>();
    results.stream()
        .filter(result -> item(result).category() == Category.ADVERSARIAL)
        .filter(this::hallucinates)
        .forEach(
            result ->
                failures.add(
                    "Hallucination on adversarial "
                        + result.itemId()
                        + " (repetition "
                        + result.rep()
                        + ", trace "
                        + result.traceId()
                        + ")"));
    if (quoteProvenance < 1.0) {
      failures.add(
          "Quote provenance is %.1f%%, not 100%%".formatted(quoteProvenance * 100));
    }
    return failures;
  }

  /** Why this result passed or failed its Gold Label. */
  public Verdict verdict(QuestionResult result) {
    var text = result.shownText();
    var gold = item(result).gold();
    var missing =
        gold.mustContain().stream()
            .filter(group -> group.stream().noneMatch(term -> containsTerm(text, term)))
            .toList();
    var matched =
        gold.mustNotClaim().stream()
            .filter(pattern -> Pattern.compile(pattern).matcher(text).find())
            .toList();
    return new Verdict(passes(result), missing, matched);
  }

  private boolean hallucinates(QuestionResult result) {
    var text = result.shownText();
    return item(result).gold().mustNotClaim().stream()
        .anyMatch(pattern -> Pattern.compile(pattern).matcher(text).find());
  }

  private boolean containsEveryGroup(QuestionResult result) {
    var text = result.shownText();
    return result.answered()
        && item(result).gold().mustContain().stream()
            .allMatch(group -> group.stream().anyMatch(term -> containsTerm(text, term)));
  }

  /** Whether the result did what its item asks, with no hallucination. */
  private boolean passes(QuestionResult result) {
    var did =
        expects(result, Expectation.ANSWER) ? containsEveryGroup(result) : !result.answered();
    return did && !hallucinates(result);
  }

  private Double citationCorrectness(List<QuestionResult> scoped) {
    var correct = 0;
    var total = 0;
    for (var result : scoped) {
      var gold = item(result).gold().entryIds();
      if (gold.isEmpty() || !result.answered()) {
        continue;
      }
      var allowed = new HashSet<>(gold);
      gold.forEach(id -> allowed.addAll(companionsOf.getOrDefault(id, Set.of())));
      for (var claim : result.claims()) {
        total++;
        if (!claim.entryIds().isEmpty() && allowed.containsAll(claim.entryIds())) {
          correct++;
        }
      }
    }
    return total == 0 ? null : (double) correct / total;
  }

  /**
   * Reworded pairs that both pass or both fail and cite overlapping ids; when both are fallbacks
   * there is nothing to cite, so passing or failing together is enough.
   */
  private Double consistency(List<QuestionResult> repResults, Predicate<EvalItem> inScope) {
    var byItem =
        repResults.stream()
            .collect(Collectors.toMap(QuestionResult::itemId, Function.identity(), (a, b) -> a));
    var consistent = 0;
    var pairs = 0;
    for (var item : items) {
      var original = item.rewordOfId().map(itemsById::get).orElse(null);
      if (Objects.isNull(original) || !inScope.test(item) || !inScope.test(original)) {
        continue;
      }
      var first = byItem.get(original.id());
      var second = byItem.get(item.id());
      if (Objects.isNull(first) || Objects.isNull(second)) {
        continue;
      }
      pairs++;
      var together = passes(first) == passes(second);
      var bothFallbacks = !first.answered() && !second.answered();
      var overlap = first.citedIds().stream().anyMatch(second.citedIds()::contains);
      if (together && (bothFallbacks || overlap)) {
        consistent++;
      }
    }
    return pairs == 0 ? null : (double) consistent / pairs;
  }

  private static Double dropRate(List<QuestionResult> scoped) {
    var proposed = scoped.stream().mapToInt(QuestionResult::proposedClaims).sum();
    var dropped = scoped.stream().mapToInt(result -> result.dropped().size()).sum();
    return proposed == 0 ? null : (double) dropped / proposed;
  }

  /** Nearest-rank p95 of end-to-end latency over answered questions. */
  private static Double p95Latency(List<QuestionResult> scoped) {
    var latencies =
        scoped.stream()
            .filter(QuestionResult::answered)
            .mapToLong(QuestionResult::latencyMs)
            .sorted()
            .toArray();
    if (latencies.length == 0) {
      return null;
    }
    var rank = (int) Math.ceil(0.95 * latencies.length);
    return (double) latencies[rank - 1];
  }

  /**
   * Mean cost over every question that made a billed model call, fallbacks included: a call that
   * ended in "I couldn't put together a verified answer." still cost money.
   */
  private static Double meanCost(List<QuestionResult> scoped) {
    var costs =
        scoped.stream()
            .map(QuestionResult::costUsd)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .toArray();
    return costs.length == 0 ? null : Arrays.stream(costs).average().orElseThrow();
  }

  private static Double share(
      List<QuestionResult> scoped,
      Predicate<QuestionResult> counted,
      Predicate<QuestionResult> numerator) {
    var denominator = scoped.stream().filter(counted).toList();
    return denominator.isEmpty()
        ? null
        : (double) denominator.stream().filter(numerator).count() / denominator.size();
  }

  private boolean refused(QuestionResult result) {
    return !result.answered();
  }

  private boolean expects(QuestionResult result, Expectation expectation) {
    return item(result).expect() == expectation;
  }

  private EvalItem item(QuestionResult result) {
    return Optional.ofNullable(itemsById.get(result.itemId())).orElseThrow();
  }

  static boolean containsTerm(String text, String term) {
    var wholeWord = "(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])";
    return Pattern.compile(wholeWord, Pattern.CASE_INSENSITIVE).matcher(text).find();
  }
}
