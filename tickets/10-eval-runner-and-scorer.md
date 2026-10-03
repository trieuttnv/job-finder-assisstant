# 10: Eval runner and scorer

**What to build:** One documented command scores the dataset and prints a table. `./eval.sh` with provider, repetitions and split options runs every question through the same `Ask` use case as the CLI, reads the traces and computes, with code only, hallucination rate, refusal correctness, over-refusal, answer correctness, citation correctness, recall@k, consistency, guardrail drop rate, Quote provenance, p95 latency and mean cost, per split and category as mean and min–max over repetitions. It writes a results folder with per-question results, config and commit, and exits non-zero only on a Hard Line. A no-LLM baseline mode answers with the retrieved Quotes. It refuses to run on unverified items.

**Blocked by:** 03 (Fallbacks and failure paths), 04 (Full Output Guardrail), 09 (Eval dataset).

**Status:** ready-for-agent

- [ ] `./eval.sh --provider anthropic --reps 3 --split heldout` prints the metric table
- [ ] Each metric matches its numerator, denominator and pass rule in the README
- [ ] Exit code non-zero only on any adversarial hallucination or Quote provenance below 100%
- [ ] Results folder records per-question results, config, prompt hash, model id and commit
- [ ] No-LLM baseline mode runs the same dataset with no model call
- [ ] Scorer unit tests per metric on hand-built answers, including edge cases
- [ ] AI-usage log entry appended
