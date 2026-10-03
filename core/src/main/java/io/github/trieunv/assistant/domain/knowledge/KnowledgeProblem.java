package io.github.trieunv.assistant.domain.knowledge;

/** Why startup refused the knowledge base. */
public enum KnowledgeProblem {
  INVALID_SCHEMA,
  DUPLICATE_ID,
  UNKNOWN_SOURCE,
  UNRESOLVED_PARENT,
  QUOTE_NOT_IN_SNAPSHOT,
  TYPED_FIELD_NOT_IN_QUOTE,
  UNREDACTED_EMAIL
}
