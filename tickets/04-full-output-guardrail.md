# 04: Full Output Guardrail

**What to build:** Code, not the model, decides what the Interviewer sees. Beyond the citation rule, a claim is dropped if it contains a number or date its cited Entries don't contain, or names an employer, product or technology its cited Entries don't contain (names come from all Entries plus a committed technology list). Before comparing, number words become digits, units and punctuation are stripped, and known name aliases match. The answer says "N statements removed: not supported by my sources." when claims were dropped, and becomes "I couldn't put together a verified answer." when none survive or more than `GUARD_MAX_DROP_RATIO` (default 0.5) were dropped.

**Blocked by:** 01 (Walking skeleton).

**Status:** ready-for-agent

- [x] A claim with a number absent from its cited Entries is dropped; "four percent" against "4%" is kept
- [x] A claim naming an employer or technology absent from its cited Entries is dropped (for example "Goldman", or "Go" citing a CV Entry)
- [x] The removed-claims line appears exactly when at least one claim was dropped
- [x] No surviving claim, or drop ratio above the threshold → "I couldn't put together a verified answer."
- [x] Unit tests per rule and per normalisation; tests through the `Ask` use case for the withheld answer
- [x] AI-usage log entry appended

## Comments

- 2026-10-04 (ticket 01 review): the CLI renderer already prints "N statements removed: not supported by my sources." when `removedClaims > 0` (singular for 1), tested in `AnswerRendererTest`. This ticket still owns the box: confirm the line appears *exactly* when claims were dropped through the `Ask` use case, including the withheld-answer cases.
- 2026-10-04, Claude Code (implementation):
  - `OutputGuardrail` (domain, no model) now drops a claim with a number, year or month its cited Entries lack (`NUMBER_NOT_IN_SOURCES`) or a name they lack (`NAME_NOT_IN_SOURCES`).
  - **Normalisation (`ClaimText`):** number words become digits, including "twenty-five". Thousands separators and units (`%`, `+`, `M`, `ms`, …) are stripped. Typed `start`/`end` fields count as the cited year and month.
  - **Names:** employers come from every Entry's `employer` field. Technologies and aliases come from the committed `knowledge/names.yaml` (Golang → Go, K8s → Kubernetes, …). Any capitalised word that doesn't start a sentence counts as a name too; this is how "Goldman", which no list contains, is caught. Only "I" and "CV" are exempt.
  - Listed names match only as written, so "go", "visa" and "react" as plain words are not checked as names. An earlier any-case rule dropped honest claims ("work visa", "react quickly"), so I removed it.
  - A cited bullet brings its parent role, so "At Alipay I improved payment success by 4%" citing only the bullet passes, while the same achievement placed at Tamara is still dropped.
  - Every number is checked, whatever unit is glued to it ("500TB", "5G"). A scale is part of its value, so "60K" fails against "60M" and "60 million" passes.
  - **Withheld answer:** no surviving claim (including an empty claims list from the model) or a drop ratio above `GUARD_MAX_DROP_RATIO` gives `UNVERIFIED`, "I couldn't put together a verified answer." A ratio of exactly 0.5 is shown with the removed line.
  - **Known gaps** (the eval measures them):
    - **False drops:** the capitalised-word rule drops honest claims naming something the cited Entries don't spell out ("HCMC", "Site Reliability Engineering"), and so do derived durations ("four years at Tamara").
    - **Passes it shouldn't:**
      - an unlisted name that starts a sentence ("Google hired me")
      - a lower-case title ("engineering director")
      - facts mixed across two cited Entries
