# 10: Eval runner and scorer

**What to build:** One documented command scores the dataset and prints a table. `./eval.sh` with provider, repetitions and split options runs every question through the same `Ask` use case as the CLI, reads the traces and computes, with code only, hallucination rate, refusal correctness, over-refusal, answer correctness, citation correctness, recall@k, consistency, guardrail drop rate, Quote provenance, p95 latency and mean cost, per split and category as mean and min–max over repetitions. It writes a results folder with per-question results, config and commit, and exits non-zero only on a Hard Line. A no-LLM baseline mode answers with the retrieved Quotes. It refuses to run on unverified items.

**Blocked by:** 03 (Fallbacks and failure paths), 04 (Full Output Guardrail), 09 (Eval dataset).

**Status:** ready-for-agent

- [ ] `./eval.sh --provider anthropic --reps 3 --split heldout` prints the metric table
- [x] Each metric matches its numerator, denominator and pass rule in the README
- [x] Exit code non-zero only on any adversarial hallucination or Quote provenance below 100%
- [x] Results folder records per-question results, config, prompt hash, model id and commit
- [x] No-LLM baseline mode runs the same dataset with no model call
- [x] Scorer unit tests per metric on hand-built answers, including edge cases
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).** Built and tested. The first box ("`./eval.sh --provider anthropic --reps 3 --split heldout` prints the metric table") stays open for two reasons. The dataset is not yet verified by the Subject, so the runner correctly refuses with exit code 2. And there is no working Anthropic key, because the Subject chose to skip real calls. The same command was checked end to end with the no-LLM baseline on a temporary verified copy of the dataset; that copy was outside the repository and not committed.

- **Shared wiring.** The configuration, properties and `application.yml` moved from `app-cli` into a new `wiring` module that `app-cli` and `app-eval` share. The eval therefore runs exactly the CLI's `Ask` use case. `app-cli` builds a Boot jar, which can't be a dependency.
- **`./eval.sh [--provider anthropic|deepseek|baseline] [--baseline] [--reps N] [--split dev|heldout|all]`** builds `app-eval` and runs it.
  - It refuses (exit 2) a dataset that breaks its design or any unverified item it would run.
  - It asks each question through the `Ask` use case, then reads each trace line by trace id from the run's own git-ignored `traces/eval/<time>/` folder.
  - It scores, prints the table and writes `evals/results/<date>-<provider>-<model>/`:
    - `results.jsonl`: one line per question and repetition, written so ticket 11 can tag failures from the committed file alone, without the git-ignored traces. Each line holds:
      - the mode, and the shown claims with their Entry ids
      - the ranked retrieval hits (score, and whether a hit was expanded)
      - every dropped claim with its reason
      - tokens, latency, cost and trace id
      - a verdict: passed, the `mustContain` groups missed, and the `mustNotClaim` patterns matched
    - `scorecard.json`
    - `table.txt`
    - `run.json`: commit (with a `-dirty` flag), the prompt hash and the dataset SHA-256, and the configuration the trace lines recorded (provider, model, price, retrieval and guardrail settings, prompt version)
  - A missing trace line stops the run rather than zeroing recall, latency and the drop rate.
  - Exit 1 only on a Hard Line.
- **`Scorer`** computes the README Task 3, Step 4 metrics by code. Each is computed per repetition for all, dev, held-out and each category, then reported as mean and min–max:
  - Hallucination rate (over shown answers, not fallbacks), Refusal correctness, Over-refusal, Answer correctness
  - Citation correctness: gold ids, or their role headers and Conflict partners
  - Recall@k: all gold ids among retrieved, including expanded Entries
  - Consistency: a reworded pair is consistent when both pass or both fail and they cite overlapping ids; two fallbacks have nothing to cite
  - Guardrail drop rate, Quote provenance
  - p95 latency (nearest-rank, over answered questions)
  - Mean cost, over every question that made a billed model call, fallbacks included
  - The README metric table now states the consistency rule for two fallbacks, what Recall@k counts, and these denominators.

  Hard Lines: any adversarial hallucination, or Quote provenance below 100%. Terms and patterns are matched against shown claims only.
- **Quote provenance** is counted by code at load time (`KnowledgeCheck.quoteProvenance`) rather than assumed.
- **No-LLM baseline.** `MODEL_PROVIDER=baseline`, or `--baseline`, uses `QuoteBaselineModelAdapter`: the retrieved Quotes are the claims, each citing itself, through the same pipeline. It works from `./ask` too. `AnswerPrompt` now carries the retrieved Entries as values for it.
- **Tests:**
  - `ScorerTest`, 15 cases: every metric; the empty-denominator, all-refusals and reworded-pair edge cases; repetition ranges and scopes; the cost of a fallback; verdicts; both Hard Lines, including an honest correction that must pass.
  - `EvalRunnerTest`: runs the real `Ask` pipeline on the real knowledge with the real trace writer, and checks every number read from a trace line plus the missing-trace stop.
  - `QuoteBaselineModelAdapterTest` covers the baseline.
- **Known limits:**
  - All three reworded pairs are in dev, so held-out consistency shows "–".
  - Exit code 2 (refused) is not a run, so "non-zero only on a Hard Line" applies to completed runs.
