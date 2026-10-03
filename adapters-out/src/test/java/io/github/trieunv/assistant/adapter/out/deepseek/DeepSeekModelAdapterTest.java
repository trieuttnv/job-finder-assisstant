package io.github.trieunv.assistant.adapter.out.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.exception.ModelOutputUnusableException;
import io.github.trieunv.assistant.domain.answer.AnswerPrompt;
import io.github.trieunv.assistant.domain.answer.Claim;
import io.github.trieunv.assistant.domain.answer.SystemPrompt;
import io.github.trieunv.assistant.domain.knowledge.EntryId;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The real DeepSeek adapter against a local stub of the chat-completions API: the request we send
 * (JSON mode, model id set explicitly, temperature 0, capped output) and how each reply is read.
 */
class DeepSeekModelAdapterTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final AnswerPrompt PROMPT =
      new AnswerPrompt(
          new SystemPrompt("answer-test", "Reply with JSON only."), "<question>Q</question>");
  private static final String CLAIMS =
      "{\"claims\":[{\"text\":\"I built two card products.\","
          + "\"entryIds\":[\"cv.role.tamara.b1\"]}],\"notInSources\":false}";

  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicInteger requests = new AtomicInteger();
  private HttpServer server;
  private int status = 200;
  private String responseBody;
  private Duration delay = Duration.ZERO;

  @BeforeEach
  void startStubApi() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          requests.incrementAndGet();
          sleep(delay);
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          var body = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopStubApi() {
    server.stop(0);
  }

  @Test
  void shouldSendOneJsonModeCallAtTemperatureZero_whenGenerating() {
    replyWithContent(CLAIMS);

    adapter().generate(PROMPT);

    JsonNode request = JSON.readTree(requestBody.get());
    assertThat(request.get("model").asString()).isEqualTo("deepseek-flash");
    assertThat(request.get("temperature").asDouble()).isZero();
    assertThat(request.get("max_tokens").asInt()).isEqualTo(800);
    assertThat(request.get("response_format").get("type").asString()).isEqualTo("json_object");
    assertThat(request.get("stream").asBoolean()).isFalse();
    assertThat(request.get("messages").get(0).get("role").asString()).isEqualTo("system");
    assertThat(request.get("messages").get(0).get("content").asString())
        .isEqualTo("Reply with JSON only.");
    assertThat(request.get("messages").get(1).get("content").asString())
        .isEqualTo("<question>Q</question>");
    assertThat(authorization.get()).isEqualTo("Bearer test-key");
    assertThat(requests).hasValue(1);
  }

  @Test
  void shouldReturnClaimsRawOutputAndTokens_whenTheModelRepliesWithClaimsJson() {
    replyWithContent(CLAIMS);

    var reply = adapter().generate(PROMPT);

    assertThat(reply.getAnswer().claims())
        .containsExactly(
            new Claim("I built two card products.", List.of(EntryId.of("cv.role.tamara.b1"))));
    assertThat(reply.getAnswer().notInSources()).isFalse();
    assertThat(reply.getRawOutput()).isEqualTo(CLAIMS);
    assertThat(reply.getProvider()).isEqualTo("deepseek");
    assertThat(reply.getModelId()).isEqualTo("deepseek-flash");
    assertThat(reply.getInputTokens()).isEqualTo(410);
    assertThat(reply.getOutputTokens()).isEqualTo(52);
  }

  @Test
  void shouldFailAsUnusableAndKeepTheRawOutput_whenTheContentIsEmpty() {
    replyWithContent("");

    assertThatThrownBy(() -> adapter().generate(PROMPT))
        .isInstanceOf(ModelOutputUnusableException.class)
        .extracting(e -> ((ModelOutputUnusableException) e).getRawOutput())
        .isEqualTo("");
  }

  @Test
  void shouldNameTheTruncationAndKeepTheTokens_whenTheReplyHitTheOutputCap() {
    replyWithContent("{\"claims\":[{\"text\":\"I built", "length");

    assertThatThrownBy(() -> adapter().generate(PROMPT))
        .isInstanceOf(ModelOutputUnusableException.class)
        .hasMessageContaining("finish_reason: length")
        .satisfies(
            e -> {
              var unusable = (ModelOutputUnusableException) e;
              assertThat(unusable.getInputTokens()).isEqualTo(410);
              assertThat(unusable.getOutputTokens()).isEqualTo(52);
            });
  }

  @Test
  void shouldFailAsUnusable_whenTheJsonHasNoClaimsList() {
    replyWithContent("{}");

    assertErrorCode(ErrorCode.MODEL_OUTPUT_UNUSABLE);
  }

  @Test
  void shouldFailAsUnusable_whenTheContentIsNotJson() {
    replyWithContent("Sure! I built card products at Tamara.");

    assertErrorCode(ErrorCode.MODEL_OUTPUT_UNUSABLE);
  }

  @Test
  void shouldFailAsUnavailable_whenTheProviderReturnsAnError() {
    status = 503;
    responseBody = "{\"error\":{\"message\":\"Server busy\"}}";

    assertErrorCode(ErrorCode.MODEL_UNAVAILABLE);
  }

  @Test
  void shouldFailAsTimeoutAfterOneRequest_whenTheProviderIsSlowerThanTheTimeout() {
    replyWithContent(CLAIMS);
    delay = Duration.ofSeconds(2);

    assertThatThrownBy(() -> adapter("test-key", Duration.ofMillis(300)).generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(ErrorCode.MODEL_TIMEOUT);
    assertThat(requests).hasValue(1);
  }

  @Test
  void shouldFailAsNotConfiguredWithoutCallingTheProvider_whenTheApiKeyIsBlank() {
    replyWithContent(CLAIMS);

    assertThatThrownBy(() -> adapter("", Duration.ofSeconds(5)).generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(ErrorCode.MODEL_NOT_CONFIGURED);
    assertThat(requests).hasValue(0);
  }

  private void assertErrorCode(ErrorCode expected) {
    assertThatThrownBy(() -> adapter().generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(expected);
  }

  private DeepSeekModelAdapter adapter() {
    return adapter("test-key", Duration.ofSeconds(5));
  }

  private DeepSeekModelAdapter adapter(String apiKey, Duration timeout) {
    var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    return new DeepSeekModelAdapter(apiKey, baseUrl, "deepseek-flash", timeout, 800);
  }

  private void replyWithContent(String content) {
    replyWithContent(content, "stop");
  }

  private void replyWithContent(String content, String finishReason) {
    status = 200;
    responseBody =
        JSON.writeValueAsString(
            JSON.createObjectNode()
                .put("id", "chatcmpl-test")
                .put("model", "deepseek-flash")
                .<ObjectNode>set(
                    "choices",
                    JSON.createArrayNode()
                        .add(
                            JSON.createObjectNode()
                                .put("index", 0)
                                .put("finish_reason", finishReason)
                                .set(
                                    "message",
                                    JSON.createObjectNode()
                                        .put("role", "assistant")
                                        .put("content", content))))
                .set(
                    "usage",
                    JSON.createObjectNode()
                        .put("prompt_tokens", 410)
                        .put("completion_tokens", 52)));
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
