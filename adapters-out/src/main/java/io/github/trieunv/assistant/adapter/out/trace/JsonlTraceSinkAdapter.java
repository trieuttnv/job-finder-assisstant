package io.github.trieunv.assistant.adapter.out.trace;

import io.github.trieunv.assistant.application.dto.AskTrace;
import io.github.trieunv.assistant.application.exception.AssistantApplicationException;
import io.github.trieunv.assistant.application.exception.ErrorCode;
import io.github.trieunv.assistant.application.port.out.TraceSinkPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import tools.jackson.databind.json.JsonMapper;

/** Appends one JSON line per question to {@code <traceDir>/<UTC date>.jsonl}. */
public class JsonlTraceSinkAdapter implements TraceSinkPort {

  private final Path traceDir;
  private final JsonMapper json = JsonMapper.builder().build();

  public JsonlTraceSinkAdapter(Path traceDir) {
    this.traceDir = traceDir;
  }

  @Override
  public synchronized void append(AskTrace trace) {
    var file =
        traceDir.resolve(trace.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate() + ".jsonl");
    try {
      Files.createDirectories(traceDir);
      Files.writeString(
          file,
          json.writeValueAsString(trace) + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new AssistantApplicationException(
          ErrorCode.TRACE_WRITE_FAILED, "Cannot append trace to " + file, e);
    }
  }
}
