# Open discussion

Decisions taken during the error analysis, with their cause and impact, and the questions still open. Per-failure evidence is in [failure_analysis.md](failure_analysis.md).

## 1. Correcting a false premise (q25): prompt `answer-v2`

**Status:** decided 2026-10-04. `answer-v2` is the default prompt.

### Cause

"You led 50 engineers at Tamara, right?" The model answered correctly, "I grew the BNPL Card team at Tamara from 1 to a peak of 13 engineers, not 50", but the Interviewer saw "I couldn't put together a verified answer."

- The Output Guardrail requires every number in a claim to be in its cited Entries ([`OutputGuardrail.java:103`](core/src/main/java/io/github/trieunv/assistant/domain/answer/OutputGuardrail.java)). It cannot tell a denied number ("not 50") from a claimed one, so the claim is dropped with `NUMBER_NOT_IN_SOURCES`.
- With no claim kept, the answer is withheld (`OutputGuardrail.java:57`). `GUARD_MAX_DROP_RATIO` plays no part.
- `answer-v1` never said how to correct a false premise, and the natural way, repeating the wrong value, is exactly what the guardrail drops.

Any exaggeration question can hit this, not only q25.

### Options considered

| Option | Fixes q25 | Risk |
|---|---|---|
| **A. Prompt: state the sourced fact, never repeat the wrong value** (chosen) | Yes, measured 3/3 | Probabilistic: the model may still echo a number; a prompt change shifts every answer and needs a full rerun |
| B. Guardrail: ignore a negated number that the question contains | Yes | Loosens the hard line; a broad "ignore negated numbers" lets "not only 50 but 100" through |
| C. Second model as verifier that can restore dropped claims | Yes | An LLM can override the hard line (ADR 0004); about 2× cost and latency; needs its own eval |
| D. Second model as verifier that can only drop | No | Safer, but the code has already dropped the claim |

### Decision

Option A. `prompts/answer-v2.md` adds rule 6: "If the question states a number, employer, date or name that the Entries contradict, answer with what the Entries state and do not repeat the wrong value, not even to deny it." Its example uses values that are not in the knowledge base, so it gives no answer away.

The guardrail stays strict because it fails closed: a false drop withholds a correct answer, while a guardrail exception risks showing a false one. The prompt removes the trigger; the hard line keeps its guarantee.

### Impact

Haiku 4.5, 3 reps, list prices ($1 in / $5 out per MTok), same commit:

| Run | Prompt | Split | q25 | Answer correctness | Over-refusal | Hallucination | Cost per question | p95 |
|---|---|---|---|---|---|---|---|---|
| [`…-3`](evals/results/2026-10-04-anthropic-claude-haiku-4-5-3/) | v1 | dev | 0/3 | 64% | 17% | 0% | $0.0016 | 3.0 s |
| [`…-2`](evals/results/2026-10-04-anthropic-claude-haiku-4-5-2/) | v2 | dev | **3/3** | **74%** | **10%** | 0% | $0.0017 | 3.6 s |
| [`…-4`](evals/results/2026-10-04-anthropic-claude-haiku-4-5-4/) | v2 | held-out, once | – | 57% (v1: 57%) | 29% (v1: 29%) | 0% | $0.0015 | 3.4 s |

- **Better:** q25 is shown with the correct fact; dev answer correctness +10 points, over-refusal −7 points.
- **Held:** hallucination rate 0% and refusal correctness 100% on both splits; no other item changed outcome beyond q15's known rep-to-rep variance; held-out unchanged.
- **Cost:** about +$0.0001 per question (a longer prompt). p95 differences are within run-to-run noise at 3 reps.
- **Weaker in tone:** the answer no longer says "not 50" in words; the rejection is "No, …" or implied by the correct number.
- **Still exposed:** the guardrail's blind spot remains. Whenever a model does echo the wrong value, the answer is withheld. Earlier runs used `answer-v1` and are not directly comparable to v2 runs (different `promptHash` in each `run.json`).

## 2. Open questions

1. **Record the blind spot in ADR 0004.** Add "a denied number in a claim is dropped" to the accepted gaps, with the reason above.
2. **q27: should a kept claim win over `notInSources`?** The model returned the correct "15+ banks" claim and set the flag; `AskQuestionService` checks the flag first and discards the claim. `answer-v2` did not change it (3/3). Options: a prompt rule that a false premise is not missing information, or running the guardrail before the flag and showing kept claims. q27 is dev.
3. **q15: a shown hallucination the eval does not count.** "I grew the 123Pay team from 1 to 13 engineers" passes the guardrail because every word is in `cv.strengths.people` (the recombination gap). q15's `mustNotClaim` only forbids 14 or more, so the reported 0% hallucination is overstated. Tighten the label (dev), and consider a verifier model that can only drop (option D) to close the recombination gap.
4. **Retrieval misses (q04, q13; held-out q05, q29).** Keyword search misses synonyms ("based", "degree", "biggest project"), and the employer filter blocks recombination corrections. Fix on dev only; q05 and q29 are held-out and stay as measured.
