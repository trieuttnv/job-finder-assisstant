package io.github.trieunv.assistant.adapter.out.yaml;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One Source's provenance record in {@code knowledge/sources.yaml}. */
@Getter
@Setter
@NoArgsConstructor
class SourceYaml {

  private String id;
  private String kind;

  /** Path of the Snapshot text, relative to the knowledge folder. */
  private String snapshot;

  private String collected;

  /** Commit of the copied repository; empty for a document. */
  private String commit;

  private String original;
  private String copied;
  private List<String> redactions;
}
