package io.github.trieunv.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.trieunv.assistant.adapter.out.state.JsonKnowledgeStateAdapter;
import io.github.trieunv.assistant.adapter.out.yaml.YamlKnowledgeBasePersistenceAdapter;
import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.application.service.LoadKnowledgeService;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.InvalidKnowledgeException;
import io.github.trieunv.assistant.domain.knowledge.KnowledgeProblem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Seam 2: ingest. Each test writes a Synthetic fixture Snapshot, its {@code sources.yaml} record
 * and an Entry file, then loads them through the real YAML and state-file adapters.
 */
class LoadKnowledgeTest {

  /** Synthetic: shaped like PDF-extracted CV text, with wrapped lines and justified spacing. */
  private static final String SNAPSHOT =
      """
      [redacted-email]  ·  Germany
      Engineering Manager Oct 2022 – Sep 2026
      Tamara (Fintech) · Berlin, Germany
      • Built two card products from scratch: Developed the BNPL Card and Credit Card entirely
      from zero, with no initial team or requirements.
      • Team & people: Grew the  BNPL Card  team from 1  to  a  peak  of  13  engineers.
      Senior Software Engineer June 2017 – Aug 2017
      Pebg · Fintech · Dubai, UAE
      TECHNICAL SKILLS
      Infrastructure & Cloud: AWS · SRE Practices (SLO/
      SLI, Incident Management) · from high-
      level design
      """;

  private static final String SOURCES =
      """
      - id: cv
        kind: pdf
        snapshot: sources/cv/cv.txt
        collected: 2026-10-04
        copied: Synthetic test Snapshot
        redactions: ["Email replaced by [redacted-email]"]
      """;

  private static final String ENTRIES =
      """
      - id: cv.role.tamara
        type: role
        source: cv
        employer: Tamara
        title: Engineering Manager
        start: 2022-10
        end: 2026-09
        quote: "Engineering Manager Oct 2022 – Sep 2026 Tamara (Fintech) · Berlin, Germany"
      - id: cv.role.tamara.b1
        type: bullet
        parent: cv.role.tamara
        source: cv
        quote: "Built two card products from scratch: Developed the BNPL Card and Credit Card entirely from zero, with no initial team or requirements."
      - id: cv.role.tamara.b2
        type: bullet
        parent: cv.role.tamara
        source: cv
        quote: "Team & people: Grew the BNPL Card team from 1 to a peak of 13 engineers."
      - id: cv.role.pebg
        type: role
        source: cv
        employer: Pebg
        title: Senior Software Engineer
        start: 2017-06
        end: 2017-08
        quote: "Senior Software Engineer June 2017 – Aug 2017 Pebg · Fintech · Dubai, UAE"
      - id: cv.skills.infrastructure
        type: skill-group
        source: cv
        quote: "Infrastructure & Cloud: AWS · SRE Practices (SLO/SLI, Incident Management) · from high-level design"
      """;

  @TempDir Path knowledgeDir;
  @TempDir Path stateDir;

  @Test
  void shouldLoadEveryEntry_whenQuotesMatchTheSnapshotAcrossWrappedLines() {
    var loaded = load(ENTRIES);

    assertThat(loaded.getEntries())
        .extracting(entry -> entry.id().value())
        .containsExactly(
            "cv.role.tamara",
            "cv.role.tamara.b1",
            "cv.role.tamara.b2",
            "cv.role.pebg",
            "cv.skills.infrastructure");
    assertThat(loaded.getEntries().getFirst())
        .extracting(Entry::employer, Entry::title, Entry::start, Entry::end)
        .containsExactly("Tamara", "Engineering Manager", "2022-10", "2026-09");
  }

  @Test
  void shouldReportEveryIdAsAdded_whenThereIsNoPreviousRun() {
    var changes = load(ENTRIES).getChanges();

    assertThat(changes.added()).hasSize(5).contains("cv.role.tamara", "cv.skills.infrastructure");
    assertThat(changes.changed()).isEmpty();
    assertThat(changes.removed()).isEmpty();
  }

  @Test
  void shouldReportNoChanges_whenNothingChangedSinceThePreviousRun() {
    load(ENTRIES);

    var changes = load(ENTRIES).getChanges();

    assertThat(changes.isEmpty()).isTrue();
  }

  @Test
  void shouldReportAddedChangedAndRemovedIds_whenEntriesChangedSinceThePreviousRun() {
    load(ENTRIES);
    var edited =
        ENTRIES
            .replace("id: cv.role.tamara.b2", "id: cv.role.tamara.team")
            .replace(" · from high-level design\"", "\"");
    var withoutPebg =
        edited.substring(0, edited.indexOf("- id: cv.role.pebg"))
            + edited.substring(edited.indexOf("- id: cv.skills.infrastructure"));

    var changes = load(withoutPebg).getChanges();

    assertThat(changes.added()).containsExactly("cv.role.tamara.team");
    assertThat(changes.changed()).containsExactly("cv.skills.infrastructure");
    assertThat(changes.removed()).containsExactly("cv.role.pebg", "cv.role.tamara.b2");
  }

  @Test
  void shouldFailNamingTheEntry_whenItsQuoteIsNotWordForWordInTheSnapshot() {
    var problem =
        loadFailure(ENTRIES.replace("peak of 13 engineers", "peak of 50 engineers"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara.b2");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.QUOTE_NOT_IN_SNAPSHOT);
    assertThat(problem.getMessage()).startsWith("Entry cv.role.tamara.b2: ").contains("cv.txt");
  }

  @Test
  void shouldFailNamingTheEntry_whenItsQuoteStartsOrEndsInsideAWord() {
    var problem = loadFailure(ENTRIES.replace("quote: \"Team & people:", "quote: \"eam & people:"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara.b2");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.QUOTE_NOT_IN_SNAPSHOT);
  }

  @Test
  void shouldFailNamingTheEntry_whenItsTitleIsOnlyPartOfAWordInItsQuote() {
    var problem = loadFailure(ENTRIES.replace("title: Engineering Manager", "title: Manage"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.TYPED_FIELD_NOT_IN_QUOTE);
  }

  @Test
  void shouldFailNamingTheEntry_whenTheIdIsUsedTwice() {
    var problem = loadFailure(ENTRIES.replace("id: cv.role.tamara.b2", "id: cv.role.tamara.b1"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara.b1");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.DUPLICATE_ID);
  }

  @Test
  void shouldFailNamingTheEntry_whenItsParentDoesNotResolve() {
    var problem =
        loadFailure(
            ENTRIES.replace(
                "id: cv.role.tamara.b2\n  type: bullet\n  parent: cv.role.tamara",
                "id: cv.role.tamara.b2\n  type: bullet\n  parent: cv.role.tamra"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara.b2");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.UNRESOLVED_PARENT);
    assertThat(problem.getMessage()).contains("cv.role.tamra");
  }

  @Test
  void shouldFailNamingTheEntry_whenItsParentIsNotARole() {
    var problem =
        loadFailure(
            ENTRIES.replace(
                "id: cv.role.tamara.b2\n  type: bullet\n  parent: cv.role.tamara",
                "id: cv.role.tamara.b2\n  type: bullet\n  parent: cv.role.tamara.b1"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara.b2");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.UNRESOLVED_PARENT);
  }

  @Test
  void shouldFailNamingTheEntry_whenItsTypeIsNotInTheFixedList() {
    var problem = loadFailure(ENTRIES.replace("type: skill-group", "type: skills"));

    assertThat(problem.target()).isEqualTo("cv.skills.infrastructure");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.INVALID_SCHEMA);
    assertThat(problem.getMessage()).contains("skills");
  }

  @Test
  void shouldFailNamingTheEntry_whenItHasAFieldTheSchemaDoesNotKnow() {
    var problem = loadFailure(ENTRIES.replace("employer: Pebg", "emplyer: Pebg"));

    assertThat(problem.target()).isEqualTo("cv.role.pebg");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.INVALID_SCHEMA);
    assertThat(problem.getMessage()).contains("emplyer");
  }

  @Test
  void shouldFailNamingTheEntry_whenABulletHasNoParent() {
    var problem = loadFailure(ENTRIES.replace("  parent: cv.role.tamara\n", ""));

    assertThat(problem.target()).isEqualTo("cv.role.tamara.b1");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.INVALID_SCHEMA);
  }

  @Test
  void shouldFailNamingTheEntry_whenADateIsNotYearAndMonth() {
    var problem = loadFailure(ENTRIES.replace("start: 2022-10", "start: Oct 2022"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.INVALID_SCHEMA);
  }

  @Test
  void shouldFailNamingThePosition_whenAnEntryHasNoId() {
    var problem = loadFailure(ENTRIES.replace("- id: cv.role.pebg\n  type: role", "- type: role"));

    assertThat(problem.target()).isEqualTo("cv.yaml entry 4");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.INVALID_SCHEMA);
  }

  @Test
  void shouldFailNamingTheEntry_whenItsSourceIsNotDeclared() {
    var problem =
        loadFailure(
            ENTRIES.replace(
                "id: cv.skills.infrastructure\n  type: skill-group\n  source: cv",
                "id: linkedin.skills.infrastructure\n  type: skill-group\n  source: linkedin"));

    assertThat(problem.target()).isEqualTo("linkedin.skills.infrastructure");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.UNKNOWN_SOURCE);
  }

  @Test
  void shouldFailNamingTheEntry_whenItsEmployerIsNotInItsQuote() {
    var problem = loadFailure(ENTRIES.replace("employer: Tamara", "employer: Tabby"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.TYPED_FIELD_NOT_IN_QUOTE);
    assertThat(problem.getMessage()).contains("employer").contains("Tabby");
  }

  @Test
  void shouldFailNamingTheEntry_whenItsTitleIsNotInItsQuote() {
    var problem =
        loadFailure(ENTRIES.replace("title: Senior Software Engineer", "title: Staff Engineer"));

    assertThat(problem.target()).isEqualTo("cv.role.pebg");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.TYPED_FIELD_NOT_IN_QUOTE);
    assertThat(problem.getMessage()).contains("title");
  }

  @Test
  void shouldFailNamingTheEntry_whenItsStartIsNotInItsQuote() {
    var problem = loadFailure(ENTRIES.replace("start: 2022-10", "start: 2021-10"));

    assertThat(problem.target()).isEqualTo("cv.role.tamara");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.TYPED_FIELD_NOT_IN_QUOTE);
    assertThat(problem.getMessage()).contains("start").contains("2021-10");
  }

  @Test
  void shouldFailNamingTheEntry_whenItsEndIsNotInItsQuote() {
    var problem = loadFailure(ENTRIES.replace("end: 2017-08", "end: 2017-09"));

    assertThat(problem.target()).isEqualTo("cv.role.pebg");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.TYPED_FIELD_NOT_IN_QUOTE);
    assertThat(problem.getMessage()).contains("end");
  }

  @Test
  void shouldFailNamingTheSource_whenItsSnapshotStillHoldsAnEmailAddress() {
    var problem =
        loadFailure(ENTRIES, SNAPSHOT.replace("[redacted-email]", "someone@example.com"));

    assertThat(problem.target()).isEqualTo("cv");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.UNREDACTED_EMAIL);
    assertThat(problem.getMessage()).startsWith("Source cv: ").doesNotContain("someone@");
  }

  @Test
  void shouldKeepThePreviousRunsIds_whenALoadFails() {
    load(ENTRIES);
    loadFailure(ENTRIES.replace("peak of 13", "peak of 50"));

    var changes = load(ENTRIES).getChanges();

    assertThat(changes.isEmpty()).isTrue();
  }

  @Test
  void shouldReportTheSameChangesAgain_whenStartupFailedAfterTheLoad() {
    load(ENTRIES);
    var edited = ENTRIES.replace("id: cv.role.tamara.b2", "id: cv.role.tamara.team");
    loadWithoutRecording(edited, SOURCES);

    var changes = load(edited).getChanges();

    assertThat(changes.added()).containsExactly("cv.role.tamara.team");
    assertThat(changes.removed()).containsExactly("cv.role.tamara.b2");
  }

  @Test
  void shouldFailNamingTheSource_whenItsIdIsDeclaredTwice() {
    var problem =
        catchThrowableOfType(
            InvalidKnowledgeException.class,
            () -> loadWithoutRecording(ENTRIES, SOURCES + SOURCES));

    assertThat(problem.target()).isEqualTo("cv");
    assertThat(problem.problem()).isEqualTo(KnowledgeProblem.DUPLICATE_ID);
    assertThat(problem.getMessage()).startsWith("Source cv: ");
  }

  /** A successful startup: load, then record this run as the previous one. */
  private LoadedKnowledge load(String entries) {
    return load(entries, SNAPSHOT);
  }

  private LoadedKnowledge load(String entries, String snapshot) {
    var service = service(entries, snapshot, SOURCES);
    var loaded = service.load();
    service.recordAsPreviousRun(loaded);
    return loaded;
  }

  /** Startup failed after the load (a missing prompt file, say), so nothing is recorded. */
  private LoadedKnowledge loadWithoutRecording(String entries, String sources) {
    return service(entries, SNAPSHOT, sources).load();
  }

  private LoadKnowledgeService service(String entries, String snapshot, String sources) {
    write("sources.yaml", sources);
    write("sources/cv/cv.txt", snapshot);
    write("entries/cv.yaml", entries);
    return new LoadKnowledgeService(
        new YamlKnowledgeBasePersistenceAdapter(knowledgeDir),
        new JsonKnowledgeStateAdapter(stateDir));
  }

  private InvalidKnowledgeException loadFailure(String entries) {
    return loadFailure(entries, SNAPSHOT);
  }

  private InvalidKnowledgeException loadFailure(String entries, String snapshot) {
    return catchThrowableOfType(
        InvalidKnowledgeException.class, () -> load(entries, snapshot));
  }

  private void write(String relativePath, String content) {
    var file = knowledgeDir.resolve(relativePath);
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
