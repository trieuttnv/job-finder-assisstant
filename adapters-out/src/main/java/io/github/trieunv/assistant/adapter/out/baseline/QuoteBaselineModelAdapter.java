package io.github.trieunv.assistant.adapter.out.baseline;

import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.domain.answer.AnswerPrompt;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The no-LLM baseline (ADR 0003): no model is called, and the answer is the retrieved Quotes
 * themselves, each citing its own Entry. Run through the same pipeline and evaluation, it shows
 * what generation adds (fluent, selective answers) and what it risks (anything not in a Quote).
 */
public class QuoteBaselineModelAdapter implements ModelPort {

  public static final String PROVIDER = "baseline";
  public static final String MODEL_ID = "none";

  @Override
  public ModelReply generate(AnswerPrompt prompt) {
    var claims =
        prompt.entries().stream()
            .map(entry -> new Claim(entry.quote(), List.of(entry.id())))
            .toList();
    return ModelReply.builder()
        .provider(PROVIDER)
        .modelId(MODEL_ID)
        .rawOutput(
            prompt.entries().stream()
                .map(entry -> "[" + entry.id() + "] " + entry.quote())
                .collect(Collectors.joining("\n")))
        .answer(new ModelAnswer(claims, claims.isEmpty()))
        .inputTokens(0)
        .outputTokens(0)
        .build();
  }
}
