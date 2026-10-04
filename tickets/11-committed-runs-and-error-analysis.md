# 11: Committed runs and error analysis

**What to build:** The evaluation results a reviewer reads, committed as they are. Run Claude Haiku 4.5 (the Subject's choice; the Anthropic adapter cannot call Sonnet 5.5 yet), DeepSeek Flash × 1 and the no-LLM baseline, commit each results folder with its failures (at least two), and tag every failure as retrieval miss, generation, guardrail false drop or label too strict. The Subject writes the error analysis. The measured cost per question and p95 latency per provider go into the README, against the p95 target of 10 s.

**Blocked by:** 05 (Retrieval filters and expansion), 07 (DeepSeek adapter), 08 (Interactive CLI), 10 (Eval runner and scorer).

**Status:** ready-for-human (the Subject tags failures and writes the analysis)

- [ ] Three results folders committed (Haiku final run × 1, DeepSeek × 1 once the remaining failures are fixed, baseline)
- [x] At least two failures present and each tagged with one cause (`failure_analysis.md`)
- [ ] Error analysis in `failure_analysis.md`, reviewed by the Subject, summarised in the README
- [ ] Measured cost per question and p95 per provider in Scalability and Performance
- [x] Tuning done only on dev; headline numbers from held-out (q30 burned to dev, q31 added)
- [ ] AI-usage log entry appended
