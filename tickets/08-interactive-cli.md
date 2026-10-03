# 08: Interactive CLI

**What to build:** An Interviewer who doesn't know what to ask runs `./ask` with no argument and gets a session that opens with five suggested questions, one per evaluation category, so each behaviour (including a fallback and a Conflict) is one keystroke away. The rendered output is consistent everywhere: citations match the Sources block, fallbacks never show one, and the removed-claims line appears only when claims were dropped.

**Blocked by:** 03 (Fallbacks and failure paths), 04 (Full Output Guardrail).

**Status:** ready-for-agent

- [x] `./ask` with no argument opens a session showing five suggested questions
- [x] Every answer's citation numbers match its Sources block
- [x] Every fallback prints only its message, with no Sources block
- [x] The removed-claims line appears exactly when claims were dropped
- [x] Rendered-output tests through the `Ask` use case
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).**

- **Opening the session.** `./ask` with no argument opens `InteractiveSession`. It lists the README's five example questions, one per evaluation category, each labelled with its category name. The label names the category, not the expected outcome.
- **Input.**
  - A number asks that suggestion and echoes it. A number with no suggestion asks again rather than costing a model call.
  - Anything else is asked as written; blank lines are ignored.
  - "exit", "quit" or the end of input closes the session.
  - An unexpected failure prints the fixed message, and the session goes on.
- **Shared step.** `CliAnswers` holds the ask-and-render step both modes share, including the last-resort catch.
- **`RenderedOutputTest`** runs nine scripted answers through the `Ask` use case and the renderer. Each pins its expected mode, removed-claims count and Conflict count, so the checks can't pass by accident. It checks the three promises on each:
  - Every citation number matches a numbered Sources line, numbered from 1 with no gap.
  - A fallback is one line with no Sources block.
  - The removed-claims line appears exactly when claims were dropped.

  The nine answers are: one claim; claims sharing a citation; a dropped claim; a Conflict notice; not in sources, with and without a model call; withheld; unavailable; and an unexpected failure.
- **Through the session:** one more test runs `InteractiveSession` against the real use case and checks the claim and its Sources line.
- **Not built:** a progress indicator during the model call, or a message on Ctrl-C (Ctrl-D exits cleanly).
- **Checked by piping input to the real `./ask`.**
  - Suggestion 4 answered "That isn't in my sources." without a model call.
  - Suggestion 1 answered "can't answer right now", because the `.env` key is rejected (401).
