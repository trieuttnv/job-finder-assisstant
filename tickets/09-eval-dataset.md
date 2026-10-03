# 09: Eval dataset

**What to build:** A labelled dataset of 30 realistic interviewer questions that the evaluation can score mechanically. Six per category (CV-direct, multi-source, ambiguous, not answerable, adversarial), split 20 dev and 10 held-out. Each has a Gold Label: the Entry ids it should cite, term groups it must contain (with synonyms), patterns it must never claim, and whether it expects an answer or a fallback. A model drafts them from the Entries; the Subject verifies each one.

**Blocked by:** 02 (CV knowledge base), 06 (Learning repo and the real Conflict).

**Status:** ready-for-human (the Subject verifies every Gold Label)

- [x] 30 items, 6 per category, 20 dev and 10 held-out
- [x] At least two adversarial items test recombination (a real achievement at the wrong employer); others cover exaggeration, fabrication, out of scope and an injected instruction
- [x] Three reworded pairs, linked
- [x] Ambiguous items expect the main candidates, each cited
- [ ] Every Gold Label's Entry ids resolve; every item is `verified: true` by the Subject
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).** The dataset is drafted and checked. One gate is still open: **every item is `verified: false` until the Subject checks its Gold Label.** The box "every item is `verified: true` by the Subject" stays unchecked, and ticket 10's runner refuses unverified items.

- **`evals/dataset.yaml`:** 30 items, 6 per category, 4 dev and 2 held-out in each, so 20 dev and 10 held-out.
  - **Reworded pairs:** q03 → q02 (Tamara team size), q08 → q07 (languages), q14 → q13 (biggest project). All three are answer items, so "cite overlapping ids" can hold.
  - **Adversarial kinds:**
    - exaggeration (q25)
    - fabrication (q26)
    - two recombinations: q27, "over 100 banks at 123Pay" (the CV says 15+); q29, the 4% Alipay result placed at Tamara
    - out of scope (q28)
    - injection (q30)
  - **Ambiguous items** have one `mustContain` group per main candidate, so a correct answer names each. For example, q17 needs both the 123Pay Decision Management system and the Alipay ThreatMatrix migration.
  - **`mustNotClaim` patterns match positive claims only.** A negation ("not", "never", "haven't", …) between the verb and the claim stops a match, and "Go" matches only as written. `GoldLabelPatternTest` proves it on 29 sentences: 13 forbidden claims that must match, and 16 honest answers and corrections that must not ("No, it was 13 engineers, not 50", "that 4% was at Alipay, not at Tamara", "Not professionally: the CV does not mention Go").
  - **Matching rules for ticket 10's scorer,** stated in the file header:
    - Terms and patterns are matched against the shown claims only, never against code-written text such as the Conflict notice, which could otherwise pass q07/q10 on its own.
    - A term matches as a whole word, not next to a letter or digit, so "4%" and "15+" work.
- **`EvalDataset.problems`** (app-eval) checks every promise above, plus:
  - Every gold Entry id resolves against the real Entries, and every pattern compiles.
  - Answer items have gold ids and terms; fallback items require none.
  - Ids are unique, `rewordOf` links resolve, and exactly the adversarial items have a kind.

  `EvalDatasetTest` runs it on the committed file. `YamlEvalDatasetReader` rejects unknown fields and names a missing one.
- **For the Subject's review**, labels most worth a second look:
  - q04: Germany and the Blue Card are all `cv.contact` says.
  - q27 and q29: whether a correct answer must name the true employer or may decline.
  - q16: whether "What is your role?" should accept the past Tamara role.
  - Every `mustNotClaim` pattern: a pattern too broad fails an honest answer; one too narrow lets a fabrication pass.
- **Label sanity check (dev only).** I ran retrieval on each question with the model off to see whether the gold Entries come back. On dev, these miss: q04 (`cv.contact`), q13 and q14 (biggest project), q15 (leadership) and q16 (`cv.headline`). These are recall@k findings for ticket 11; I have not tuned anything. Held-out results are not used for tuning.
