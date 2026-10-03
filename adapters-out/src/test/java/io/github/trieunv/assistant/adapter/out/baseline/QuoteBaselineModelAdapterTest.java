package io.github.trieunv.assistant.adapter.out.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.domain.answer.AnswerPrompt;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.EntryType;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteBaselineModelAdapterTest {

  private static final SystemPrompt PROMPT = new SystemPrompt("answer-test", "Answer.");

  @Test
  void shouldAnswerWithEachRetrievedQuoteCitingItsOwnEntry_whenEntriesWereRetrieved() {
    var role = entry("cv.role.atlassian", "Senior Software Engineer Sep 2014 – Jul 2015");
    var prompt = AnswerPrompt.of(PROMPT, "When?", List.of(role));

    var reply = new QuoteBaselineModelAdapter().generate(prompt);

    assertThat(reply.getAnswer().claims())
        .containsExactly(new Claim(role.quote(), List.of(role.id())));
    assertThat(reply.getAnswer().notInSources()).isFalse();
    assertThat(reply.getProvider()).isEqualTo("baseline");
    assertThat(reply.getInputTokens()).isZero();
  }

  @Test
  void shouldSayNotInSources_whenNothingWasRetrieved() {
    var reply = new QuoteBaselineModelAdapter().generate(AnswerPrompt.of(PROMPT, "Q?", List.of()));

    assertThat(reply.getAnswer().notInSources()).isTrue();
  }

  private static Entry entry(String id, String quote) {
    return new Entry(EntryId.of(id), EntryType.SUMMARY, "cv", null, null, null, null, null, quote);
  }
}
