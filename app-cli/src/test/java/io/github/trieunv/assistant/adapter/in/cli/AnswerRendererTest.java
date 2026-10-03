package io.github.trieunv.assistant.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.trieunv.assistant.application.dto.AnswerResult;
import io.github.trieunv.assistant.application.dto.AnsweredClaim;
import io.github.trieunv.assistant.application.dto.SourceReference;
import io.github.trieunv.assistant.domain.answer.AnswerMode;
import io.github.trieunv.assistant.domain.answer.TraceId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerRendererTest {

  private final AnswerRenderer renderer = new AnswerRenderer();

  @Test
  void shouldEndEachClaimWithItsCitationsAndListAlignedSources_whenAnswered() {
    var rendered =
        renderer.render(
            answer(
                0,
                List.of(
                    new AnsweredClaim("I built two card products.", List.of(1)),
                    new AnsweredClaim("I grew the team to 13 engineers", List.of(2, 3))),
                List.of(
                    new SourceReference(1, "cv.role.tamara.b1", "cv", "Built two card products"),
                    new SourceReference(2, "cv.role.tamara.b2", "cv", "Grew the team to 13"),
                    new SourceReference(3, "cv.role.tamara", "cv", "Engineering Manager"))));

    assertThat(rendered)
        .isEqualTo(
            """
            I built two card products [1]. I grew the team to 13 engineers [2][3]

            Sources
             [1] cv.role.tamara.b1  cv  "Built two card products"
             [2] cv.role.tamara.b2  cv  "Grew the team to 13"
             [3] cv.role.tamara     cv  "Engineering Manager"
            """);
  }

  @Test
  void shouldSayHowManyStatementsWereRemoved_whenClaimsWereDropped() {
    var rendered =
        renderer.render(
            answer(
                2,
                List.of(new AnsweredClaim("I built two card products.", List.of(1))),
                List.of(new SourceReference(1, "cv.role.tamara.b1", "cv", "Built"))));

    assertThat(rendered).contains("\n2 statements removed: not supported by my sources.\n");
  }

  private static AnswerResult answer(
      int removedClaims, List<AnsweredClaim> claims, List<SourceReference> sources) {
    return new AnswerResult(
        AnswerMode.ANSWERED, claims, sources, removedClaims, new TraceId("trace-1"));
  }
}
