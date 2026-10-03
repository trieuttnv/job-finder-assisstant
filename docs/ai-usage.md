# AI usage log

One entry per ticket, written when it lands: what a model generated, what the Subject changed after review, and minutes spent. The README's AI-usage note is written from this log.

| Date | Work | Model-generated | Changed after review | Minutes |
|---|---|---|---|---|
| 2026-10-03 | Design (README RFC, ADRs 0001–0008, CONTEXT.md, exercise map) | Drafted by Claude Code (Opus 5.5) through a structured interview | Every decision chosen by the Subject at a gate; restructured from per-task answers into a system design; map moved out of the README | *(fill in)* |
| 2026-10-04 | Ticket 01: walking skeleton (Maven multi-module, `Ask` use case, Lucene BM25, Anthropic adapter, citation rule, CLI rendering, JSONL traces, ArchUnit rule) | Code, tests, prompt `answer-v1`, fixture Entries and `./ask` written by Claude Code (Opus 5.5) test-first at the `Ask` use-case seam; checked end to end against a local mock of the Anthropic API (no key on the machine) | *(Subject to fill in after review)* | *(fill in)* |
| 2026-10-04 | Ticket 02: CV knowledge base (CV Snapshot and provenance, 55 `cv` Entries, startup ingest checks, id report, `LoadKnowledgeTest`) | Snapshot extraction, the Entry file draft, the checks and their tests written by Claude Code (Opus 5.5), test-first at the ingest seam; `./ask` checked against a local mock of the Anthropic API | Entries: none, all 55 accepted as drafted by the Subject |  *(fill in)* |
| 2026-10-04 | Ticket 03: fallbacks and failure paths (Input Guardrail floor, `notInSources`, unavailable and error modes, timeout and missing-key detection, `AskFallbacksTest`) | Code and tests written by Claude Code (Opus 5.5) test-first at the `Ask` use-case seam; the provisional score floor measured on 12 questions against the real CV | *(Subject to fill in after review)* | *(fill in)* |
| 2026-10-04 | Ticket 04: full Output Guardrail (number, date and name rules, normalisation, `names.yaml`, drop-ratio withholding, `OutputGuardrailTest`) | Code, tests and the name list written by Claude Code (Opus 5.5), test-first: unit tests per rule and normalisation, then through the `Ask` use case | *(Subject to fill in after review)* | *(fill in)* |
