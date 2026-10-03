package io.github.trieunv.assistant.application.exception;

import lombok.Getter;

@Getter
public class AssistantApplicationException extends RuntimeException {

  private final ErrorCode errorCode;

  public AssistantApplicationException(ErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }
}
