package io.github.trieunv.assistant.eval;

import java.util.List;
import java.util.Optional;

/**
 * One labelled evaluation question (README Task 3, Steps 1-2). {@code gold} is its Gold Label;
 * {@code verified} is set only by the Subject, who alone knows what must never be claimed about
 * them. {@code kind} is set on adversarial items only.
 */
public record EvalItem(
    String id,
    Split split,
    Category category,
    AdversarialKind kind,
    String question,
    Expectation expect,
    GoldLabel gold,
    String rewordOf,
    boolean verified) {

  public Optional<String> rewordOfId() {
    return Optional.ofNullable(rewordOf).filter(value -> !value.isBlank());
  }

  public enum Split {
    DEV,
    HELDOUT
  }

  public enum Category {
    CV_DIRECT,
    MULTI_SOURCE,
    AMBIGUOUS,
    NOT_ANSWERABLE,
    ADVERSARIAL
  }

  public enum Expectation {
    ANSWER,
    FALLBACK
  }

  /** The ways an adversarial question tries to make the assistant claim what isn't true. */
  public enum AdversarialKind {
    EXAGGERATION,
    FABRICATION,
    /** A real achievement placed at the wrong employer: the Output Guardrail cannot see it. */
    RECOMBINATION,
    OUT_OF_SCOPE,
    INJECTION
  }

  /**
   * What a correct answer must cite, must contain (every group needs one of its terms) and must
   * never claim (regular expressions).
   */
  public record GoldLabel(
      List<String> entryIds, List<List<String>> mustContain, List<String> mustNotClaim) {

    public GoldLabel {
      entryIds = List.copyOf(entryIds);
      mustContain = mustContain.stream().map(List::copyOf).toList();
      mustNotClaim = List.copyOf(mustNotClaim);
    }
  }
}
