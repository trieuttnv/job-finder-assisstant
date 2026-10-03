# 02: CV knowledge base

**What to build:** The assistant answers from the Subject's real CV. The CV Snapshot is committed with its email redacted and a provenance record (date, what was copied, redactions). A model drafts the `cv` Entries (one per role, one per bullet linked to its role, skill groups, education, summary), and the Subject reviews every one before commit. At startup the service loads the Snapshots and Entries and stops on the first problem, naming the Entry and the reason, then reports which ids were added, changed or removed since the last run.

**Blocked by:** 01 (Walking skeleton).

**Status:** ready-for-human (the Subject reviews the drafted Entries)

- [x] CV Snapshot committed with the email replaced by `[redacted-email]`, and its provenance record
- [x] `cv` Entry file reviewed by the Subject; ids are readable (for example `cv.role.tamara.b1`)
- [x] Startup fails with Entry id and reason on: invalid schema, duplicate id, unresolved `parent`, a Quote not found word for word in its Snapshot, a typed field (employer, title, start, end) not in its Quote
- [x] Startup reports ids added, changed and removed against the previous run
- [x] Ingest tests with fixture Snapshots cover each failure and the id report
- [ ] `./ask "When did you work at Atlassian?"` answers Sep 2014 – Jul 2015, cited to the CV role
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).** Built and checked; two items wait on the Subject.

- Snapshot: `knowledge/sources/cv/cv.txt`, the text of `~/JobFinder/Trieu_Nguyen_CV.pdf` (3 pages, SHA-256 in `knowledge/sources.yaml`) extracted once with PDFBox 3.0.8, email replaced by `[redacted-email]`. The PDF is not committed because it holds the email.
- Entries: `knowledge/entries/cv.yaml`, 55 Entries drafted by Claude Code: 7 roles, 28 bullets, 5 summary sentences, headline, contact line, 7 Core Strengths (typed `summary`), 5 skill groups, education. **Waiting on the Subject's review**, Entry by Entry. Choices to check: the summary is split per sentence and Core Strengths are `summary` Entries (the type list has no `strength`); the two Alipay roles are `cv.role.alipay-2017` and `cv.role.alipay-2015`; the 123Pay role's employer is `VNG Corporation, 123Pay`; the earlier-roles employer is the list of four companies.
- "Word for word" compares on a key that collapses whitespace and joins a line break after `-` or `/` (PDF wrapping of `high-level`, `SLO/SLI`); the stored Quote is never rewritten. A typed date `2017-06` must appear as `Jun 2017` or `June 2017`. Ids must be lower-case and start with their Source.
- Also checked at startup: no email address left in any Snapshot.
- The id report prints to stderr only when something changed, against `STATE_DIR/knowledge-ids.json` (git-ignored). A failed load saves nothing.
- `./ask "When did you work at Atlassian?"`: retrieval ranks `cv.role.atlassian` first and the cited answer renders, checked against a local mock of the Anthropic API. **Not yet checked with a real key**: the key in `.env` was rejected with 401.

**2026-10-04, the Subject.** Reviewed `knowledge/entries/cv.yaml`: all 55 Entries accepted as drafted, with no changes.
