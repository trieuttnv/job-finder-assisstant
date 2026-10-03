# 07: DeepSeek adapter: the paid vs open-weights experiment

**What to build:** The Subject can run the same prompt, Entries and questions on a paid model (Anthropic Sonnet 5.5) and an open-weights model (DeepSeek Flash), chosen by `MODEL_PROVIDER` and `MODEL_ID`. The DeepSeek adapter uses JSON mode with the model id set explicitly, and the prompt is verified against both models (JSON mode needs the word "json" in the prompt). Every trace records the provider, the raw output, tokens, cost (token usage × configured price) and per-step latency, so each model's behaviour can be checked beyond its guardrail-filtered answer.

**Blocked by:** 03 (Fallbacks and failure paths).

**Status:** ready-for-agent

- [x] `MODEL_PROVIDER=deepseek` answers through the DeepSeek adapter with no code change
- [x] DeepSeek empty content or schema break → "can't answer right now" (as in ticket 03)
- [ ] The same prompt file works for both providers; a manual check of raw output from each is recorded in the AI-usage log
- [x] Traces include provider, model id, raw output, tokens, cost and latency per pipeline step
- [x] `.env.example` adds `DEEPSEEK_API_KEY` and the price variables
- [x] AI-usage log entry appended

## Comments

**2026-10-04, Claude Code (implementation).** Built and tested against local stubs. One box is still open: the manual check of real raw output from each provider. On 2026-10-04 the Subject chose to skip real provider calls, and the `.env` Anthropic key returns 401. Ticket 11's runs will do this check.

- **`DeepSeekModelAdapter`** sends `POST /chat/completions` through the JDK's HTTP client. Each request:
  - uses JSON mode (`response_format: json_object`)
  - sets the model id explicitly
  - uses temperature 0 and capped `max_tokens`
  - has a per-call timeout and no retry
- **Unusable output.** Empty content, non-JSON, a missing claims list or a claim without text is `MODEL_OUTPUT_UNUSABLE`, and the raw text is kept in the trace. A timeout is `MODEL_TIMEOUT`; HTTP errors are `MODEL_UNAVAILABLE`. A blank `DEEPSEEK_API_KEY` is detected at startup, and no request is sent.
- **Why not Spring AI.** Its DeepSeek module would add WebFlux and a retry template that would have to be switched off. The Anthropic adapter is unchanged.
- **Same prompt for both providers.** `prompts/answer-v1.md` already says "Reply with JSON only, matching this shape", which DeepSeek's JSON mode requires and which shows it the claims shape that it can't enforce.
- **Configuration.** `MODEL_PROVIDER=deepseek` selects the adapter. An empty `MODEL_ID` means the provider's default (`claude-sonnet-5-5`, `deepseek-flash`). A `MODEL_ID` of the other provider stops startup. `.env.example` names `DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL` and both price variables, and leaves `MODEL_ID` empty.
- **Unusable output.** When the output is unusable, the trace keeps the raw text, the tokens billed and their cost, plus the provider and model id. A truncated reply is named in the failure (`finish_reason: length`). `PromptContractTest` checks that every committed prompt says "json" and shows the claims shape.
- **Traces** now record:
  - **Cost:** tokens × price. Sonnet 5.5's list price is $2 / $10 per MTok; with no price set, the cost is null rather than a guess.
  - **Latency per step:** retrieval, model, guardrail and total.
- **End-to-end check.** `MODEL_PROVIDER=deepseek` answered "When did you work at Atlassian?" through a local stub of the DeepSeek API, with no code change.
