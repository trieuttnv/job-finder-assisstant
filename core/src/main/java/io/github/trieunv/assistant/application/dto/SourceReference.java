package io.github.trieunv.assistant.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** One numbered line of the Sources block: the cited Entry, its Source and its exact Quote. */
@Builder
@Getter
@AllArgsConstructor
public class SourceReference {

  private final int number;
  private final String entryId;
  private final String source;
  private final String quote;
}
