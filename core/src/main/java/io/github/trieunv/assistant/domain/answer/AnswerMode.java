package io.github.trieunv.assistant.domain.answer;

import java.util.Optional;

/**
 * What kind of reply the Interviewer gets. Every mode but {@code ANSWERED} is a fallback: a fixed
 * message written by code, never by the model, with no claims and no Sources.
 */
public enum AnswerMode {
  ANSWERED(null),
  NOT_IN_SOURCES("That isn't in my sources."),
  UNVERIFIED("I couldn't put together a verified answer."),
  UNAVAILABLE("The assistant can't answer right now. Please try again."),
  /** Anything unexpected; shown with the trace id so it can be reported. */
  ERROR("Something went wrong. Please try again.");

  private final String fallbackMessage;

  AnswerMode(String fallbackMessage) {
    this.fallbackMessage = fallbackMessage;
  }

  /** The fixed message for a fallback; empty for {@code ANSWERED}. */
  public Optional<String> fallbackMessage() {
    return Optional.ofNullable(fallbackMessage);
  }
}
