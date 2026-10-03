package io.github.trieunv.assistant.domain.answer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.EntryType;
import io.github.trieunv.assistant.domain.knowledge.KnownNames;
import io.github.trieunv.assistant.domain.knowledge.NameList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Each Output Guardrail rule and normalisation, on Synthetic Entries shaped like the CV's. */
class OutputGuardrailTest {

  private static final Entry TAMARA =
      new Entry(
          EntryId.of("cv.role.tamara"),
          EntryType.ROLE,
          "cv",
          null,
          "Tamara",
          "Engineering Manager",
          "2022-10",
          "2026-09",
          "Engineering Manager Oct 2022 – Sep 2026 Tamara · Berlin, Germany");
  private static final Entry CARDS =
      bullet(
          "cv.role.tamara.b1",
          "cv.role.tamara",
          "Built two card products from scratch: designed for 60M DAU with ~500ms response time.");
  private static final Entry ALIPAY =
      new Entry(
          EntryId.of("cv.role.alipay"),
          EntryType.ROLE,
          "cv",
          null,
          "Alipay",
          "Tech Lead",
          "2017-09",
          "2022-09",
          "Tech Lead Sep 2017 – Sep 2022 Alipay (Alibaba Group)");
  private static final Entry SUCCESS_RATE =
      bullet(
          "cv.role.alipay.b4",
          "cv.role.alipay",
          "Payment success: Improved overall payment success rate by 4% across platforms.");
  private static final Entry LANGUAGES =
      new Entry(
          EntryId.of("cv.skills.languages"),
          EntryType.SKILL_GROUP,
          "cv",
          null,
          null,
          null,
          null,
          null,
          "Languages & Frameworks: Java · Spring Boot · Microservices");
  private static final Entry PROTOTYPE =
      new Entry(
          EntryId.of("gh.learn-outbox.readme"),
          EntryType.PROJECT,
          "gh",
          null,
          null,
          null,
          null,
          null,
          "Requires Go 1.23 only if building or testing outside Docker");

  private static final Map<EntryId, Entry> RETRIEVED =
      byId(TAMARA, CARDS, ALIPAY, SUCCESS_RATE, LANGUAGES, PROTOTYPE);

  private static final NameList NAME_LIST =
      new NameList(
          List.of("Java", "Spring Boot", "Go", "Kafka", "Docker"), Map.of("Go", List.of("Golang")));

  private final OutputGuardrail guardrail =
      new OutputGuardrail(
          List.copyOf(RETRIEVED.values()), KnownNames.from(RETRIEVED.values(), NAME_LIST), 0.5);

  @Test
  void shouldDropClaim_whenItCitesNothing() {
    assertDropped(claim("I am the best engineer."), DropReason.CITES_NOTHING);
  }

  @Test
  void shouldDropClaim_whenItCitesAnEntryNotRetrieved() {
    assertDropped(
        claim("I integrated 100+ banks.", "cv.role.vng.b3"), DropReason.CITES_ENTRY_NOT_RETRIEVED);
  }

  @Test
  void shouldKeepClaim_whenEveryNumberIsInItsCitedEntries() {
    assertKept(claim("The system was designed for 60M DAU at ~500ms.", "cv.role.tamara.b1"));
  }

  @Test
  void shouldDropClaim_whenANumberIsNotInItsCitedEntries() {
    assertDropped(
        claim("The system was designed for 70M DAU.", "cv.role.tamara.b1"),
        DropReason.NUMBER_NOT_IN_SOURCES);
  }

  @Test
  void shouldKeepClaim_whenANumberIsWrittenAsAWord() {
    assertKept(claim("I raised payment success by four percent.", "cv.role.alipay.b4"));
    assertKept(claim("I built two card products.", "cv.role.tamara.b1"));
  }

  @Test
  void shouldKeepClaim_whenANumberCarriesADifferentUnit() {
    assertKept(claim("It served 60 million DAU in about 500 ms.", "cv.role.tamara.b1"));
  }

  @Test
  void shouldDropClaim_whenANumberWordIsNotInItsCitedEntries() {
    assertDropped(
        claim("I built three card products.", "cv.role.tamara.b1"),
        DropReason.NUMBER_NOT_IN_SOURCES);
  }

  @Test
  void shouldKeepClaim_whenItsDatesMatchItsCitedRole() {
    assertKept(claim("I was there from October 2022 to September 2026.", "cv.role.tamara"));
  }

  @Test
  void shouldDropClaim_whenAYearIsNotInItsCitedEntries() {
    assertDropped(
        claim("I joined Tamara in October 2021.", "cv.role.tamara"),
        DropReason.NUMBER_NOT_IN_SOURCES);
  }

  @Test
  void shouldDropClaim_whenAMonthIsNotInItsCitedEntries() {
    assertDropped(
        claim("I joined Tamara in March 2022.", "cv.role.tamara"),
        DropReason.NUMBER_NOT_IN_SOURCES);
  }

  @Test
  void shouldDropClaim_whenItNamesAnEmployerNoEntryContains() {
    assertDropped(
        claim("I was CTO at Goldman.", "cv.role.tamara"), DropReason.NAME_NOT_IN_SOURCES);
  }

  @Test
  void shouldDropClaim_whenItPutsARealAchievementAtTheWrongEmployer() {
    assertDropped(
        claim("At Tamara I improved payment success by 4%.", "cv.role.alipay.b4"),
        DropReason.NAME_NOT_IN_SOURCES);
  }

  @Test
  void shouldDropClaim_whenItNamesATechnologyItsCitedEntriesDoNotContain() {
    assertDropped(
        claim("I am experienced in Go and Java.", "cv.skills.languages"),
        DropReason.NAME_NOT_IN_SOURCES);
  }

  @Test
  void shouldDropClaim_whenItNamesAListedTechnologyAtTheStartOfASentence() {
    assertDropped(
        claim("Kafka and Java are my tools.", "cv.skills.languages"),
        DropReason.NAME_NOT_IN_SOURCES);
  }

  @Test
  void shouldKeepClaim_whenAListedNameAppearsOnlyAsALowerCaseWord() {
    assertKept(claim("I react quickly with Java in spring.", "cv.skills.languages"));
  }

  @Test
  void shouldKeepClaim_whenItCitesABulletAndNamesItsRolesEmployerAndDates() {
    assertKept(
        claim("At Alipay I improved payment success by 4% between 2017 and 2022.", "cv.role.alipay.b4"));
  }

  @Test
  void shouldDropClaim_whenANumberHasADifferentScale() {
    assertDropped(
        claim("The system was designed for 60K DAU.", "cv.role.tamara.b1"),
        DropReason.NUMBER_NOT_IN_SOURCES);
  }

  @Test
  void shouldDropClaim_whenANumberHasAUnitTheGuardrailDoesNotKnow() {
    assertDropped(
        claim("The system handled 500TB daily.", "cv.role.alipay.b4"),
        DropReason.NUMBER_NOT_IN_SOURCES);
  }

  @Test
  void shouldKeepClaim_whenItUsesAKnownAliasOfANameInItsCitedEntries() {
    assertKept(claim("My prototypes need Golang 1.23 outside Docker.", "gh.learn-outbox.readme"));
  }

  @Test
  void shouldKeepClaim_whenItsOnlyCapitalsStartASentenceOrNameTheCv() {
    assertKept(
        claim("My CV lists Java and Spring Boot. Both are listed.", "cv.skills.languages"));
  }

  @Test
  void shouldWithholdTheAnswer_whenNoClaimSurvives() {
    var verdict =
        guardrail.check(List.of(claim("I was CTO at Goldman.", "cv.role.tamara")), RETRIEVED);

    assertThat(verdict.withheld()).isTrue();
  }

  @Test
  void shouldWithholdTheAnswer_whenTheModelProposesNoClaims() {
    assertThat(guardrail.check(List.of(), RETRIEVED).withheld()).isTrue();
  }

  @Test
  void shouldWithholdTheAnswer_whenMoreThanTheMaximumShareIsDropped() {
    var verdict =
        guardrail.check(
            List.of(
                claim("I built two card products.", "cv.role.tamara.b1"),
                claim("I was CTO at Goldman.", "cv.role.tamara"),
                claim("I built five card products.", "cv.role.tamara.b1")),
            RETRIEVED);

    assertThat(verdict.dropped()).hasSize(2);
    assertThat(verdict.withheld()).isTrue();
  }

  @Test
  void shouldShowTheAnswer_whenExactlyTheMaximumShareIsDropped() {
    var verdict =
        guardrail.check(
            List.of(
                claim("I built two card products.", "cv.role.tamara.b1"),
                claim("I was CTO at Goldman.", "cv.role.tamara")),
            RETRIEVED);

    assertThat(verdict.kept()).hasSize(1);
    assertThat(verdict.withheld()).isFalse();
  }

  private void assertKept(Claim claim) {
    var verdict = guardrail.check(List.of(claim), RETRIEVED);
    assertThat(verdict.dropped()).as("dropped: %s", verdict.dropped()).isEmpty();
  }

  private void assertDropped(Claim claim, DropReason reason) {
    var verdict = guardrail.check(List.of(claim), RETRIEVED);
    assertThat(verdict.dropped()).extracting(DroppedClaim::reason).containsExactly(reason);
  }

  private static Claim claim(String text, String... entryIds) {
    return new Claim(text, Arrays.stream(entryIds).map(EntryId::of).toList());
  }

  private static Entry bullet(String id, String parent, String quote) {
    return new Entry(
        EntryId.of(id), EntryType.BULLET, "cv", EntryId.of(parent), null, null, null, null, quote);
  }

  private static Map<EntryId, Entry> byId(Entry... entries) {
    return Arrays.stream(entries)
        .collect(
            Collectors.toMap(
                Entry::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
  }
}
