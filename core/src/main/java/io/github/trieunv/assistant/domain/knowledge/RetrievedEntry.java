package io.github.trieunv.assistant.domain.knowledge;

/** An Entry retrieved for one question, with its retrieval score (BM25, higher is better). */
public record RetrievedEntry(Entry entry, double score) {}
