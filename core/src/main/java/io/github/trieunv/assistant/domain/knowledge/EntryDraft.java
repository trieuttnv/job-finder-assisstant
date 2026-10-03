package io.github.trieunv.assistant.domain.knowledge;

import java.util.Set;

/**
 * One Entry exactly as written in an Entry file, before any check. Every field may be missing;
 * {@code unknownFields} holds field names the schema does not have, and {@code position} (such as
 * {@code cv.yaml entry 4}) names it when the id is missing.
 */
public record EntryDraft(
    String position,
    String id,
    String type,
    String source,
    String parent,
    String employer,
    String title,
    String start,
    String end,
    String quote,
    Set<String> unknownFields) {}
