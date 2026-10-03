package io.github.trieunv.assistant.domain.answer;

import io.github.trieunv.assistant.domain.knowledge.Entry;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The one prompt sent to the model for a question: the versioned system prompt, and a user message
 * that carries the question and the retrieved Entries as delimited data, never as instructions.
 */
public record AnswerPrompt(SystemPrompt systemPrompt, String userMessage) {

  public static AnswerPrompt of(SystemPrompt systemPrompt, String question, List<Entry> entries) {
    var entryLines = entries.stream().map(AnswerPrompt::describe).collect(Collectors.joining("\n"));
    var userMessage =
        """
        <question>
        %s
        </question>

        <entries>
        %s
        </entries>
        """
            .formatted(question, entryLines);
    return new AnswerPrompt(systemPrompt, userMessage);
  }

  /**
   * {@code [id] quote}, then the typed fields that are set, so the model sees employer and dates.
   */
  private static String describe(Entry entry) {
    var fields =
        Stream.of(
                field("type", entry.type().code()),
                field("source", entry.source()),
                field("parent", Objects.toString(entry.parent(), null)),
                field("employer", entry.employer()),
                field("title", entry.title()),
                field("start", entry.start()),
                field("end", entry.end()))
            .filter(Objects::nonNull)
            .collect(Collectors.joining("; "));
    return "[%s] %s\n    (%s)".formatted(entry.id(), entry.quote(), fields);
  }

  private static String field(String name, String value) {
    return Objects.isNull(value) ? null : name + ": " + value;
  }
}
