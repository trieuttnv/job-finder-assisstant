# The Output Guardrail is code, and only code decides what is shown

The model proposes claims, each citing Entry ids; a deterministic Output Guardrail with no model drops any claim that cites nothing, cites an Entry that was not retrieved, or contains a number, date, employer or technology its cited Entries do not contain. We chose code over prompting or an LLM checker because the brief's failure case is a fabricated qualification, and only a check that cannot itself hallucinate can hold that line for every answer.

## Consequences

- Number words, units and separators are normalised on both sides ("four percent" and "4%" are both 4), and known aliases match (`knowledge/names.yaml`). Other honest paraphrases may still be dropped: we accept noisy over silent, and the eval reports the drop rate.
- A "name" is any known employer, product or technology (from the Entries and `knowledge/names.yaml`) or any capitalised word that doesn't start a sentence, so an employer no list contains is caught. This also drops some honest claims; the eval's "guardrail false drop" tag measures it.
- The answer is withheld as "I couldn't put together a verified answer." when no claim survives or more than `GUARD_MAX_DROP_RATIO` (0.5) are dropped, so a mostly unsupported answer is never shown in part.
- A false claim built only from words that appear in the cited Entries can pass. The eval's groundedness metric measures what remains.
