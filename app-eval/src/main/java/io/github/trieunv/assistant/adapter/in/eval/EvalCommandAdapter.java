package io.github.trieunv.assistant.adapter.in.eval;

import io.github.trieunv.assistant.application.dto.LoadedKnowledge;
import io.github.trieunv.assistant.application.port.in.AskQuestionUseCase;
import io.github.trieunv.assistant.config.AssistantProperties;
import io.github.trieunv.assistant.domain.Hashing;
import io.github.trieunv.assistant.eval.EvalDataset;
import io.github.trieunv.assistant.eval.EvalItem;
import io.github.trieunv.assistant.eval.Scorer;
import io.github.trieunv.assistant.eval.run.EvalRunner;
import io.github.trieunv.assistant.eval.run.ResultsFolder;
import io.github.trieunv.assistant.eval.run.RunRecord;
import io.github.trieunv.assistant.eval.run.ScorecardTable;
import io.github.trieunv.assistant.eval.run.TraceLines;
import io.github.trieunv.assistant.eval.yaml.YamlEvalDatasetReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * {@code ./eval.sh [--reps N] [--split dev|heldout|all]}: runs the dataset through the Ask use
 * case, scores it with code only, prints the table and writes a results folder.
 *
 * <p>Exit code 0 for a completed run, ordinary failures included; 1 when a Hard Line fails; 2 when
 * it refuses to start (an invalid dataset, or an item the Subject has not verified), which is not a
 * run at all.
 */
@Component
@RequiredArgsConstructor
public class EvalCommandAdapter implements CommandLineRunner, ExitCodeGenerator {

  static final int HARD_LINE_FAILED = 1;
  static final int REFUSED = 2;

  private final AskQuestionUseCase askQuestion;
  private final LoadedKnowledge knowledge;
  private final AssistantProperties properties;
  private final PrintStream out = System.out;
  private int exitCode;

  @Override
  public void run(String... args) {
    var options = EvalOptions.parse(args);
    var dataset = new YamlEvalDatasetReader().read(options.dataset());
    var items = dataset.items().stream().filter(options::includes).toList();
    if (refused(dataset, items)) {
      exitCode = REFUSED;
      return;
    }
    out.printf("Running %d questions x %d repetitions...%n", items.size(), options.reps());
    var run =
        new EvalRunner(askQuestion, new TraceLines(properties.getTraceDir()))
            .run(items, options.reps());
    var scorer = new Scorer(dataset, knowledge.companions(), knowledge.getQuoteProvenance());
    var card = scorer.score(run.results());
    var table = ScorecardTable.render(card);
    out.println();
    out.print(table);

    var record =
        new RunRecord(
            LocalDate.now(Clock.systemUTC()).toString(),
            Objects.requireNonNullElse(System.getenv("EVAL_COMMIT"), "unknown"),
            options.split(),
            options.reps(),
            items.size(),
            run.config().path("provider").asString("unknown"),
            run.config().path("modelId").asString("unknown"),
            run.promptHash(),
            Hashing.sha256(read(options.dataset())),
            knowledge.getQuoteProvenance(),
            knowledge.getEntries().size(),
            run.config());
    var folders = new ResultsFolder();
    var folder = folders.create(options.resultsRoot(), record.folderName());
    folders.write(folder, record, run.results(), scorer::verdict, card, table);
    out.println("\nResults: " + folder);
    exitCode = card.hardLineFailures().isEmpty() ? 0 : HARD_LINE_FAILED;
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }

  /** A broken dataset, or an item only the Subject may approve, stops the run before it starts. */
  private boolean refused(EvalDataset dataset, List<EvalItem> items) {
    var problems = dataset.problems(entryIds());
    if (!problems.isEmpty()) {
      out.println("The dataset breaks its own design; fix it first:");
      problems.forEach(problem -> out.println("  " + problem));
      return true;
    }
    var unverified = new EvalDataset(items).unverifiedIds();
    if (!unverified.isEmpty()) {
      out.printf(
          "Refusing to run: the Subject has not verified %d items: %s%n",
          unverified.size(), unverified);
      out.println("Only the Subject decides what must never be claimed about them.");
      return true;
    }
    return false;
  }

  private Set<String> entryIds() {
    return knowledge.getEntries().stream()
        .map(entry -> entry.id().value())
        .collect(Collectors.toSet());
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    }
  }

  /** Command-line options; anything else is left to Spring. */
  record EvalOptions(int reps, String split, Path dataset, Path resultsRoot) {

    private static final Set<String> SPLITS = Set.of("dev", "heldout", "all");

    static EvalOptions parse(String... args) {
      var reps = 1;
      var split = "all";
      var dataset = Path.of("evals/dataset.yaml");
      var resultsRoot = Path.of("evals/results");
      for (var index = 0; index < args.length - 1; index++) {
        var value = args[index + 1];
        switch (args[index]) {
          case "--reps" -> reps = Integer.parseInt(value);
          case "--split" -> split = value;
          case "--dataset" -> dataset = Path.of(value);
          case "--results" -> resultsRoot = Path.of(value);
          default -> {
            continue;
          }
        }
        index++;
      }
      if (reps < 1 || !SPLITS.contains(split)) {
        throw new IllegalArgumentException("Use --reps N (N >= 1) and --split dev|heldout|all");
      }
      return new EvalOptions(reps, split, dataset, resultsRoot);
    }

    boolean includes(EvalItem item) {
      return "all".equals(split) || item.split().name().equalsIgnoreCase(split);
    }
  }
}
