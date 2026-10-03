package io.github.trieunv.assistant.adapter.out.deepseek;

import io.github.trieunv.assistant.application.dto.ModelReply;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.exception.ModelOutputUnusableException;
import io.github.trieunv.assistant.application.port.out.ModelPort;
import io.github.trieunv.assistant.domain.answer.AnswerPrompt;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.ModelAnswer;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * DeepSeek's chat-completions API in JSON mode, the open-weights side of the provider experiment
 * (ADR 0005). One call, temperature 0, capped output, the model id always sent explicitly, a
 * per-call timeout and no retry. JSON mode only guarantees JSON, not the claims shape, and can
 * return empty content, so both are checked here; the prompt already says "JSON", which JSON mode
 * requires. It uses the JDK's HTTP client directly: Spring AI's DeepSeek module would add WebFlux
 * and a retry template to switch off.
 */
@Slf4j
public class DeepSeekModelAdapter implements ModelPort {

  private static final String PROVIDER = "deepseek";
  private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

  private final String apiKey;
  private final URI endpoint;
  private final String modelId;
  private final Duration timeout;
  private final int maxOutputTokens;
  private final boolean configured;
  private final HttpClient http;
  private final JsonMapper json = JsonMapper.builder().build();

  /**
   * @param baseUrl the API root; blank means DeepSeek's own. Set only to point at a local stub.
   */
  public DeepSeekModelAdapter(
      String apiKey, String baseUrl, String modelId, Duration timeout, int maxOutputTokens) {
    this.apiKey = apiKey;
    var root = StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
    this.endpoint = URI.create(root.replaceAll("/+$", "") + "/chat/completions");
    this.modelId = modelId;
    this.timeout = timeout;
    this.maxOutputTokens = maxOutputTokens;
    this.configured = StringUtils.hasText(apiKey);
    if (!configured) {
      log.warn("DEEPSEEK_API_KEY is not set: every question will get the unavailable message.");
    }
    this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  @Override
  public ModelReply generate(AnswerPrompt prompt) {
    if (!configured) {
      throw new AssistantApplicationException(
          ErrorCode.MODEL_NOT_CONFIGURED, "DEEPSEEK_API_KEY is not set", null);
    }
    var response = send(requestBody(prompt));
    if (response.statusCode() / 100 != 2) {
      throw new AssistantApplicationException(
          ErrorCode.MODEL_UNAVAILABLE, "DeepSeek returned HTTP " + response.statusCode(), null);
    }
    JsonNode body;
    try {
      body = json.readTree(response.body());
    } catch (JacksonException e) {
      // The API envelope itself is broken, not the model's answer inside it.
      throw new AssistantApplicationException(
          ErrorCode.MODEL_UNAVAILABLE, "DeepSeek returned a body that is not JSON", e);
    }
    var choice = body.path("choices").path(0);
    var rawOutput =
        Optional.ofNullable(choice.path("message").get("content"))
            .filter(JsonNode::isString)
            .map(JsonNode::asString)
            .orElse(null);
    var usage = body.path("usage");
    var inputTokens = usage.path("prompt_tokens").asInt(0);
    var outputTokens = usage.path("completion_tokens").asInt(0);
    ModelAnswer answer;
    try {
      answer = parse(rawOutput);
    } catch (RuntimeException e) {
      // "length" means the reply hit max_tokens mid-JSON, which JSON mode doesn't prevent.
      var finishReason = choice.path("finish_reason").asString("unknown");
      throw new ModelOutputUnusableException(
          "Model output does not fit the claims schema (finish_reason: " + finishReason + ")",
          rawOutput,
          inputTokens,
          outputTokens,
          e);
    }
    return ModelReply.builder()
        .provider(PROVIDER)
        .modelId(modelId)
        .rawOutput(rawOutput)
        .answer(answer)
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)
        .build();
  }

  private String requestBody(AnswerPrompt prompt) {
    var request = json.createObjectNode();
    request.put("model", modelId);
    request.put("temperature", 0.0);
    request.put("max_tokens", maxOutputTokens);
    request.put("stream", false);
    request.putObject("response_format").put("type", "json_object");
    var messages = request.putArray("messages");
    messages.addObject().put("role", "system").put("content", prompt.systemPrompt().text());
    messages.addObject().put("role", "user").put("content", prompt.userMessage());
    return json.writeValueAsString(request);
  }

  private HttpResponse<String> send(String body) {
    var request =
        HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new AssistantApplicationException(
          ErrorCode.MODEL_TIMEOUT, "DeepSeek call exceeded its timeout", e);
    } catch (IOException e) {
      throw new AssistantApplicationException(
          ErrorCode.MODEL_UNAVAILABLE, "DeepSeek call failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssistantApplicationException(
          ErrorCode.MODEL_UNAVAILABLE, "DeepSeek call interrupted", e);
    }
  }

  /** Empty content, non-JSON, a missing claims list or a claim without text is unusable. */
  private ModelAnswer parse(String rawOutput) {
    if (!StringUtils.hasText(rawOutput)) {
      throw new IllegalArgumentException("empty model output");
    }
    var document = json.readTree(rawOutput);
    var claimsNode = document.get("claims");
    if (Objects.isNull(claimsNode) || !claimsNode.isArray()) {
      throw new IllegalArgumentException("no claims list");
    }
    var claims =
        claimsNode.valueStream()
            .map(
                claim -> {
                  var text = claim.get("text");
                  if (Objects.isNull(text) || !text.isString()) {
                    throw new IllegalArgumentException("claim without text");
                  }
                  var ids =
                      claim.path("entryIds").valueStream()
                          .map(id -> EntryId.of(id.asString()))
                          .toList();
                  return new Claim(text.asString(), ids);
                })
            .toList();
    return new ModelAnswer(claims, document.path("notInSources").asBoolean(false));
  }
}
