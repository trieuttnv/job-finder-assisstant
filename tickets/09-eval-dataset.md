# 09: Eval dataset

**What to build:** A labelled dataset of 30 realistic interviewer questions that the evaluation can score mechanically. Six per category (CV-direct, multi-source, ambiguous, not answerable, adversarial), split 20 dev and 10 held-out. Each has a Gold Label: the Entry ids it should cite, term groups it must contain (with synonyms), patterns it must never claim, and whether it expects an answer or a fallback. A model drafts them from the Entries; the Subject verifies each one.

**Blocked by:** 02 (CV knowledge base), 06 (Learning repo and the real Conflict).

**Status:** ready-for-human (the Subject verifies every Gold Label)

- [ ] 30 items, 6 per category, 20 dev and 10 held-out
- [ ] At least two adversarial items test recombination (a real achievement at the wrong employer); others cover exaggeration, fabrication, out of scope and an injected instruction
- [ ] Three reworded pairs, linked
- [ ] Ambiguous items expect the main candidates, each cited
- [ ] Every Gold Label's Entry ids resolve; every item is `verified: true` by the Subject
- [ ] AI-usage log entry appended
