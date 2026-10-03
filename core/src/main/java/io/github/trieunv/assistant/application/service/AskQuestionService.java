package io.github.trieunv.assistant.application.service;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AnsweredClaim;
import io.github.trieunv.assistant.application.dto.AskQuestionCommand;
import io.github.trieunv.assistant.application.dto.AskTrace;
import io.github.trieunv.assistant.application.dto.AskTrace.ClaimTrace;
import io.github.trieunv.assistant.application.dto.AskTrace.RetrievedTrace;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.application.port.out.RetrieverPort;
import io.github.trieunv.assistant.application.port.out.TraceSinkPort;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.AnswerPrompt;
import io.github.trieunv.assistant.domain.answer.CitationNumbering;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.OutputGuardrail;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import io.github.trieunv.assistant.domain.answer.TraceId;
import io.github.trieunv.assistant.domain.knowledge.Entry;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import io.github.trieunv.assistant.domain.knowledge.RetrievedEntry;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;

/**
 * The fixed answering pipeline (ADR 0003): retrieve, one model call, Output Guardrail, number the
 * citations, append one trace line. No model decides a step.
 */
@RequiredArgsConstructor
public class AskQuestionService implements AskQuestionUseCase {

  private final RetrieverPort retriever;
  private final ModelPort model;
  private final TraceSinkPort traceSink;
  private final SystemPrompt systemPrompt;
  private final Clock clock;
  private final OutputGuardrail outputGuardrail = new OutputGuardrail();

  @Override
  public AnswerResult ask(AskQuestionCommand command) {
    var traceId = TraceId.random();
    var question = command.getQuestion();
    var startedAt = System.nanoTime();

    var retrieved = retriever.retrieve(question);
    var retrievedAt = System.nanoTime();
    Map<EntryId, Entry> retrievedById =
        retrieved.stream()
            .map(RetrievedEntry::entry)
            .collect(
                Collectors.toMap(
                    Entry::id, Function.identity(), (first, second) -> first, LinkedHashMap::new));

    var prompt = AnswerPrompt.of(systemPrompt, question, List.copyOf(retrievedById.values()));
    var reply = model.generate(prompt);
    var answeredAt = System.nanoTime();

    var verdict = outputGuardrail.check(reply.getAnswer().claims(), retrievedById.keySet());
    var numbering = new CitationNumbering();
    var claims =
        verdict.kept().stream()
            .map(
                claim ->
                    AnsweredClaim.builder()
                        .text(claim.text())
                        .citations(numbering.numberAll(claim.entryIds()))
                        .build())
            .toList();
    var sources = sourceReferences(numbering.citedInOrder(), retrievedById);

    traceSink.append(
        AskTrace.builder()
            .traceId(traceId.value())
            .timestamp(clock.instant())
            .question(question)
            .retrieved(retrieved.stream().map(AskQuestionService::retrievedTrace).toList())
            .promptVersion(systemPrompt.version())
            .promptHash(systemPrompt.hash())
            .provider(reply.getProvider())
            .modelId(reply.getModelId())
            .rawOutput(reply.getRawOutput())
            .notInSources(reply.getAnswer().notInSources())
            .keptClaims(verdict.kept().stream().map(claim -> claimTrace(claim, null)).toList())
            .droppedClaims(
                verdict.dropped().stream()
                    .map(dropped -> claimTrace(dropped.claim(), dropped.reason().name()))
                    .toList())
            .inputTokens(reply.getInputTokens())
            .outputTokens(reply.getOutputTokens())
            .retrievalLatencyMs(millisBetween(startedAt, retrievedAt))
            .modelLatencyMs(millisBetween(retrievedAt, answeredAt))
            .totalLatencyMs(millisBetween(startedAt, System.nanoTime()))
            .build());

    return AnswerResult.builder()
        .mode(AnswerMode.ANSWERED)
        .claims(claims)
        .sources(sources)
        .removedClaims(verdict.dropped().size())
        .traceId(traceId)
        .build();
  }

  /** Sources block lines, numbered as {@code cited} orders them. */
  private static List<SourceReference> sourceReferences(
      List<EntryId> cited, Map<EntryId, Entry> retrievedById) {
    return IntStream.range(0, cited.size())
        .mapToObj(
            index -> {
              var entry = retrievedById.get(cited.get(index));
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
    return RetrievedTrace.builder().entryId(hit.entry().id().value()).score(hit.score()).build();
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
}
