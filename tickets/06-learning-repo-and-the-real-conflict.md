# 06: Learning repo and the real Conflict

**What to build:** The assistant knows about the Subject's Go learning prototypes and handles the real Conflict honestly. The learning-repository Snapshot (READMEs, plans and measured reports of `learn-kafka` and `learn-outbox`) is committed with its commit and date as provenance, and its drafted Entries are reviewed by the Subject. The Conflict "the CV lists Java and Spring Boot; the prototypes are written in Go" is recorded with both sides' Entry ids, topic terms and rule. Retrieving either side brings the other; code renders the Conflict notice with both sides cited when a surviving claim cites either side or the question contains a topic term.

**Blocked by:** 02 (CV knowledge base), 04 (Full Output Guardrail).

**Status:** ready-for-human (the Subject reviews the drafted Entries)

- [ ] Learning-repo Snapshot and provenance committed; Entries reviewed by the Subject
- [x] Conflict recorded with both sides, topic terms and rule
- [x] "What languages do you write code in?" shows Java and Spring Boot from the CV and Go from the prototypes, both cited, with the Conflict notice
- [x] A merged claim ("experienced in Go") citing CV Entries is dropped by the Output Guardrail
- [x] "What did you build at Tamara?" shows no Conflict notice
- [x] Tests through the `Ask` use case for expansion and the relevance rule
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).** All built and tested. One gate is still open: the Subject's review of the drafted Entries (the first box stays unchecked until then).

- **Snapshot:** `knowledge/sources/gh-learn/` holds eight files copied unedited with `git show` at the pinned commit (2026-09-18): for each prototype, the README, `IMPLEMENTATION_PLAN.md`, the measured report and `go.mod`. The provenance record in `knowledge/sources.yaml` names the commit and date only.
  - A Snapshot can now be a folder. A Quote must be word for word within one of its files, never across two.
- **Entries:** `knowledge/entries/gh-learn.yaml` has 26 `project` Entries drafted by Claude Code: each prototype's title, what it is ("a rig"), design points, measured results, and the `go 1.23` lines from both `go.mod` files.
  - **Waiting on the Subject's review.**
  - Ids are `gh-learn.<prototype>.<topic>`, because ids must start with their Source. The README's example ids are updated to match.
- **Conflict:** `knowledge/conflicts.yaml` records `languages`.
  - **CV side:** `cv.skills.languages`.
  - **Go side:** both `go.mod` Entries, plus the 2026-dated learn-kafka report, so "2026" is cited too.
  - **Terms:** `language`, `languages`, `Go`, `Golang`, `Java`. "programming" is dropped because it caught "pair programming".
  - Startup fails, naming the Conflict, if a side cites an Entry that doesn't exist.
- **Retrieval:** retrieving either side brings the other (flagged `expanded`).
- **Notice:** appended by code to an answer, with both sides cited, whenever the question names a term or a surviving claim cites a side. It never replaces a fallback: "That isn't in my sources." and "I couldn't put together a verified answer." stay as they are.
- **Checked against a local stub of the Anthropic API**, for "What languages do you write code in?":
  - The honest CV claim is kept.
  - "I am experienced in Go", citing the CV, is dropped, with the removed-claims line.
  - The notice cites both sides.
  - "What did you build at Tamara?" retrieves no Conflict Entry and shows no notice.
- **Known gaps** (the eval measures them):
  - "I am experienced in Go" citing a learning-repo Entry that contains "Go" passes the Output Guardrail. This is the recombination gap ADR 0004 accepts.
  - Language questions without a term ("What's your tech stack?") show the notice only when a claim cites a side.
  - "What languages do you speak?" shows the notice if the model answers at all.
