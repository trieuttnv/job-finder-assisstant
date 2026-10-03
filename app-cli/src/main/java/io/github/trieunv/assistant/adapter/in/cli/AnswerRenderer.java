package io.github.trieunv.assistant.adapter.in.cli;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AnsweredClaim;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
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
    text.append(
        answer.getClaims().stream()
            .map(AnswerRenderer::withCitations)
            .collect(Collectors.joining(" ")));
    text.append('\n');
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

  /**
   * Puts the citation before a closing full stop: "I built X [1]." rather than "I built X. [1]".
   */
  private static String withCitations(AnsweredClaim claim) {
    var citations =
        claim.getCitations().stream().map(n -> "[" + n + "]").collect(Collectors.joining());
    var text = claim.getText().strip();
    return text.endsWith(".")
        ? text.substring(0, text.length() - 1) + " " + citations + "."
        : text + " " + citations;
  }
}
