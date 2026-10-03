package io.github.trieunv.assistant.adapter.out.state;

import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.port.out.KnowledgeStatePort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Keeps the previous run's Entry fingerprints in {@code <stateDir>/knowledge-ids.json}. */
public class JsonKnowledgeStateAdapter implements KnowledgeStatePort {

  private static final TypeReference<Map<String, String>> FINGERPRINTS = new TypeReference<>() {};

  private final Path file;
  private final JsonMapper json = JsonMapper.builder().build();

  public JsonKnowledgeStateAdapter(Path stateDir) {
    this.file = stateDir.resolve("knowledge-ids.json");
  }

  @Override
  public Map<String, String> previousFingerprints() {
    if (!Files.exists(file)) {
      return Map.of();
    }
    try {
      return json.readValue(file.toFile(), FINGERPRINTS);
    } catch (JacksonException e) {
      throw failed("Cannot read the previous run's Entry ids from " + file, e);
    }
  }

  @Override
  public void save(Map<String, String> fingerprints) {
    try {
      Files.createDirectories(file.getParent());
      var sorted = new TreeMap<>(fingerprints);
      Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(sorted));
    } catch (IOException | JacksonException e) {
      throw failed("Cannot save this run's Entry ids to " + file, e);
    }
  }

  private static AssistantApplicationException failed(String message, Exception cause) {
    return new AssistantApplicationException(ErrorCode.KNOWLEDGE_STATE_FAILED, message, cause);
  }
}
