# 08: Interactive CLI

**What to build:** An Interviewer who doesn't know what to ask runs `./ask` with no argument and gets a session that opens with five suggested questions, one per evaluation category, so each behaviour (including a fallback and a Conflict) is one keystroke away. The rendered output is consistent everywhere: citations match the Sources block, fallbacks never show one, and the removed-claims line appears only when claims were dropped.

**Blocked by:** 03 (Fallbacks and failure paths), 04 (Full Output Guardrail).

**Status:** ready-for-agent

- [ ] `./ask` with no argument opens a session showing five suggested questions
- [ ] Every answer's citation numbers match its Sources block
- [ ] Every fallback prints only its message, with no Sources block
- [ ] The removed-claims line appears exactly when claims were dropped
- [ ] Rendered-output tests through the `Ask` use case
- [ ] AI-usage log entry appended
