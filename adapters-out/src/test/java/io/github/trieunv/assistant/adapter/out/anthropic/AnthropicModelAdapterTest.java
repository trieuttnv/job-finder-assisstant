package io.github.trieunv.assistant.adapter.out.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
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

/**
 * The real adapter against a local mock of the Anthropic Messages API: checks the request we send
 * and how we read the reply, with no network and no API key.
 */
class AnthropicModelAdapterTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final AnswerPrompt PROMPT =
      new AnswerPrompt(
          new SystemPrompt("answer-test", "Answer from Entries."),
          "<question>Q</question>",
          List.of());

  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicInteger requests = new AtomicInteger();
  private HttpServer server;
  private int status;
  private String responseBody;
  private Duration delay = Duration.ZERO;

  @BeforeEach
  void startMockApi() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          requests.incrementAndGet();
          sleep(delay);
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
  void stopMockApi() {
    server.stop(0);
  }

  @Test
  void shouldSendOneCallAtTemperatureZeroWithCappedOutputAndClaimsSchema_whenGenerating() {
    replyWithText(
        "{\"claims\":[{\"text\":\"I built two card"
            + " products.\",\"entryIds\":[\"cv.role.tamara.b1\"]}],\"notInSources\":false}");

    adapter().generate(PROMPT);

    JsonNode request = JSON.readTree(requestBody.get());
    assertThat(request.get("model").asString()).isEqualTo("claude-sonnet-5-5");
    assertThat(request.get("temperature").asDouble()).isZero();
    assertThat(request.get("max_tokens").asInt()).isEqualTo(800);
    assertThat(request.toString()).contains("Answer from Entries.");
    assertThat(request.get("messages").get(0).toString()).contains("<question>Q</question>");
    var schema = request.get("output_config").get("format").get("schema");
    assertThat(schema.get("required").valueStream().map(JsonNode::asString))
        .containsExactlyInAnyOrder("claims", "notInSources");
  }

  @Test
  void shouldReturnClaimsRawOutputAndTokens_whenModelRepliesWithClaimsJson() {
    var raw =
        "{\"claims\":[{\"text\":\"I built two card"
            + " products.\",\"entryIds\":[\"cv.role.tamara.b1\"]}],\"notInSources\":false}";
    replyWithText(raw);

    var reply = adapter().generate(PROMPT);

    assertThat(reply.getAnswer().claims())
        .containsExactly(
            new Claim("I built two card products.", List.of(EntryId.of("cv.role.tamara.b1"))));
    assertThat(reply.getAnswer().notInSources()).isFalse();
    assertThat(reply.getRawOutput()).isEqualTo(raw);
    assertThat(reply.getProvider()).isEqualTo("anthropic");
    assertThat(reply.getModelId()).isEqualTo("claude-sonnet-5-5");
    assertThat(reply.getInputTokens()).isEqualTo(321);
    assertThat(reply.getOutputTokens()).isEqualTo(87);
  }

  @Test
  void shouldFailAsUnusableOutput_whenReplyDoesNotFitTheClaimsSchema() {
    replyWithText("Sure! Here is my answer about Tamara.");

    assertThatThrownBy(() -> adapter().generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(ErrorCode.MODEL_OUTPUT_UNUSABLE);
  }

  @Test
  void shouldFailAsUnavailable_whenProviderReturnsAnError() {
    status = 500;
    responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}";

    assertThatThrownBy(() -> adapter().generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(ErrorCode.MODEL_UNAVAILABLE);
  }

  @Test
  void shouldFailAsTimeoutAfterOneRequest_whenTheProviderIsSlowerThanTheTimeout() {
    replyWithText("{\"claims\":[],\"notInSources\":true}");
    delay = Duration.ofSeconds(2);

    assertThatThrownBy(() -> adapter("test-key", Duration.ofMillis(300)).generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(ErrorCode.MODEL_TIMEOUT);
    assertThat(requests).hasValue(1);
  }

  @Test
  void shouldFailAsUnusableOutput_whenTheReplyHasNoContent() {
    replyWithText("");

    assertThatThrownBy(() -> adapter().generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(ErrorCode.MODEL_OUTPUT_UNUSABLE);
  }

  @Test
  void shouldFailAsNotConfiguredWithoutCallingTheProvider_whenTheApiKeyIsBlank() {
    replyWithText("{\"claims\":[],\"notInSources\":true}");
    var adapter = adapter(" ", Duration.ofSeconds(5));

    assertThat(adapter.isConfigured()).isFalse();
    assertThatThrownBy(() -> adapter.generate(PROMPT))
        .isInstanceOf(AssistantApplicationException.class)
        .extracting(e -> ((AssistantApplicationException) e).getErrorCode())
        .isEqualTo(ErrorCode.MODEL_NOT_CONFIGURED);
    assertThat(requests).hasValue(0);
  }

  private AnthropicModelAdapter adapter() {
    return adapter("test-key", Duration.ofSeconds(5));
  }

  private AnthropicModelAdapter adapter(String apiKey, Duration timeout) {
    return new AnthropicModelAdapter(
        apiKey,
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "claude-sonnet-5-5",
        timeout,
        800);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void replyWithText(String text) {
    status = 200;
    responseBody =
        JSON.writeValueAsString(
            JSON.createObjectNode()
                .put("id", "msg_test")
                .put("type", "message")
                .put("role", "assistant")
                .put("model", "claude-sonnet-5-5")
                .put("stop_reason", "end_turn")
                .putNull("stop_sequence")
                .<tools.jackson.databind.node.ObjectNode>set(
                    "content",
                    JSON.createArrayNode()
                        .add(JSON.createObjectNode().put("type", "text").put("text", text)))
                .set(
                    "usage",
                    JSON.createObjectNode().put("input_tokens", 321).put("output_tokens", 87)));
  }
}
