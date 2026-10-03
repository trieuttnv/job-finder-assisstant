# Two model providers behind one port, chosen by config

The answer step calls a model through one port with two adapters: Anthropic (Claude Sonnet 5.5) and DeepSeek. `MODEL_PROVIDER` and `MODEL_ID` choose which one runs, so the same eval can compare them on groundedness, cost and p95 without code changes. Sonnet 5.5 is the default because schema-following and combining sources matter more here than price. DeepSeek is the cheaper challenger. Both see the question and retrieved Entries, so both are named in Security and Privacy with their retention terms.

## Considered Options

- **One provider only.** Simpler, but the brief asks what evidence would make us switch models, and that needs a second model runnable on the same eval.
- **A local model (Ollama).** Nothing leaves the machine, but the reviewer could not install and run it in under ten minutes, and small local models follow a claim schema less reliably.
