package io.github.trieunv.assistant.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The names a question can be narrowed by come from the Entries, never from code. */
class RetrievalVocabularyTest {

  private static final Entry VNG =
      new Entry(
          EntryId.of("cv.role.vng"),
          EntryType.ROLE,
          "cv",
          null,
          "VNG Corporation, 123Pay",
          "Technical Lead",
          "2011-02",
          "2014-08",
          "Technical Lead Feb 2011 – Aug 2014 VNG Corporation, 123Pay");
  private static final Entry SKILLS =
      new Entry(
          EntryId.of("cv.skills.languages"),
          EntryType.SKILL_GROUP,
          "cv",
          null,
          null,
          null,
          null,
          null,
          "Languages & Frameworks: Java · Spring Boot");

  private final RetrievalVocabulary vocabulary =
      RetrievalVocabulary.from(
          List.of(VNG, SKILLS),
          new NameList(List.of("Java", "Kafka"), Map.of("Kafka", List.of("Apache Kafka"))));

  @Test
  void shouldFindAnEmployer_whenTheQuestionNamesAnyPartOfItInAnyCase() {
    assertThat(vocabulary.scopeOf("What did you do at 123pay?").employers())
        .containsExactly("VNG Corporation, 123Pay");
    assertThat(vocabulary.scopeOf("Tell me about VNG").employers())
        .containsExactly("VNG Corporation, 123Pay");
  }

  @Test
  void shouldFindOnlyTechnologiesSomeEntryMentions_whenTheQuestionNamesThem() {
    var scope = vocabulary.scopeOf("Do you know Java and Kafka?");

    assertThat(scope.technologies()).containsExactly("Java");
  }

  @Test
  void shouldFindAnEntryType_whenTheQuestionNamesItInSingularOrPlural() {
    assertThat(vocabulary.scopeOf("What are your skills?").types())
        .containsExactly(EntryType.SKILL_GROUP);
    assertThat(vocabulary.scopeOf("Tell me about one role").types())
        .containsExactly(EntryType.ROLE);
    assertThat(vocabulary.scopeOf("Any summaries?").types()).containsExactly(EntryType.SUMMARY);
  }

  @Test
  void shouldFindAnEntryType_whenTheQuestionUsesAnInterviewersWordForIt() {
    assertThat(vocabulary.scopeOf("Which university did you attend?").types())
        .containsExactly(EntryType.EDUCATION);
    assertThat(vocabulary.scopeOf("What positions have you held?").types())
        .containsExactly(EntryType.ROLE);
    assertThat(vocabulary.scopeOf("Name one achievement").types())
        .containsExactly(EntryType.BULLET);
    assertThat(vocabulary.scopeOf("Tell me about your qualifications").typeOnly()).isTrue();
  }

  @Test
  void shouldNotReadAWorkProjectAsALearningPrototype_whenTheQuestionSaysProject() {
    assertThat(vocabulary.scopeOf("Tell me about a project you led").types()).isEmpty();
    assertThat(vocabulary.scopeOf("Tell me about your prototypes").types())
        .containsExactly(EntryType.PROJECT);
  }

  @Test
  void shouldFindATechnologyOnlyAsWritten_whenTheQuestionUsesTheWordOtherwise() {
    assertThat(vocabulary.scopeOf("Do you know java?").technologies()).isEmpty();
  }

  @Test
  void shouldSayATypeStandsAlone_whenTheQuestionOnlyAsksForThatType() {
    assertThat(vocabulary.scopeOf("Tell me about your skills").typeOnly()).isTrue();
    assertThat(vocabulary.scopeOf("What skills does Messi have?").typeOnly()).isFalse();
  }

  @Test
  void shouldFindNothing_whenTheQuestionNamesNoKnownName() {
    var scope = vocabulary.scopeOf("What is your favourite football club?");

    assertThat(scope.employers()).isEmpty();
    assertThat(scope.technologies()).isEmpty();
    assertThat(scope.types()).isEmpty();
  }
}
