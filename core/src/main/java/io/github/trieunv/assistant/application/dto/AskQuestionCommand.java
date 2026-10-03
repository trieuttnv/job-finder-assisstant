package io.github.trieunv.assistant.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class AskQuestionCommand {

  private final String question;
}
