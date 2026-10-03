# 07: DeepSeek adapter: the paid vs open-weights experiment

**What to build:** The Subject can run the same prompt, Entries and questions on a paid model (Anthropic Sonnet 5.5) and an open-weights model (DeepSeek Flash), chosen by `MODEL_PROVIDER` and `MODEL_ID`. The DeepSeek adapter uses JSON mode with the model id set explicitly, and the prompt is verified against both models (JSON mode needs the word "json" in the prompt). Every trace records the provider, the raw output, tokens, cost (token usage × configured price) and per-step latency, so each model's behaviour can be checked beyond its guardrail-filtered answer.

**Blocked by:** 03 (Fallbacks and failure paths).

**Status:** ready-for-agent

- [ ] `MODEL_PROVIDER=deepseek` answers through the DeepSeek adapter with no code change
- [ ] DeepSeek empty content or schema break → "can't answer right now" (as in ticket 03)
- [ ] The same prompt file works for both providers; a manual check of raw output from each is recorded in the AI-usage log
- [ ] Traces include provider, model id, raw output, tokens, cost and latency per pipeline step
- [ ] `.env.example` adds `DEEPSEEK_API_KEY` and the price variables
- [ ] AI-usage log entry appended
