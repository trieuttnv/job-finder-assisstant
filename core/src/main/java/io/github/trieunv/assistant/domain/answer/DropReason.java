package io.github.trieunv.assistant.domain.answer;

public enum DropReason {
  CITES_NOTHING,
  CITES_ENTRY_NOT_RETRIEVED,
  /** A number, year or month that none of the cited Entries contain. */
  NUMBER_NOT_IN_SOURCES,
  /** An employer, product or technology that none of the cited Entries contain. */
  NAME_NOT_IN_SOURCES
}
