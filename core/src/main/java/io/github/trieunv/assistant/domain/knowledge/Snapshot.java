package io.github.trieunv.assistant.domain.knowledge;

/**
 * The committed text of one Source, which is all the assistant ever reads. {@code location} is
 * where it lives, for error messages.
 */
public record Snapshot(String sourceId, String location, String text) {}
