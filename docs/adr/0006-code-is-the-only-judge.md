# Code is the only judge in the evaluation

Every eval metric is computed by code from Gold Labels written as checkable items: Entry ids to cite, terms an answer must contain (with listed synonyms), and patterns it must never claim. No model scores the eval. We chose this because the brief says "a metric nobody can recompute is not a metric", and a model judge would itself need validating against our labels, adding cost and a second instrument that can drift.

## Considered Options

- **An LLM judge for answer correctness**, prompt committed and agreement with the Subject's labels reported. It catches correct paraphrases that a term list misses, at the cost of judge calls and a judge we would have to trust.

## Consequences

- A correct answer worded outside the synonym list scores as a miss. The error analysis tags these as "label too strict" rather than hiding them, and the synonym lists grow only from dev-split failures.
