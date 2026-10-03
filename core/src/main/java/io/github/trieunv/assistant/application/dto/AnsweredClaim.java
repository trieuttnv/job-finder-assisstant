package io.github.trieunv.assistant.application.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class AnsweredClaim {

  private final String text;
  private final List<Integer> citations;
}
