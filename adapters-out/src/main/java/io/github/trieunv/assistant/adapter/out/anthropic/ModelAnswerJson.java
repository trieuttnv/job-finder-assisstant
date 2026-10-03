package io.github.trieunv.assistant.adapter.out.anthropic;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The model's JSON output contract: {@code {claims: [{text, entryIds[]}], notInSources}}. */
@Getter
@Setter
@NoArgsConstructor
class ModelAnswerJson {

  private List<ClaimJson> claims;
  private boolean notInSources;

  @Getter
  @Setter
  @NoArgsConstructor
  static class ClaimJson {

    private String text;
    private List<String> entryIds;
  }
}
