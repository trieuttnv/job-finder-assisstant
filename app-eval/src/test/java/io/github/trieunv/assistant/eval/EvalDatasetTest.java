package io.github.trieunv.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.adapter.out.yaml.YamlKnowledgeBasePersistenceAdapter;
import io.github.trieunv.assistant.domain.knowledge.KnowledgeCheck;
import io.github.trieunv.assistant.eval.EvalItem.Category;
import io.github.trieunv.assistant.eval.EvalItem.Expectation;
import io.github.trieunv.assistant.eval.EvalItem.GoldLabel;
import io.github.trieunv.assistant.eval.EvalItem.Split;
import io.github.trieunv.assistant.eval.yaml.YamlEvalDatasetReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The committed dataset keeps the shape README Task 3, Step 1 promises. */
class EvalDatasetTest {

  private static final Path DATASET = Path.of("../evals/dataset.yaml");
  private static final Path KNOWLEDGE = Path.of("../knowledge");

  @Test
  void shouldKeepEveryPromiseOfTheDatasetDesign_whenReadingTheCommittedDataset() {
    var dataset = new YamlEvalDatasetReader().read(DATASET);

    assertThat(dataset.items()).hasSizeGreaterThanOrEqualTo(30);
    assertThat(dataset.problems(knownEntryIds())).isEmpty();
  }

  @Test
  void shouldAcceptMoreThanTheMinimum_whenABurnedItemMovesToDevAndAFreshOneJoinsHeldOut() {
    var items = new ArrayList<EvalItem>();
    var n = 0;
    for (var category : Category.values()) {
      var size = category == Category.ADVERSARIAL ? 7 : 6;
      for (var i = 0; i < size; i++) {
        n++;
        var split = n <= 21 ? Split.DEV : Split.HELDOUT;
        items.add(item("q" + n, split, category, null, List.of("cv.role.atlassian")));
      }
    }

    var problems = new EvalDataset(items).problems(Set.of("cv.role.atlassian"));

    assertThat(problems)
        .noneMatch(line -> line.startsWith("ADVERSARIAL:"))
        .noneMatch(line -> line.startsWith("split:"));
  }

  @Test
  void shouldNameEachBrokenPromise_whenADatasetBreaksThem() {
    var items = new ArrayList<EvalItem>();
    for (var n = 1; n <= 5; n++) {
      items.add(item("q" + n, Category.CV_DIRECT, null, List.of("cv.role.atlassian")));
    }
    items.add(item("q6", Category.ADVERSARIAL, null, List.of("cv.role.nowhere")));

    var problems = new EvalDataset(items).problems(Set.of("cv.role.atlassian"));

    assertThat(problems)
        .anyMatch(line -> line.startsWith("CV_DIRECT: 5 items"))
        .anyMatch(line -> line.startsWith("split:"))
        .anyMatch(line -> line.equals("q6: gold Entry 'cv.role.nowhere' does not exist"))
        .anyMatch(line -> line.startsWith("q6: an adversarial item, and only one, needs a kind"))
        .anyMatch(line -> line.startsWith("only 0 reworded pairs"))
        .anyMatch(line -> line.startsWith("fewer than 2 recombination"));
  }

  @Test
  void shouldListUnverifiedItems_whenTheSubjectHasNotCheckedThem() {
    var dataset =
        new EvalDataset(
            List.of(item("q1", Category.CV_DIRECT, null, List.of("cv.role.atlassian"))));

    assertThat(dataset.unverifiedIds()).containsExactly("q1");
  }

  private static EvalItem item(
      String id, Category category, EvalItem.AdversarialKind kind, List<String> gold) {
    return item(id, Split.DEV, category, kind, gold);
  }

  private static EvalItem item(
      String id,
      Split split,
      Category category,
      EvalItem.AdversarialKind kind,
      List<String> gold) {
    return new EvalItem(
        id,
        split,
        category,
        kind,
        "Question?",
        Expectation.ANSWER,
        new GoldLabel(gold, List.of(List.of("term")), List.of()),
        null,
        false);
  }

  private static Set<String> knownEntryIds() {
    var knowledge = new YamlKnowledgeBasePersistenceAdapter(KNOWLEDGE);
    return KnowledgeCheck.check(knowledge.loadSnapshots(), knowledge.loadEntryDrafts()).stream()
        .map(entry -> entry.id().value())
        .collect(Collectors.toSet());
  }
}
