# A fixed pipeline with one model call, not an agent and not a no-LLM search

Every question runs the same steps: retrieve → Input Guardrail (no Entry above the score floor means no model call) → one model call that returns cited claims → Output Guardrail → render. We chose this because the brief grades whether a reviewer can predict the system's behaviour, and a fixed path with one model call has a short, testable list of failure paths and a stable cost and latency per question.

## Considered Options

- **No-LLM search (BM25 plus the matching Quotes, no generation).** It cannot fabricate and costs nothing, so it is the baseline the eval compares against. Rejected as the product because it does not answer: "What have you built from zero?" returns five bullets from three roles, and combining them, choosing what matters and saying "my sources don't cover that" in plain words is the job the brief describes. Generation is allowed back in only because the Output Guardrail checks every claim it makes.
- **Tool-using agent in a bounded loop.** More flexible for multi-part questions, but the model choosing tools adds calls, cost and latency variance and makes behaviour harder to predict. Revisit if the eval shows multi-source questions failing because one retrieval pass misses context.
