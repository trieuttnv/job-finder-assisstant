package io.github.trieunv.assistant.adapter.out.yaml;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One Conflict as written in {@code knowledge/conflicts.yaml}. */
@Getter
@Setter
@NoArgsConstructor
class ConflictYaml {

  private String id;
  private String topic;
  private List<String> terms = List.of();
  private List<SideYaml> sides = List.of();
  private String note;
  private String rule;

  @Getter
  @Setter
  @NoArgsConstructor
  static class SideYaml {

    private String text;
    private List<String> entryIds = List.of();
  }
}
