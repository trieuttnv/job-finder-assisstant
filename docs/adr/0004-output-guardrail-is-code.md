# The Output Guardrail is code, and only code decides what is shown

The model proposes claims, each citing Entry ids; a deterministic Output Guardrail with no model drops any claim that cites nothing, cites an Entry that was not retrieved, or contains a number, date, employer or technology its cited Entries do not contain. We chose code over prompting or an LLM checker because the brief's failure case is a fabricated qualification, and only a check that cannot itself hallucinate can hold that line for every answer.

## Consequences

- Honest paraphrases that change a number's form ("four percent" for "4%") are dropped. We accept noisy over silent, and the eval reports the drop rate.
- A false claim built only from words that appear in the cited Entries can pass. The eval's groundedness metric measures what remains.
