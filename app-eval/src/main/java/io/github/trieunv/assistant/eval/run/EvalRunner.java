package io.github.trieunv.assistant.eval.run;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.eval.EvalItem;
import io.github.trieunv.assistant.eval.QuestionResult;
import io.github.trieunv.assistant.eval.QuestionResult.DroppedClaim;
import io.github.trieunv.assistant.eval.QuestionResult.RetrievedHit;
import io.github.trieunv.assistant.eval.QuestionResult.ShownClaim;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * Runs every item through the same Ask use case as the CLI, once per repetition, then joins each
 * answer with its trace line. A missing trace line stops the run: scoring without it would
 * silently zero recall, latency and the drop rate.
 */
@RequiredArgsConstructor
public class EvalRunner {

  private final AskQuestionUseCase askQuestion;
  private final TraceLines traces;

  /** The results, and the settings the traces say the answers were produced under. */
  public record Run(List<QuestionResult> results, JsonNode config, String promptHash) {}

  public Run run(List<EvalItem> items, int repetitions) {
    var asked = new ArrayList<Asked>();
    for (var rep = 0; rep < repetitions; rep++) {
      for (var item : items) {
        var answer =
            askQuestion.ask(AskQuestionCommand.builder().question(item.question()).build());
        asked.add(new Asked(item, rep, answer));
      }
    }
    var traceById = traces.byTraceId();
    var results = new ArrayList<QuestionResult>();
    JsonNode lastTrace = null;
    for (var one : asked) {
      var traceId = one.answer().getTraceId().value();
      var trace = traceById.get(traceId);
      if (Objects.isNull(trace)) {
        throw new IllegalStateException(
            "No trace line for " + one.item().id() + " (trace " + traceId + "); is TRACE_DIR "
                + "the folder this run writes to?");
      }
      results.add(toResult(one, trace));
      lastTrace = trace;
    }
    return new Run(
        results,
        Objects.requireNonNull(lastTrace, "no questions were run").path("config"),
        lastTrace.path("promptHash").asString(""));
  }

  private static QuestionResult toResult(Asked asked, JsonNode trace) {
    var answer = asked.answer();
    var entryByNumber =
        answer.getSources().stream()
            .collect(Collectors.toMap(SourceReference::getNumber, SourceReference::getEntryId));
    var claims =
        answer.getClaims().stream()
            .map(
                claim ->
                    new ShownClaim(
                        claim.getText(),
                        claim.getCitations().stream().map(entryByNumber::get).toList()))
            .toList();
    var retrieved = new ArrayList<RetrievedHit>();
    trace
        .path("retrieved")
        .forEach(
            hit ->
                retrieved.add(
                    new RetrievedHit(
                        hit.path("entryId").asString(),
                        hit.path("score").asDouble(0),
                        hit.path("expanded").asBoolean(false))));
    var dropped = new ArrayList<DroppedClaim>();
    trace
        .path("droppedClaims")
        .forEach(
            claim ->
                dropped.add(
                    new DroppedClaim(
                        claim.path("text").asString(""),
                        claim.path("entryIds").valueStream().map(JsonNode::asString).toList(),
                        claim.path("dropReason").asString(""))));
    var cost = trace.get("costUsd");
    var rawOutput = trace.get("rawOutput");
    return new QuestionResult(
        asked.item().id(),
        asked.rep(),
        asked.item().question(),
        answer.getMode(),
        claims,
        retrieved,
        trace.path("keptClaims").size() + dropped.size(),
        dropped,
        Objects.nonNull(rawOutput) && rawOutput.isString() ? rawOutput.asString() : null,
        trace.path("inputTokens").asInt(0),
        trace.path("outputTokens").asInt(0),
        trace.path("totalLatencyMs").asLong(0),
        Objects.nonNull(cost) && cost.isNumber() ? cost.asDouble() : null,
        answer.getTraceId().value());
  }

  private record Asked(EvalItem item, int rep, AnswerResult answer) {}
}
