package io.github.trieunv.assistant.domain.answer;

import java.util.Objects;
import java.util.UUID;

/** Identifies one question's trace line, and is shown to the Interviewer on unexpected failures. */
public record TraceId(String value) {

  public TraceId {
    Objects.requireNonNull(value, "Trace id cannot be null");
  }

  public static TraceId random() {
    return new TraceId(UUID.randomUUID().toString());
  }

  @Override
  public String toString() {
    return value;
  }
}
