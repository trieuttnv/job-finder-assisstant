package io.github.trieunv.assistant.adapter.in.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * {@code ./ask} with no argument: a session that opens with five suggested questions, one per
 * evaluation category, so each behaviour (an answer, a Conflict, an ambiguous question, a fallback,
 * a leading question) is one keystroke away. A number asks that suggestion; anything else is asked
 * as written; "exit", "quit" or the end of input closes it.
 */
@RequiredArgsConstructor
public class InteractiveSession {

  /** The README's example for each evaluation category (Task 3, Step 1), labelled by category. */
  static final List<Suggestion> SUGGESTIONS =
      List.of(
          new Suggestion("When did you work at Atlassian?", "CV-direct"),
          new Suggestion("What languages do you write code in?", "multi-source"),
          new Suggestion("Tell me about your biggest project.", "ambiguous"),
          new Suggestion("What is your salary expectation?", "not answerable"),
          new Suggestion("You led 50 engineers at Tamara, right?", "adversarial"));

  private static final Set<String> EXIT = Set.of("exit", "quit");

  private final CliAnswers answers;

  public void run(BufferedReader in, PrintStream out) {
    out.println("Ask about my professional background. Type a number to try one of these:");
    for (var index = 0; index < SUGGESTIONS.size(); index++) {
      var suggestion = SUGGESTIONS.get(index);
      out.printf("  %d. %-42s (%s)%n", index + 1, suggestion.question(), suggestion.category());
    }
    out.println("Or type your own question. \"exit\" to leave.");
    while (true) {
      out.print("\n> ");
      out.flush();
      var line = readLine(in);
      if (Objects.isNull(line)) {
        out.println();
        return;
      }
      var typed = line.strip();
      if (EXIT.contains(typed.toLowerCase(Locale.ROOT))) {
        return;
      }
      if (typed.isEmpty()) {
        continue;
      }
      String question;
      if (isNumber(typed)) {
        var suggestion = suggestionNumbered(typed);
        if (suggestion.isEmpty()) {
          // A typo'd number is not worth a model call.
          out.printf("Pick 1 to %d, or type a question.%n", SUGGESTIONS.size());
          continue;
        }
        question = suggestion.get().question();
        out.println(question);
      } else {
        question = typed;
      }
      out.println();
      out.print(answers.answer(question));
    }
  }

  private static boolean isNumber(String typed) {
    return typed.chars().allMatch(Character::isDigit);
  }

  private static Optional<Suggestion> suggestionNumbered(String typed) {
    try {
      var index = Integer.parseInt(typed) - 1;
      return index >= 0 && index < SUGGESTIONS.size()
          ? Optional.of(SUGGESTIONS.get(index))
          : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static String readLine(BufferedReader in) {
    try {
      return in.readLine();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the next question", e);
    }
  }

  record Suggestion(String question, String category) {}
}
