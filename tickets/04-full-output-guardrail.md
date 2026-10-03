# 04: Full Output Guardrail

**What to build:** Code, not the model, decides what the Interviewer sees. Beyond the citation rule, a claim is dropped if it contains a number or date its cited Entries don't contain, or names an employer, product or technology its cited Entries don't contain (names come from all Entries plus a committed technology list). Before comparing, number words become digits, units and punctuation are stripped, and known name aliases match. The answer says "N statements removed: not supported by my sources." when claims were dropped, and becomes "I couldn't put together a verified answer." when none survive or more than `GUARD_MAX_DROP_RATIO` (default 0.5) were dropped.

**Blocked by:** 01 (Walking skeleton).

**Status:** ready-for-agent

- [ ] A claim with a number absent from its cited Entries is dropped; "four percent" against "4%" is kept
- [ ] A claim naming an employer or technology absent from its cited Entries is dropped (for example "Goldman", or "Go" citing a CV Entry)
- [ ] The removed-claims line appears exactly when at least one claim was dropped
- [ ] No surviving claim, or drop ratio above the threshold → "I couldn't put together a verified answer."
- [ ] Unit tests per rule and per normalisation; tests through the `Ask` use case for the withheld answer
- [ ] AI-usage log entry appended

## Comments

- 2026-10-04 (ticket 01 review): the CLI renderer already prints "N statements removed: not supported by my sources." when `removedClaims > 0` (singular for 1), tested in `AnswerRendererTest`. This ticket still owns the box: confirm the line appears *exactly* when claims were dropped through the `Ask` use case, including the withheld-answer cases.

