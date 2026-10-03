package io.github.trieunv.assistant.eval.run;

import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * What one evaluation run was, written to {@code run.json}: enough to reproduce it and compare it
 * with another. {@code askConfig} is the configuration the trace lines recorded (provider, model,
 * prices, retrieval and guardrail settings, prompt version).
 */
public record RunRecord(
    String date,
    String commit,
    String split,
    int repetitions,
    int questions,
    String provider,
    String modelId,
    String promptHash,
    String datasetSha256,
    double quoteProvenance,
    int entries,
    JsonNode askConfig) {

  /** The results folder's name: {@code <date>-<provider>-<model>}. */
  public String folderName() {
    return (date + "-" + provider + "-" + modelId).toLowerCase(Locale.ROOT);
  }
}
