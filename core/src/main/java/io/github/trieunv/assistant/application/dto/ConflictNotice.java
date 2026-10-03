package io.github.trieunv.assistant.application.dto;

import io.github.trieunv.assistant.domain.answer.CitationNumbering;
import io.github.trieunv.assistant.domain.knowledge.Conflict;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A Conflict shown with an answer: each side's text with citation numbers into the answer's
 * Sources, and an optional closing note. The wording comes from the reviewed Conflict record,
 * never the model.
 */
@Builder
@Getter
@AllArgsConstructor
public class ConflictNotice {

  private final String id;
  private final List<Side> sides;
  private final String note;

  /** Numbers each side's Entries with the answer's numbering, after the claims' citations. */
  public static ConflictNotice of(Conflict conflict, CitationNumbering numbering) {
    return ConflictNotice.builder()
        .id(conflict.id())
        .sides(
            conflict.sides().stream()
                .map(
                    side ->
                        Side.builder()
                            .text(side.text())
                            .citations(numbering.numberAll(side.entryIds()))
                            .build())
                .toList())
        .note(conflict.note())
        .build();
  }

  @Builder
  @Getter
  @AllArgsConstructor
  public static class Side {

    private final String text;
    private final List<Integer> citations;
  }
}
