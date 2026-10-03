package io.github.trieunv.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.application.dto.AskConfig;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seam 1, retrieval: which Entries reach the model for a question. Filters and boosts come from
 * names found in the Entries, and every retrieved bullet brings its role.
 */
class AskRetrievalTest {

  private static final Pattern ENTRY_ID = Pattern.compile("(?m)^\\[([^\\]]+)]");

  @TempDir Path traceDir;
  @TempDir Path stateDir;

  private final AtomicReference<String> promptSeen = new AtomicReference<>();

  @Test
  void shouldRetrieveOnlyThatEmployersEntries_whenTheQuestionNamesAnEmployer() {
    var ids =
        entriesSentToTheModel(AskFixture.CONFIG, "How big was the engineering team at Tamara?");

    assertThat(ids)
        .contains("fixture.role.tamara.b2", "fixture.role.tamara")
        .noneMatch(id -> id.startsWith("fixture.role.alipay"));
  }

  @Test
  void shouldBringEachRetrievedBulletsRole_whenTheRoleItselfDidNotMatch() throws IOException {
    var ids = entriesSentToTheModel(AskFixture.CONFIG, "How many banks did you integrate?");

    assertThat(ids).contains("fixture.role.alipay.b1", "fixture.role.alipay");
    var expanded =
        retrievedTrace().valueStream()
            .filter(hit -> hit.get("entryId").asString().equals("fixture.role.alipay"))
            .findFirst()
            .orElseThrow();
    assertThat(expanded.get("expanded").asBoolean()).isTrue();
  }

  @Test
  void shouldRankTheSkillGroupFirst_whenTheQuestionNamesATechnologyAndTheSkillType() {
    var ids = entriesSentToTheModel(AskFixture.CONFIG, "Which skills do you have with Java?");

    assertThat(ids.getFirst()).isEqualTo("fixture.skills.languages");
  }

  @Test
  void shouldRetrieveEntriesOfANamedType_whenNoneOfTheQuestionsOtherWordsMatch() {
    var ids = entriesSentToTheModel(AskFixture.CONFIG, "Tell me about your skills");

    assertThat(ids).containsExactly("fixture.skills.languages");
  }

  @Test
  void shouldNotCallTheModel_whenOnlyATypeWordMatchesAnUnrelatedQuestion() {
    var answer =
        AskFixture.service(capturing(), AskFixture.CONFIG, traceDir, stateDir)
            .ask(AskQuestionCommand.builder().question("What skills does Messi have?").build());

    assertThat(answer.getMode()).isEqualTo(AnswerMode.NOT_IN_SOURCES);
    assertThat(promptSeen.get()).isNull();
  }

  @Test
  void shouldRankOnlyMatchingEntriesOfANamedType_whenTheQuestionAlsoHasOtherWords() {
    var ids = entriesSentToTheModel(AskFixture.CONFIG, "Which role involved banks?");

    assertThat(ids).contains("fixture.role.alipay.b1").doesNotContain("fixture.role.tamara");
  }

  @Test
  void shouldKeepAnEntryWithoutAnEmployerThatNamesIt_whenFilteringByThatEmployer() {
    var ids = entriesSentToTheModel(AskFixture.CONFIG, "Which teams did you build at Alipay?");

    assertThat(ids)
        .contains("fixture.summary.teams")
        .noneMatch(id -> id.startsWith("fixture.role.tamara"));
  }

  @Test
  void shouldRetrieveAtMostTopKMatchesPlusTheirRoles_whenTopKIsOne() throws IOException {
    var topOne =
        AskConfig.builder()
            .provider("stub")
            .modelId("stub-model")
            .retrievalTopK(1)
            .retrievalMinScore(AskFixture.CONFIG.getRetrievalMinScore())
            .guardMaxDropRatio(AskFixture.CONFIG.getGuardMaxDropRatio())
            .promptVersion("answer-test")
            .build();

    entriesSentToTheModel(topOne, "Which card products and payment gateway did you build?");

    var hits = retrievedTrace().valueStream().toList();
    assertThat(hits.stream().filter(hit -> !hit.get("expanded").asBoolean())).hasSize(1);
    assertThat(hits).hasSizeLessThanOrEqualTo(2);
  }

  private ModelPort capturing() {
    return prompt -> {
      promptSeen.set(prompt.userMessage());
      return ModelReply.builder()
          .provider("stub")
          .modelId("stub-model")
          .rawOutput("{}")
          .answer(new ModelAnswer(List.of(), true))
          .build();
    };
  }

  private List<String> entriesSentToTheModel(AskConfig config, String question) {
    AskFixture.service(capturing(), config, traceDir, stateDir)
        .ask(AskQuestionCommand.builder().question(question).build());
    return ENTRY_ID.matcher(promptSeen.get()).results().map(match -> match.group(1)).toList();
  }

  private JsonNode retrievedTrace() throws IOException {
    try (var files = Files.list(traceDir)) {
      var line = Files.readAllLines(files.toList().getFirst()).getLast();
      return JsonMapper.builder().build().readTree(line).get("retrieved");
    }
  }
}
