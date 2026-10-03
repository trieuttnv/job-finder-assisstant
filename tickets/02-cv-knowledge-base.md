# 02: CV knowledge base

**What to build:** The assistant answers from the Subject's real CV. The CV Snapshot is committed with its email redacted and a provenance record (date, what was copied, redactions). A model drafts the `cv` Entries (one per role, one per bullet linked to its role, skill groups, education, summary), and the Subject reviews every one before commit. At startup the service loads the Snapshots and Entries and stops on the first problem, naming the Entry and the reason, then reports which ids were added, changed or removed since the last run.

**Blocked by:** 01 (Walking skeleton).

**Status:** ready-for-human (the Subject reviews the drafted Entries)

- [ ] CV Snapshot committed with the email replaced by `[redacted-email]`, and its provenance record
- [ ] `cv` Entry file reviewed by the Subject; ids are readable (for example `cv.role.tamara.b1`)
- [ ] Startup fails with Entry id and reason on: invalid schema, duplicate id, unresolved `parent`, a Quote not found word for word in its Snapshot, a typed field (employer, title, start, end) not in its Quote
- [ ] Startup reports ids added, changed and removed against the previous run
- [ ] Ingest tests with fixture Snapshots cover each failure and the id report
- [ ] `./ask "When did you work at Atlassian?"` answers Sep 2014 – Jul 2015, cited to the CV role
- [ ] AI-usage log entry appended
