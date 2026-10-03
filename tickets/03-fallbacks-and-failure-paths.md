# 03: Fallbacks and failure paths

**What to build:** Every way an answer can fail ends in a fixed message written by code, never a stack trace or an invented answer. If no Entry scores above the configured floor, the Interviewer sees "That isn't in my sources." and no model is called; the same message appears when the model sets `notInSources`. A timeout, a provider error, output that doesn't fit the schema (including empty content) or a missing API key gives "The assistant can't answer right now. Please try again."; anything unexpected gives "Something went wrong. Please try again." with the trace id. A fallback prints no Sources block.

**Blocked by:** 01 (Walking skeleton).

**Status:** ready-for-agent

- [ ] No Entry above `RETRIEVAL_MIN_SCORE` → "That isn't in my sources.", and the stub `ModelPort` is never called
- [ ] `notInSources: true` → "That isn't in my sources." written by code; the model's text is not shown
- [ ] Model call exceeding `MODEL_TIMEOUT_MS` → "can't answer right now", no retry
- [ ] Provider error, unparseable or empty output, missing key (detected at startup) → "can't answer right now"
- [ ] Unexpected exception → "Something went wrong. Please try again." with the trace id
- [ ] No fallback output contains a stack trace, provider error text or model output
- [ ] One test per path through the `Ask` use case with a stub `ModelPort`
- [ ] AI-usage log entry appended
