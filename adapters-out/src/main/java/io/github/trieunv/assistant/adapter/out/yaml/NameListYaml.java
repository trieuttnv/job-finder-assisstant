package io.github.trieunv.assistant.adapter.out.yaml;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code knowledge/names.yaml}: technology names, and each canonical name's other spellings. */
@Getter
@Setter
@NoArgsConstructor
class NameListYaml {

  private List<String> technologies = List.of();
  private Map<String, List<String>> aliases = Map.of();
}
