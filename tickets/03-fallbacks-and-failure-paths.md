# 03: Fallbacks and failure paths

**What to build:** Every way an answer can fail ends in a fixed message written by code, never a stack trace or an invented answer. If no Entry scores above the configured floor, the Interviewer sees "That isn't in my sources." and no model is called; the same message appears when the model sets `notInSources`. A timeout, a provider error, output that doesn't fit the schema (including empty content) or a missing API key gives "The assistant can't answer right now. Please try again."; anything unexpected gives "Something went wrong. Please try again." with the trace id. A fallback prints no Sources block.

**Blocked by:** 01 (Walking skeleton).

**Status:** ready-for-agent

- [x] No Entry above `RETRIEVAL_MIN_SCORE` → "That isn't in my sources.", and the stub `ModelPort` is never called
- [x] `notInSources: true` → "That isn't in my sources." written by code; the model's text is not shown
- [x] Model call exceeding `MODEL_TIMEOUT_MS` → "can't answer right now", no retry
- [x] Provider error, unparseable or empty output, missing key (detected at startup) → "can't answer right now"
- [x] Unexpected exception → "Something went wrong. Please try again." with the trace id
- [x] No fallback output contains a stack trace, provider error text or model output
- [x] One test per path through the `Ask` use case with a stub `ModelPort`
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).**

- Input Guardrail in `AskQuestionService`: no retrieved Entry at or above `RETRIEVAL_MIN_SCORE` gives `NOT_IN_SOURCES` with no model call. The floor is **provisional at 0.5**. On the real CV, five unrelated questions retrieve nothing (score 0); the weakest relevant one ("Tell me about your biggest project") scores 1.45. Ticket 09 sets it on the dev split.
- Fixed messages live on `AnswerMode` (domain), so the CLI and the designed API show the same text. A new `ERROR` mode covers anything unexpected; the CLI appends the trace id.
- `AnthropicModelAdapter` reports `MODEL_TIMEOUT` (an `InterruptedIOException` or `TimeoutException` anywhere in the cause chain) separately from `MODEL_UNAVAILABLE`, so the designed API can map it to 504. A blank `ANTHROPIC_API_KEY` is detected when the adapter is built: startup logs one warning, and every call fails as `MODEL_NOT_CONFIGURED` without a request. `application.yml` now defaults the key to empty instead of failing startup on an unresolved placeholder.
- Every fallback still appends its trace line, with `mode` and `failure` (error code or exception type, and message). Failure text goes only to the local trace, never to the Interviewer.
- Seen while measuring: "Where did you study?" retrieves `cv.role.alipay-2017.b8`, not `cv.education`, because "study" isn't in the Quote. Recall is ticket 05's job.
