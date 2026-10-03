package io.github.trieunv.assistant.adapter.out.yaml;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One Entry as written in a {@code knowledge/entries/*.yaml} file. */
@Getter
@Setter
@NoArgsConstructor
class EntryYaml {

  private String id;
  private String type;
  private String source;
  private String parent;
  private String employer;
  private String title;
  private String start;
  private String end;
  private String quote;
}
