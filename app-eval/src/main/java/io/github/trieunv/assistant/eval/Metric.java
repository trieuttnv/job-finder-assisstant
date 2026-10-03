package io.github.trieunv.assistant.eval;

/** The metrics of README Task 3, Step 4, each computed by code only (ADR 0006). */
public enum Metric {
  HALLUCINATION_RATE("Hallucination rate", Unit.SHARE),
  REFUSAL_CORRECTNESS("Refusal correctness", Unit.SHARE),
  OVER_REFUSAL("Over-refusal", Unit.SHARE),
  ANSWER_CORRECTNESS("Answer correctness", Unit.SHARE),
  CITATION_CORRECTNESS("Citation correctness", Unit.SHARE),
  RECALL_AT_K("Recall@k", Unit.SHARE),
  CONSISTENCY("Consistency", Unit.SHARE),
  GUARDRAIL_DROP_RATE("Guardrail drop rate", Unit.SHARE),
  QUOTE_PROVENANCE("Quote provenance", Unit.SHARE),
  P95_LATENCY_MS("p95 latency", Unit.MILLISECONDS),
  MEAN_COST_USD("Mean cost", Unit.US_DOLLARS);

  private final String label;
  private final Unit unit;

  Metric(String label, Unit unit) {
    this.label = label;
    this.unit = unit;
  }

  public String label() {
    return label;
  }

  public Unit unit() {
    return unit;
  }

  public enum Unit {
    SHARE,
    MILLISECONDS,
    US_DOLLARS
  }
}
