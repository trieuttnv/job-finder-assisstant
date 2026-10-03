package io.github.trieunv.assistant.domain.exception;

/** A domain rule was broken, such as an Entry without an id, type, Source or Quote. */
public class AssistantDomainException extends RuntimeException {

  public AssistantDomainException(String message) {
    super(message);
  }
}
