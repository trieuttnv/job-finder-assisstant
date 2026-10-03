package io.github.trieunv.assistant.domain.knowledge;

import java.util.List;

/**
 * The committed text of one Source, which is all the assistant ever reads: one file, or every file
 * of a repository Snapshot. A Quote must be word for word within one of {@code texts}, never
 * across two. {@code location} is where it lives, for error messages.
 */
public record Snapshot(String sourceId, String location, List<String> texts) {

  public Snapshot {
    texts = List.copyOf(texts);
  }

  public static Snapshot ofText(String sourceId, String location, String text) {
    return new Snapshot(sourceId, location, List.of(text));
  }
}
