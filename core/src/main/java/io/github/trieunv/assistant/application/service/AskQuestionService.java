package io.github.trieunv.assistant.application.service;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AnsweredClaim;
import io.github.trieunv.assistant.application.dto.AskConfig;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.AskTrace;
import io.github.trieunv.assistant.application.dto.AskTrace.ClaimTrace;
import io.github.trieunv.assistant.application.dto.AskTrace.RetrievedTrace;
import io.github.trieunv.assistant.application.dto.ConflictNotice;
import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ModelOutputUnusableException;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.application.port.out.RetrieverPort;
import io.github.trieunv.assistant.application.port.out.TraceSinkPort;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.AnswerPrompt;
import io.github.trieunv.assistant.domain.answer.CitationNumbering;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ConflictNotices;
import io.github.trieunv.assistant.domain.answer.OutputGuardrail;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import io.github.trieunv.assistant.domain.answer.TraceId;
import io.github.trieunv.assistant.domain.knowledge.Conflict;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.RetrievedEntry;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;

/**
 * The fixed answering pipeline (ADR 0003): retrieve, Input Guardrail, one model call, Output
 * Guardrail, number the citations, append one trace line. No model decides a step, and every
 * failure ends in a fallback mode whose message is written by code.
 */
@RequiredArgsConstructor
public class AskQuestionService implements AskQuestionUseCase {

  private static final System.Logger LOG = System.getLogger(AskQuestionService.class.getName());

  private final RetrieverPort retriever;
  private final ModelPort model;
  private final TraceSinkPort traceSink;
  private final SystemPrompt systemPrompt;
  private final AskConfig config;
  private final Clock clock;
  private final OutputGuardrail outputGuardrail;
  private final ConflictNotices conflictNotices;

  @Override
  public AnswerResult ask(AskQuestionCommand command) {
    var traceId = TraceId.random();
    var startedAt = System.nanoTime();
    var trace =
        AskTrace.builder()
            .traceId(traceId.value())
            .timestamp(clock.instant())
            .question(command.getQuestion())
            .promptVersion(systemPrompt.version())
            .promptHash(systemPrompt.hash())
            .config(config);
    AnswerResult result;
    try {
      result = answer(command.getQuestion(), traceId, trace, startedAt);
    } catch (RuntimeException e) {
      trace.failure(describe(e));
      result = fallback(AnswerMode.ERROR, traceId);
    }
    appendTrace(trace.mode(result.getMode()).totalLatencyMs(millisSince(startedAt)).build());
    return result;
  }

  private AnswerResult answer(
      String question, TraceId traceId, AskTrace.AskTraceBuilder trace, long startedAt) {
    var retrieved = retriever.retrieve(question);
    var retrievedAt = System.nanoTime();
    trace
        .retrieved(retrieved.stream().map(AskQuestionService::retrievedTrace).toList())
        .retrievalLatencyMs(millisBetween(startedAt, retrievedAt));

    // Input Guardrail: nothing relevant enough, so nothing reaches the model or gets invented.
    if (retrieved.stream()
        .filter(hit -> !hit.expanded())
        .noneMatch(hit -> hit.score() > config.getRetrievalMinScore())) {
      return fallback(AnswerMode.NOT_IN_SOURCES, traceId);
    }

    Map<EntryId, Entry> retrievedById =
        retrieved.stream()
            .map(RetrievedEntry::entry)
            .collect(
                Collectors.toMap(
                    Entry::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));
    var prompt = AnswerPrompt.of(systemPrompt, question, List.copyOf(retrievedById.values()));
    ModelReply reply;
    try {
      reply = model.generate(prompt);
    } catch (AssistantApplicationException e) {
      if (e instanceof ModelOutputUnusableException unusable) {
        trace.rawOutput(unusable.getRawOutput());
      }
      trace.failure(describe(e)).modelLatencyMs(millisSince(retrievedAt));
      return fallback(AnswerMode.UNAVAILABLE, traceId);
    }
    trace
        .provider(reply.getProvider())
        .modelId(reply.getModelId())
        .rawOutput(reply.getRawOutput())
        .notInSources(reply.getAnswer().notInSources())
        .inputTokens(reply.getInputTokens())
        .outputTokens(reply.getOutputTokens())
        .modelLatencyMs(millisSince(retrievedAt));

    // A negative answer is never generated: the flag becomes the fixed message, claims unshown.
    if (reply.getAnswer().notInSources()) {
      return fallback(AnswerMode.NOT_IN_SOURCES, traceId);
    }

    var verdict = outputGuardrail.check(reply.getAnswer().claims(), retrievedById);
    trace
        .keptClaims(verdict.kept().stream().map(claim -> claimTrace(claim, null)).toList())
        .droppedClaims(
            verdict.dropped().stream()
                .map(dropped -> claimTrace(dropped.claim(), dropped.reason().name()))
                .toList());
    if (verdict.withheld()) {
      return fallback(AnswerMode.UNVERIFIED, traceId);
    }

    // Conflicts are appended by code, both sides cited, whatever the claims say (README Task 2,
    // Step 4). Claims are numbered first, then each side, into one Sources block.
    var kept = verdict.kept();
    var conflicts = conflictNotices.relevantTo(question, kept);
    var numbering = new CitationNumbering();
    var claims =
        kept.stream()
            .map(
                claim ->
                    AnsweredClaim.builder()
                        .text(claim.text())
                        .citations(numbering.numberAll(claim.entryIds()))
                        .build())
            .toList();
    var notices =
        conflicts.stream().map(conflict -> ConflictNotice.of(conflict, numbering)).toList();
    trace.conflictIds(conflicts.stream().map(Conflict::id).toList());
    return AnswerResult.builder()
        .mode(AnswerMode.ANSWERED)
        .claims(claims)
        .conflicts(notices)
        .sources(sourceReferences(numbering.citedInOrder(), retrievedById))
        .removedClaims(verdict.dropped().size())
        .traceId(traceId)
        .build();
  }

  private static AnswerResult fallback(AnswerMode mode, TraceId traceId) {
    return AnswerResult.builder()
        .mode(mode)
        .claims(List.of())
        .conflicts(List.of())
        .sources(List.of())
        .removedClaims(0)
        .traceId(traceId)
        .build();
  }

  /**
   * A trace that can't be written must not cost the Interviewer a verified answer, nor show them a
   * stack trace; the failure is logged locally instead.
   */
  private void appendTrace(AskTrace trace) {
    try {
      traceSink.append(trace);
    } catch (RuntimeException e) {
      LOG.log(System.Logger.Level.WARNING, "Cannot append trace " + trace.getTraceId(), e);
    }
  }

  /** For the local trace only; never shown to the Interviewer. */
  private static String describe(RuntimeException e) {
    var kind =
        e instanceof AssistantApplicationException application
            ? application.getErrorCode().name()
            : e.getClass().getName();
    return kind + ": " + e.getMessage();
  }

  /**
   * Sources block lines, numbered as {@code cited} orders them. A Conflict side that wasn't
   * retrieved is still cited, from the knowledge base.
   */
  private List<SourceReference> sourceReferences(
      List<EntryId> cited, Map<EntryId, Entry> retrievedById) {
    return IntStream.range(0, cited.size())
        .mapToObj(
            index -> {
              var id = cited.get(index);
              var entry =
                  Optional.ofNullable(retrievedById.get(id))
                      .or(() -> conflictNotices.entry(id))
                      .orElseThrow();
              return SourceReference.builder()
                  .number(index + 1)
                  .entryId(entry.id().value())
                  .source(entry.source())
                  .quote(entry.quote())
                  .build();
            })
        .toList();
  }

  private static RetrievedTrace retrievedTrace(RetrievedEntry hit) {
    return RetrievedTrace.builder()
        .entryId(hit.entry().id().value())
        .score(hit.score())
        .expanded(hit.expanded())
        .build();
  }

  private static ClaimTrace claimTrace(Claim claim, String dropReason) {
    return ClaimTrace.builder()
        .text(claim.text())
        .entryIds(claim.entryIds().stream().map(EntryId::value).toList())
        .dropReason(dropReason)
        .build();
  }

  private static long millisBetween(long startNanos, long endNanos) {
    return (endNanos - startNanos) / 1_000_000;
  }

  private static long millisSince(long startNanos) {
    return millisBetween(startNanos, System.nanoTime());
  }
}
