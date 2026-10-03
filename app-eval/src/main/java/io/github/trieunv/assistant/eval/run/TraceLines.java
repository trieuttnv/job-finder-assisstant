package io.github.trieunv.assistant.eval.run;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The trace lines a run wrote, by trace id: the evaluation reads its numbers from them. */
public class TraceLines {

  private final Path traceDir;
  private final JsonMapper json = JsonMapper.builder().build();

  public TraceLines(Path traceDir) {
    this.traceDir = traceDir;
  }

  public Map<String, JsonNode> byTraceId() {
    var lines = new HashMap<String, JsonNode>();
    if (!Files.isDirectory(traceDir)) {
      return lines;
    }
    try (var files = Files.list(traceDir)) {
      for (var file : files.filter(path -> path.toString().endsWith(".jsonl")).toList()) {
        for (var line : Files.readAllLines(file)) {
          if (!line.isBlank()) {
            var trace = json.readTree(line);
            Optional.ofNullable(trace.get("traceId"))
                .ifPresent(id -> lines.put(id.asString(), trace));
          }
        }
      }
      return lines;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read traces in " + traceDir, e);
    }
  }
}
