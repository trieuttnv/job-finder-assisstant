package io.github.trieunv.assistant.eval;

import io.github.trieunv.assistant.eval.EvalItem.AdversarialKind;
import io.github.trieunv.assistant.eval.EvalItem.Category;
import io.github.trieunv.assistant.eval.EvalItem.Expectation;
import io.github.trieunv.assistant.eval.EvalItem.Split;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * The labelled questions, and the shape README Task 3, Step 1 promises for them: at least 6 per
 * category, at least 20 dev and 10 held-out, three reworded pairs, at least two recombination adversarials,
 * Gold Labels whose Entry ids resolve and whose patterns compile. The counts are minimums so a
 * burned held-out item can move to dev while a fresh one takes its place, and nothing is deleted.
 */
public record EvalDataset(List<EvalItem> items) {

  static final int PER_CATEGORY = 6;
  static final int DEV = 20;
  static final int HELDOUT = 10;
  static final int REWORDED_PAIRS = 3;
  static final int RECOMBINATIONS = 2;

  public EvalDataset {
    items = List.copyOf(items);
  }

  /** Everything wrong with the dataset, one line each; empty when it keeps every promise. */
  public List<String> problems(Set<String> knownEntryIds) {
    var problems = new ArrayList<String>();
    problems.addAll(duplicateIds());
    problems.addAll(categoryCounts());
    problems.addAll(splitCounts());
    var itemIds = items.stream().map(EvalItem::id).collect(Collectors.toSet());
    for (var item : items) {
      problems.addAll(itemProblems(item, knownEntryIds, itemIds));
    }
    problems.addAll(rewordedPairs());
    problems.addAll(adversarialKinds());
    return problems;
  }

  /** Ids of the items the Subject has not verified yet. */
  public List<String> unverifiedIds() {
    return items.stream().filter(item -> !item.verified()).map(EvalItem::id).toList();
  }

  private List<String> duplicateIds() {
    return countBy(EvalItem::id).entrySet().stream()
        .filter(entry -> entry.getValue() > 1)
        .map(entry -> entry.getKey() + ": the id is used " + entry.getValue() + " times")
        .toList();
  }

  private List<String> categoryCounts() {
    var byCategory = countBy(EvalItem::category);
    return Arrays.stream(Category.values())
        .filter(category -> byCategory.getOrDefault(category, 0L) < PER_CATEGORY)
        .map(
            category ->
                category
                    + ": "
                    + byCategory.getOrDefault(category, 0L)
                    + " items, fewer than "
                    + PER_CATEGORY)
        .toList();
  }

  private List<String> splitCounts() {
    var bySplit = countBy(EvalItem::split);
    return bySplit.getOrDefault(Split.DEV, 0L) >= DEV
            && bySplit.getOrDefault(Split.HELDOUT, 0L) >= HELDOUT
        ? List.of()
        : List.of(
            "split: " + bySplit + ", fewer than " + DEV + " dev or " + HELDOUT + " held-out");
  }

  private List<String> rewordedPairs() {
    var pairs = items.stream().filter(item -> item.rewordOfId().isPresent()).count();
    return pairs >= REWORDED_PAIRS
        ? List.of()
        : List.of("only " + pairs + " reworded pairs, not " + REWORDED_PAIRS);
  }

  private List<String> adversarialKinds() {
    var problems = new ArrayList<String>();
    var kinds =
        items.stream()
            .filter(item -> item.category() == Category.ADVERSARIAL)
            .map(EvalItem::kind)
            .filter(Objects::nonNull)
            .toList();
    if (kinds.stream().filter(AdversarialKind.RECOMBINATION::equals).count() < RECOMBINATIONS) {
      problems.add("fewer than " + RECOMBINATIONS + " recombination adversarials");
    }
    var missing = EnumSet.allOf(AdversarialKind.class);
    kinds.forEach(missing::remove);
    if (!missing.isEmpty()) {
      problems.add("no adversarial item of kind " + missing);
    }
    return problems;
  }

  private static List<String> itemProblems(
      EvalItem item, Set<String> knownEntryIds, Set<String> itemIds) {
    var problems = new ArrayList<String>();
    var gold = item.gold();
    gold.entryIds().stream()
        .filter(id -> !knownEntryIds.contains(id))
        .forEach(id -> problems.add(item.id() + ": gold Entry '" + id + "' does not exist"));
    for (var pattern : gold.mustNotClaim()) {
      try {
        Pattern.compile(pattern);
      } catch (PatternSyntaxException e) {
        problems.add(item.id() + ": mustNotClaim '" + pattern + "' is not a valid pattern");
      }
    }
    if (item.expect() == Expectation.ANSWER
        && (gold.entryIds().isEmpty() || gold.mustContain().isEmpty())) {
      problems.add(item.id() + ": an answer item needs gold Entry ids and mustContain terms");
    }
    if (item.expect() == Expectation.FALLBACK && !gold.mustContain().isEmpty()) {
      problems.add(item.id() + ": a fallback item can't require terms");
    }
    if (gold.mustContain().stream().anyMatch(List::isEmpty)) {
      problems.add(item.id() + ": an empty mustContain group can never match");
    }
    item.rewordOfId()
        .filter(original -> !itemIds.contains(original))
        .ifPresent(original -> problems.add(item.id() + ": rewords unknown item " + original));
    if ((item.category() == Category.ADVERSARIAL) != Objects.nonNull(item.kind())) {
      problems.add(item.id() + ": an adversarial item, and only one, needs a kind");
    }
    return problems;
  }

  private <K> Map<K, Long> countBy(Function<EvalItem, K> key) {
    return items.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
  }
}
