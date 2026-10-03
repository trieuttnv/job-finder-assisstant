# 05: Retrieval filters and expansion

**What to build:** Retrieval finds the right Entries for a question. When a question names a known employer, technology or Entry type, results are filtered or boosted by it; the name lists are built from the Entries, never hand-coded. Every retrieved bullet brings its role header, so answers always know the employer and dates.

**Blocked by:** 02 (CV knowledge base).

**Status:** ready-for-agent

- [x] "What did you build at Tamara?" retrieves Tamara Entries only
- [x] Each retrieved bullet is accompanied by its `parent` role Entry
- [x] Employer, technology and type names are derived from the Entries at startup
- [x] `RETRIEVAL_TOP_K` (default 6) is honoured
- [x] Tests through the `Ask` use case on fixture Entries for filtering and expansion
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).**

- `RetrievalVocabulary` (domain) is built at startup from the Entries. It has no hand-coded lists:
  - **Employers** come from each Entry's `employer` field: every comma-separated part, plus an acronym that leads a part ("VNG").
  - **Technologies** come from `knowledge/names.yaml`, but only those some Entry's Quote mentions.
  - **Entry types** come from the fixed type list (`skill`/`skills`, `role`/`roles`, …).
  - Questions match names of three or more letters in any case, so "123pay" is found. Shorter names match only as written ("Go").
- `LuceneRetrieverAdapter` applies them:
  - **Employers filter.** A bullet is indexed with its role's employer, and an Entry with no employer (a summary or strength) belongs to every employer its Quote names. So "at Alipay" keeps Alipay's Entries and the fraud strength that names Alipay, and drops Tamara's.
  - **Technologies and types only boost Entries the question's words already match.** A type word therefore can't pull in unrelated Entries, nor lift an unrelated question ("What skills does Messi have?") over the Input Guardrail floor. A question that asks for a type and nothing else ("Tell me about your education") matches that type's Entries; this matters because the education Quote never says "education". A short list of request words ("tell", "what", "your", …) decides "nothing else".
  - Technology names match only as written, as in `names.yaml`. Employers match in any case ("at tamara").
- Technology names come from the committed `names.yaml`, kept only when some Entry mentions them; employers and types come from the Entries and the type list. The other side of a Conflict (story 29) comes with ticket 06.
- Every matched bullet brings its parent role, flagged `expanded` in the trace. `RETRIEVAL_TOP_K` counts matches only, and the Input Guardrail floor ignores expanded Entries.
- Retrieval on the real CV:
  - "What did you build at Tamara?" → Tamara Entries only
  - "What did you do at 123Pay?" → `cv.role.vng` and `cv.role.vng.b1`
  - "Tell me about your education" → `cv.education`
- **Still a gap:** "Where did you study?" finds no education Entry, because "study" appears nowhere and synonyms would be hand-coded. Ticket 09's recall@k measures it.
