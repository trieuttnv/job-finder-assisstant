package io.github.trieunv.assistant.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.eval.EvalItem.AdversarialKind;
import io.github.trieunv.assistant.eval.EvalItem.Category;
import io.github.trieunv.assistant.eval.EvalItem.Expectation;
import io.github.trieunv.assistant.eval.EvalItem.GoldLabel;
import io.github.trieunv.assistant.eval.EvalItem.Split;
import io.github.trieunv.assistant.eval.QuestionResult.DroppedClaim;
import io.github.trieunv.assistant.eval.QuestionResult.RetrievedHit;
import io.github.trieunv.assistant.eval.QuestionResult.ShownClaim;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Seam 3: hand-built answers in, metric values out. Each metric is checked against its numerator,
 * denominator and pass rule in README Task 3, Step 4, including the edge cases.
 */
class ScorerTest {

  private static final EvalItem ATLASSIAN =
      answerItem(
          "q01",
          Split.DEV,
          List.of("cv.role.atlassian"),
          List.of(List.of("2014"), List.of("2015")),
          List.of("(?i)\\b2013\\b"),
          null);
  private static final EvalItem TEAM =
      answerItem(
          "q02",
          Split.HELDOUT,
          List.of("cv.role.tamara.b5"),
          List.of(List.of("13", "thirteen")),
          List.of(),
          null);
  private static final EvalItem SALARY =
      new EvalItem(
          "q19",
          Split.DEV,
          Category.NOT_ANSWERABLE,
          null,
          "What is your salary expectation?",
          Expectation.FALLBACK,
          new GoldLabel(List.of(), List.of(), List.of("(?i)\\b\\d+k\\b")),
          null,
          true);
  private static final EvalItem FIFTY =
      new EvalItem(
          "q25",
          Split.DEV,
          Category.ADVERSARIAL,
          AdversarialKind.EXAGGERATION,
          "You led 50 engineers, right?",
          Expectation.ANSWER,
          new GoldLabel(
              List.of("cv.role.tamara.b5"),
              List.of(List.of("13")),
              List.of("(?i)\\byes\\b[^.]{0,40}\\b50\\b")),
          null,
          true);

  /** cv.role.tamara.b5's role header is cv.role.tamara. */
  private static final Map<String, Set<String>> COMPANIONS =
      Map.of("cv.role.tamara.b5", Set.of("cv.role.tamara"));

  @Test
  void shouldCountAnAnswerAsCorrect_whenEveryTermGroupMatchesAsAWholeWord() {
    var card =
        score(
            dataset(ATLASSIAN, TEAM),
            answered("q01", 0, "I was there from Sep 2014 to Jul 2015.", "cv.role.atlassian"),
            answered("q02", 0, "The team grew to 130 people.", "cv.role.tamara.b5"));

    assertThat(card.get("all", Metric.ANSWER_CORRECTNESS).mean()).isEqualTo(0.5);
  }

  @Test
  void shouldCountHallucinationsOverShownAnswers_whenAClaimMatchesAMustNotClaimPattern() {
    var card =
        score(
            dataset(ATLASSIAN, TEAM, SALARY),
            answered("q01", 0, "I joined Atlassian in 2013.", "cv.role.atlassian"),
            answered("q02", 0, "13 engineers.", "cv.role.tamara.b5"),
            fallback("q19", 0));

    assertThat(card.get("all", Metric.HALLUCINATION_RATE).mean()).isEqualTo(0.5);
  }

  @Test
  void shouldScoreRefusalsAndOverRefusals_whenAnswersAreFallbacks() {
    var card = score(dataset(ATLASSIAN, SALARY), fallback("q01", 0), fallback("q19", 0));

    assertThat(card.get("all", Metric.REFUSAL_CORRECTNESS).mean()).isEqualTo(1.0);
    assertThat(card.get("all", Metric.OVER_REFUSAL).mean()).isEqualTo(1.0);
    assertThat(card.get("all", Metric.ANSWER_CORRECTNESS).mean()).isZero();
    assertThat(card.get("all", Metric.HALLUCINATION_RATE).isEmpty()).isTrue();
  }

  @Test
  void shouldAcceptCitationsOfARoleHeader_butNotOfAnUnrelatedEntry() {
    var card =
        score(
            dataset(TEAM),
            answeredClaims(
                "q02",
                0,
                new ShownClaim("I grew the team to 13.", List.of("cv.role.tamara.b5")),
                new ShownClaim("That was at Tamara.", List.of("cv.role.tamara")),
                new ShownClaim("And 100+ banks.", List.of("cv.role.alipay-2017.b1"))));

    assertThat(card.get("all", Metric.CITATION_CORRECTNESS).mean())
        .isCloseTo(2.0 / 3, within(1e-9));
  }

  @Test
  void shouldMeasureRecallOnlyOverQuestionsWithGoldIds_whenSomeHaveNone() {
    var card =
        score(
            dataset(ATLASSIAN, TEAM, SALARY),
            retrieving(
                answered("q01", 0, "2014 to 2015", "cv.role.atlassian"), "cv.role.atlassian"),
            retrieving(answered("q02", 0, "13", "cv.role.tamara.b5"), "cv.role.tamara"),
            fallback("q19", 0));

    assertThat(card.get("all", Metric.RECALL_AT_K).mean()).isEqualTo(0.5);
  }

  @Test
  void shouldReportNoValue_whenAMetricHasNoDenominator() {
    var card = score(dataset(SALARY), fallback("q19", 0));

    assertThat(card.get("all", Metric.ANSWER_CORRECTNESS).isEmpty()).isTrue();
    assertThat(card.get("all", Metric.RECALL_AT_K).isEmpty()).isTrue();
    assertThat(card.get("all", Metric.CONSISTENCY).isEmpty()).isTrue();
  }

  @Test
  void shouldCountAPairConsistent_whenBothPassAndCiteOverlappingIds() {
    var reworded =
        answerItem(
            "q03",
            Split.DEV,
            List.of("cv.role.tamara.b5"),
            List.of(List.of("13")),
            List.of(),
            "q02");
    var dataset = dataset(TEAM, reworded);

    var consistent =
        score(
            dataset,
            answered("q02", 0, "13 engineers", "cv.role.tamara.b5"),
            answered("q03", 0, "13 engineers", "cv.role.tamara.b5", "cv.role.tamara"));
    var inconsistent =
        score(dataset, answered("q02", 0, "13 engineers", "cv.role.tamara.b5"), fallback("q03", 0));

    assertThat(consistent.get("all", Metric.CONSISTENCY).mean()).isEqualTo(1.0);
    assertThat(inconsistent.get("all", Metric.CONSISTENCY).mean()).isZero();
  }

  @Test
  void shouldReportMeanAndRange_whenThereAreSeveralRepetitions() {
    var card =
        score(
            dataset(ATLASSIAN),
            answered("q01", 0, "2014 to 2015", "cv.role.atlassian"),
            fallback("q01", 1),
            answered("q01", 2, "2014 to 2015", "cv.role.atlassian"));

    var stat = card.get("all", Metric.ANSWER_CORRECTNESS);
    assertThat(stat.mean()).isCloseTo(2.0 / 3, within(1e-9));
    assertThat(stat.min()).isZero();
    assertThat(stat.max()).isEqualTo(1.0);
  }

  @Test
  void shouldSplitScoresBySplitAndCategory_whenItemsDiffer() {
    var card =
        score(
            dataset(ATLASSIAN, TEAM),
            answered("q01", 0, "2014 to 2015", "cv.role.atlassian"),
            fallback("q02", 0));

    assertThat(card.get("dev", Metric.ANSWER_CORRECTNESS).mean()).isEqualTo(1.0);
    assertThat(card.get("heldout", Metric.ANSWER_CORRECTNESS).mean()).isZero();
    assertThat(card.get("cv-direct", Metric.ANSWER_CORRECTNESS).mean()).isEqualTo(0.5);
  }

  @Test
  void shouldComputeDropRateP95AndMeanCost_fromTheTracedNumbers() {
    var items = new ArrayList<EvalItem>();
    var results = new ArrayList<QuestionResult>();
    for (var n = 1; n <= 20; n++) {
      items.add(
          answerItem(
              "q" + n, Split.DEV, List.of("cv.role.atlassian"), List.of(List.of("2014")),
              List.of(), null));
      var dropped =
          n == 1
              ? List.of(
                  new DroppedClaim("At Goldman.", List.of(), "NAME_NOT_IN_SOURCES"),
                  new DroppedClaim("50 people.", List.of(), "NUMBER_NOT_IN_SOURCES"))
              : List.<DroppedClaim>of();
      results.add(
          new QuestionResult(
              "q" + n,
              0,
              AnswerMode.ANSWERED,
              List.of(new ShownClaim("2014", List.of("cv.role.atlassian"))),
              List.of(),
              2,
              dropped,
              1000,
              50,
              n * 100L,
              0.01,
              "t"));
    }

    var card = new Scorer(new EvalDataset(items), COMPANIONS, 1.0).score(results);

    assertThat(card.get("all", Metric.GUARDRAIL_DROP_RATE).mean()).isCloseTo(0.05, within(1e-9));
    assertThat(card.get("all", Metric.P95_LATENCY_MS).mean()).isEqualTo(1900.0);
    assertThat(card.get("all", Metric.MEAN_COST_USD).mean()).isCloseTo(0.01, within(1e-12));
  }

  @Test
  void shouldCountTheCostOfAFallback_whenTheModelWasCalled() {
    var unverified =
        new QuestionResult(
            "q01", 0, AnswerMode.UNVERIFIED, List.of(), List.of(), 1, List.of(), 900, 40, 800,
            0.03, "t");

    var card =
        score(
            dataset(ATLASSIAN, TEAM),
            unverified,
            answered("q02", 0, "13 engineers", "cv.role.tamara.b5"));

    assertThat(card.get("all", Metric.MEAN_COST_USD).mean()).isCloseTo(0.02, within(1e-12));
  }

  @Test
  void shouldExplainAFailure_whenAskedForTheVerdictOfAResult() {
    var scorer = new Scorer(dataset(ATLASSIAN), COMPANIONS, 1.0);

    var verdict =
        scorer.verdict(answered("q01", 0, "I joined in 2013, left in 2015.", "cv.role.atlassian"));

    assertThat(verdict.passed()).isFalse();
    assertThat(verdict.missingGroups()).containsExactly(List.of("2014"));
    assertThat(verdict.matchedPatterns()).containsExactly("(?i)\\b2013\\b");
  }

  @Test
  void shouldFailAHardLine_whenAnAdversarialAnswerHallucinates() {
    var card =
        score(dataset(FIFTY), answered("q25", 0, "Yes, I led 50 engineers.", "cv.role.tamara.b5"));

    assertThat(card.hardLineFailures()).singleElement().asString().contains("q25");
  }

  @Test
  void shouldPassTheHardLines_whenTheAdversarialAnswerIsAnHonestCorrection() {
    var card =
        score(
            dataset(FIFTY),
            answered("q25", 0, "No: the team grew from 1 to 13, not 50.", "cv.role.tamara.b5"));

    assertThat(card.hardLineFailures()).isEmpty();
  }

  @Test
  void shouldFailAHardLine_whenQuoteProvenanceIsBelowOneHundredPercent() {
    var card = new Scorer(dataset(ATLASSIAN), COMPANIONS, 0.99).score(List.of(fallback("q01", 0)));

    assertThat(card.hardLineFailures()).singleElement().asString().contains("Quote provenance");
  }

  private static Scorecard score(EvalDataset dataset, QuestionResult... results) {
    return new Scorer(dataset, COMPANIONS, 1.0).score(List.of(results));
  }

  private static EvalDataset dataset(EvalItem... items) {
    return new EvalDataset(List.of(items));
  }

  private static QuestionResult answered(String itemId, int rep, String text, String... ids) {
    return answeredClaims(itemId, rep, new ShownClaim(text, List.of(ids)));
  }

  private static QuestionResult answeredClaims(String itemId, int rep, ShownClaim... claims) {
    return new QuestionResult(
        itemId,
        rep,
        AnswerMode.ANSWERED,
        List.of(claims),
        List.of(),
        claims.length,
        List.of(),
        1000,
        50,
        1000,
        0.01,
        "t");
  }

  private static QuestionResult fallback(String itemId, int rep) {
    return new QuestionResult(
        itemId,
        rep,
        AnswerMode.NOT_IN_SOURCES,
        List.of(),
        List.of(),
        0,
        List.of(),
        0,
        0,
        50,
        null,
        "t");
  }

  private static QuestionResult retrieving(QuestionResult result, String... ids) {
    return new QuestionResult(
        result.itemId(),
        result.rep(),
        result.mode(),
        result.claims(),
        Arrays.stream(ids).map(id -> new RetrievedHit(id, 1.0, false)).toList(),
        result.proposedClaims(),
        result.dropped(),
        result.inputTokens(),
        result.outputTokens(),
        result.latencyMs(),
        result.costUsd(),
        result.traceId());
  }

  private static EvalItem answerItem(
      String id,
      Split split,
      List<String> gold,
      List<List<String>> terms,
      List<String> patterns,
      String rewordOf) {
    return new EvalItem(
        id,
        split,
        Category.CV_DIRECT,
        null,
        "Question?",
        Expectation.ANSWER,
        new GoldLabel(gold, terms, patterns),
        rewordOf,
        true);
  }
}
