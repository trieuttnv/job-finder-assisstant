package io.github.trieunv.assistant.eval.run;

import io.github.trieunv.assistant.eval.QuestionResult;
import io.github.trieunv.assistant.eval.Scorecard;
import io.github.trieunv.assistant.eval.Verdict;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes one run's folder under {@code evals/results/}: per-question results, the scorecard, the
 * printed table and what the run was (config, prompt hash, model, commit), so runs are comparable
 * and every number can be recomputed.
 */
public class ResultsFolder {

  private final JsonMapper json =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
  private final JsonMapper lines = JsonMapper.builder().build();

  private record Line(QuestionResult result, Verdict verdict) {}

  /** A new folder {@code <root>/<name>}, or {@code <name>-2}, {@code -3}... if it exists. */
  public Path create(Path root, String name) {
    try {
      Files.createDirectories(root);
      var folder = root.resolve(name);
      for (var n = 2; Files.exists(folder); n++) {
        folder = root.resolve(name + "-" + n);
      }
      return Files.createDirectories(folder);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create a results folder in " + root, e);
    }
  }

  /** Each results line is one result with its verdict, so a failure explains itself. */
  public void write(
      Path folder,
      RunRecord run,
      List<QuestionResult> results,
      Function<QuestionResult, Verdict> verdicts,
      Scorecard card,
      String table) {
    try {
      Files.writeString(folder.resolve("run.json"), json.writeValueAsString(run));
      var resultLines =
          results.stream()
              .map(result -> lines.writeValueAsString(new Line(result, verdicts.apply(result))))
              .collect(Collectors.joining("\n", "", "\n"));
      Files.writeString(folder.resolve("results.jsonl"), resultLines);
      Files.writeString(folder.resolve("scorecard.json"), json.writeValueAsString(card));
      Files.writeString(folder.resolve("table.txt"), table);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write the results to " + folder, e);
    }
  }
}
