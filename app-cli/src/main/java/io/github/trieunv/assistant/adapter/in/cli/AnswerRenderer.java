package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AnsweredClaim;
import io.github.trieunv.assistant.application.dto.ConflictNotice;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Renders an answer as the Interviewer sees it: each claim ends with its citation numbers, and the
 * numbered Sources block under it shows each cited Entry id, Source and exact Quote. A fallback is
 * its fixed message alone, so it can't be mistaken for an answer.
 */
public class AnswerRenderer {

  public String render(AnswerResult answer) {
    var fallback = answer.getMode().fallbackMessage();
    if (fallback.isPresent()) {
      var traceNote =
          answer.getMode() == AnswerMode.ERROR ? " (trace id: " + answer.getTraceId() + ")" : "";
      return fallback.get() + traceNote + "\n";
    }
    var text = new StringBuilder();
    if (!answer.getClaims().isEmpty()) {
      text.append(
          answer.getClaims().stream()
              .map(AnswerRenderer::withCitations)
              .collect(Collectors.joining(" ")));
      text.append('\n');
    }
    for (var notice : answer.getConflicts()) {
      text.append(notice(notice)).append('\n');
    }
    if (answer.getRemovedClaims() > 0) {
      var plural = answer.getRemovedClaims() == 1 ? "statement" : "statements";
      text.append(
          "%d %s removed: not supported by my sources.%n"
              .formatted(answer.getRemovedClaims(), plural));
    }
    text.append("\nSources\n");
    var idWidth =
        answer.getSources().stream()
            .mapToInt(source -> source.getEntryId().length())
            .max()
            .orElse(0);
    var sourceWidth =
        answer.getSources().stream()
            .mapToInt(source -> source.getSource().length())
            .max()
            .orElse(0);
    var line = " [%d] %-" + idWidth + "s  %-" + sourceWidth + "s  \"%s\"%n";
    for (SourceReference source : answer.getSources()) {
      text.append(
          line.formatted(
              source.getNumber(), source.getEntryId(), source.getSource(), source.getQuote()));
    }
    return text.toString();
  }

  /** "⚠ My sources differ: side one [1]; side two [2]. Note." */
  private static String notice(ConflictNotice notice) {
    var sides =
        notice.getSides().stream()
            .map(side -> side.getText() + " " + citations(side.getCitations()))
            .collect(Collectors.joining("; "));
    var note = Optional.ofNullable(notice.getNote()).map(text -> " " + text).orElse("");
    return "⚠ My sources differ: " + sides + "." + note;
  }

  private static String citations(List<Integer> numbers) {
    return numbers.stream().map(n -> "[" + n + "]").collect(Collectors.joining());
  }

  /**
   * Puts the citation before a closing full stop: "I built X [1]." rather than "I built X. [1]".
   */
  private static String withCitations(AnsweredClaim claim) {
    var citations = citations(claim.getCitations());
    var text = claim.getText().strip();
    return text.endsWith(".")
        ? text.substring(0, text.length() - 1) + " " + citations + "."
        : text + " " + citations;
  }
}
