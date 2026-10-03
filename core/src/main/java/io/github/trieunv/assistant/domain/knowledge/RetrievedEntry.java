package io.github.trieunv.assistant.domain.knowledge;

/**
 * An Entry retrieved for one question, with its retrieval score (BM25, higher is better). An
 * {@code expanded} Entry was not matched itself but came with one that was, such as a bullet's
 * role.
 */
public record RetrievedEntry(Entry entry, double score, boolean expanded) {

  public static RetrievedEntry matched(Entry entry, double score) {
    return new RetrievedEntry(entry, score, false);
  }

  public static RetrievedEntry expandedFrom(Entry entry, RetrievedEntry match) {
    return new RetrievedEntry(entry, match.score(), true);
  }
}
