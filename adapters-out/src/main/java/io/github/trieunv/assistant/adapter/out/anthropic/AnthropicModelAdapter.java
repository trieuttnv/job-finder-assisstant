package io.github.trieunv.assistant.adapter.out.anthropic;

import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.exception.ModelOutputUnusableException;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.domain.answer.AnswerPrompt;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.util.StringUtils;

/**
 * Claude through Spring AI, with the claims schema enforced by Anthropic structured output. One
 * call, temperature 0, capped output, a per-call timeout and no retry (ADR 0005). A blank API key
 * is detected here, at startup, and every call then fails as not configured without a request.
 */
@Slf4j
public class AnthropicModelAdapter implements ModelPort {

  private static final String PROVIDER = "anthropic";

  private final String modelId;
  private final boolean configured;
  private final AnthropicChatModel chatModel;
  private final BeanOutputConverter<ModelAnswerJson> converter =
      new BeanOutputConverter<>(ModelAnswerJson.class);

  /**
   * @param baseUrl the Messages API endpoint; blank means Anthropic's default. Set only to point at
   *     a local mock in tests.
   */
  public AnthropicModelAdapter(
      String apiKey, String baseUrl, String modelId, Duration timeout, int maxOutputTokens) {
    this.modelId = modelId;
    this.configured = StringUtils.hasText(apiKey);
    if (!configured) {
      log.warn("ANTHROPIC_API_KEY is not set: every question will get the unavailable message.");
    }
    var options =
        AnthropicChatOptions.builder()
            .apiKey(apiKey)
            .model(modelId)
            .temperature(0.0)
            .maxTokens(maxOutputTokens)
            .timeout(timeout)
            .maxRetries(0)
            .outputSchema(converter.getJsonSchema());
    if (StringUtils.hasText(baseUrl)) {
      options.baseUrl(baseUrl);
    }
    this.chatModel = AnthropicChatModel.builder().options(options.build()).build();
  }

  boolean isConfigured() {
    return configured;
  }

  @Override
  public ModelReply generate(AnswerPrompt prompt) {
    if (!configured) {
      throw new AssistantApplicationException(
          ErrorCode.MODEL_NOT_CONFIGURED, "ANTHROPIC_API_KEY is not set", null);
    }
    var request =
        new Prompt(
            List.of(
                new SystemMessage(prompt.systemPrompt().text()),
                new UserMessage(prompt.userMessage())));
    String rawOutput;
    Usage usage;
    try {
      var response = chatModel.call(request);
      rawOutput = response.getResult().getOutput().getText();
      usage = response.getMetadata().getUsage();
    } catch (RuntimeException e) {
      throw isTimeout(e)
          ? new AssistantApplicationException(
              ErrorCode.MODEL_TIMEOUT, "Anthropic call exceeded its timeout", e)
          : new AssistantApplicationException(
              ErrorCode.MODEL_UNAVAILABLE, "Anthropic call failed", e);
    }
    var inputTokens = Optional.ofNullable(usage).map(Usage::getPromptTokens).orElse(0);
    var outputTokens = Optional.ofNullable(usage).map(Usage::getCompletionTokens).orElse(0);
    return ModelReply.builder()
        .provider(PROVIDER)
        .modelId(modelId)
        .rawOutput(rawOutput)
        .answer(parse(rawOutput, inputTokens, outputTokens))
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .build();
  }

  /**
   * The SDK reports a timeout as an I/O failure wrapping an {@link InterruptedIOException} (which
   * {@code SocketTimeoutException} extends) or a {@link TimeoutException}, at some depth.
   */
  private static boolean isTimeout(Throwable failure) {
    for (var cause = failure; Objects.nonNull(cause); cause = cause.getCause()) {
      if (cause instanceof InterruptedIOException || cause instanceof TimeoutException) {
        return true;
      }
    }
    return false;
  }

  /**
   * Any output that is empty, malformed, or has a claim without text or with a blank id is
   * unusable.
   */
  private ModelAnswer parse(String rawOutput, int inputTokens, int outputTokens) {
    try {
      if (!StringUtils.hasText(rawOutput)) {
        throw new IllegalArgumentException("empty model output");
      }
      var document = converter.convert(rawOutput);
      var claims =
          Objects.requireNonNull(document.getClaims(), "no claims list").stream()
              .map(
                  claim ->
                      new Claim(
                          Objects.requireNonNull(claim.getText(), "claim without text"),
                          Optional.ofNullable(claim.getEntryIds()).orElse(List.of()).stream()
                              .map(EntryId::of)
                              .toList()))
              .toList();
      return new ModelAnswer(claims, document.isNotInSources());
    } catch (RuntimeException e) {
      throw new ModelOutputUnusableException(
          "Model output does not fit the claims schema", rawOutput, inputTokens, outputTokens, e);
    }
  }
}
