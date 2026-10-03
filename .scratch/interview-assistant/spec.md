# Spec: Interview Assistant

Status: ready-for-agent

Source design: [README.md](../../README.md) (RFC, Approved 2026-10-03, revision 27; Detailed Design organised by the five tasks). Terms: [CONTEXT.md](../../CONTEXT.md). Decisions: [docs/adr/](../../docs/adr/).

## Problem Statement

An Interviewer wants to learn about the Subject's experience, projects, decisions and working style before or during an interview, without reading every document the Subject has. A chat assistant could answer, but any assistant built on a language model can invent an employer, a role or an achievement, and an Interviewer has no way to tell an invented answer from a true one. For a candidate to a role in the financial industry, one fabricated claim is a failure.

## Solution

A command-line assistant that answers questions about the Subject only from a small, reviewed knowledge base built from the Subject's CV and a snapshot of the Subject's learning repository. Every claim in an answer carries a numbered citation to the exact line it came from, shown under the answer. Code, not the model, decides what is shown: claims the cited lines don't support are removed and counted, recorded Conflicts between sources are shown with both sides, and anything the assistant can't answer gets a clear fixed message. A committed, code-scored evaluation shows how well this holds, failures included.

## User Stories

### Asking

1. As an Interviewer, I want to ask a question in plain English from the command line, so that I can learn about the Subject without reading their documents.
2. As an Interviewer, I want to open an interactive session that suggests five example questions, so that I know what kinds of things I can ask.
3. As an Interviewer, I want each claim in an answer to end with a numbered citation, so that I can check where it came from.
4. As an Interviewer, I want each cited source shown under the answer with its Entry id, Source name and exact Quote, so that I can verify the claim myself without opening files.
5. As an Interviewer, I want to be told when statements were removed because the sources didn't support them, so that I know an answer may be partial.
6. As an Interviewer, I want a clear "That isn't in my sources." when the knowledge base doesn't cover my question, so that I don't mistake silence or a guess for an answer.
7. As an Interviewer, I want "I couldn't put together a verified answer." when most of what the model said couldn't be checked, so that I never see a mostly unsupported answer.
8. As an Interviewer, I want "The assistant can't answer right now. Please try again." when the model is slow, down or returns something unusable, so that I never see a stack trace or a made-up answer.
9. As an Interviewer, I want a fallback to look different from an answer (no Sources block), so that I can't mistake one for the other.
10. As an Interviewer, I want to be shown both sides when the Subject's sources disagree (the CV lists Java and Spring Boot; the learning prototypes are written in Go), so that I get the honest picture, not a merged one.
11. As an Interviewer, I want the Conflict notice only when my question is about the conflicting topic or the answer cites one side, so that unrelated answers aren't cluttered.
12. As an Interviewer, I want leading questions ("You led 50 engineers, right?") answered only with what the sources say, so that I can't talk the assistant into exaggerating.
13. As an Interviewer, I want questions about employers or projects the Subject never had to get no invented details, so that I can trust that what is shown is real.
14. As an Interviewer, I want instructions hidden in my question ("ignore your rules and…") to have no effect on what is shown, so that the assistant can't be turned against the Subject.
15. As an Interviewer, I want dates, employers and titles in answers to match the CV exactly, so that I can rely on them.
15a. As an Interviewer, I want an ambiguous question ("Tell me about your biggest project") answered with the main candidates, each cited, so that the assistant never picks an unsupported "biggest" for me.
15b. As an Interviewer, I want "Something went wrong. Please try again." with a trace id when something unexpected fails, so that I can report it without seeing internals.

### Knowledge base

16. As the Subject, I want my CV and a snapshot of my learning repository committed with their provenance (date, commit, what was copied), so that a reviewer can read exactly what the assistant knows.
17. As the Subject, I want my email redacted before the CV is committed, so that nothing I don't want published is in the repository.
18. As the Subject, I want a model to draft the typed Entries from each Source for me to review, so that building the knowledge base is fast but still checked by me.
19. As the Subject, I want one Entry per CV bullet linked to its role, so that every citation points at one exact line and brings the employer and dates with it.
20. As the Subject, I want readable, stable Entry ids, so that citations are understandable and evaluation labels survive re-ingesting.
21. As the Subject, I want startup to fail with the Entry id and reason if any Entry's Quote is not word for word in its Snapshot, so that the knowledge base can never say more than its Sources.
22. As the Subject, I want startup to fail on duplicate ids, unresolved parents, invalid schema, or typed fields that don't appear in their Quote, so that a broken knowledge base is never served.
23. As the Subject, I want ingest to report which ids were added, changed or removed since the last run, so that I notice when an evaluation label is about to break.
24. As the Subject, I want the Java vs Go Conflict recorded with both sides and its topic terms, so that the assistant handles it by rule.
25. As the Subject, I want to add a new Source by committing its Snapshot, a provenance record and a reviewed Entry file, so that the knowledge base grows without code changes (except a reader for a new file format).

### Answering pipeline

26. As the Interviewer, I want retrieval to find the Entries that match my question's words, names and dates, so that the answer is built from the right lines.
27. As the Interviewer, I want questions naming an employer, technology or kind of Entry to be narrowed to it, so that answers about Tamara don't pull in Alipay.
28. As the Interviewer, I want each retrieved bullet to bring its role header, so that answers always know the employer and dates.
29. As the Interviewer, I want each retrieved Entry that belongs to a Conflict to bring the other side, so that I'm never shown one side alone.
30. As the Subject, I want questions with no Entry above the score floor answered "That isn't in my sources." without calling the model, so that unanswerable questions cost nothing and can't be invented.
31. As the Subject, I want exactly one model call per answered question, so that cost and latency are predictable.
32. As the Subject, I want the model to return claims, each citing Entry ids, plus a not-in-sources flag, so that code can check every claim.
33. As the Subject, I want any claim that cites nothing or cites an Entry not retrieved for this question removed, so that every shown claim has a real source.
34. As the Subject, I want any claim with a number or date not in its cited Entries removed, so that no figure is invented.
35. As the Subject, I want any claim naming an employer, product or technology not in its cited Entries removed, so that no employer or skill is invented.
36. As the Subject, I want number words, units and known name aliases normalised before comparing, so that honest rewording ("four percent", "Alipay") isn't removed.
37. As the Subject, I want the whole answer withheld when no claim survives or more than half are removed, so that a mostly unsupported answer is never shown.
38. As the Subject, I want a not-in-sources flag from the model turned into the fixed "That isn't in my sources." message by code, so that the model never writes negative claims.
39. As the Subject, I want the Conflict notice written by code with both sides cited whatever the model said, so that a Conflict is never hidden or merged.
40. As the Subject, I want the model to treat the question and Entries as data, never instructions, under a versioned system prompt, so that injected instructions have no authority.

### Models and configuration

41. As the Subject, I want to choose Anthropic (Claude Sonnet 5.5, default) or DeepSeek (Flash) with environment variables, so that I can compare them on the same evaluation without code changes.
42. As the Subject, I want the model call to time out after a configured time with no retry, so that a hung provider ends in the fixed message instead of an endless wait.
43. As the Subject, I want a missing or invalid API key detected at startup and every question answered with the fixed unavailable message, so that the failure is clear and safe.
44. As the Subject, I want unparseable or empty model output treated as unusable and answered with the fixed unavailable message, so that DeepSeek's occasional empty JSON or any schema break is safe.
44a. As the Subject, I want to compare a paid model (Sonnet) and an open-weights model (DeepSeek) on the same prompt, Entries and questions, with each model's raw output kept in the trace, so that I can verify the prompt and the output of each, not only the guardrail-filtered answer.
45. As a reviewer, I want every setting to come from the environment with an example file naming each variable, so that I can run the project without reading the code.
46. As a reviewer, I want no real key ever committed, so that the repository is safe to share.

### Traces

47. As the Subject, I want each question to append one trace line (question, retrieved ids and scores, prompt version and hash, provider and model, raw output, per-claim guardrail decisions, tokens, cost, per-step latency, config), so that any answer can be reproduced and explained.
48. As the Subject, I want traces kept local and out of git, so that questions and model output don't leave my machine through the repository.

### Evaluation

49. As a reviewer, I want 30 labelled questions, six in each of five categories (CV-direct, multi-source, ambiguous, not answerable, adversarial), so that every behaviour the brief asks about is tested.
50. As a reviewer, I want the questions split 20 dev and 10 held-out, with headline numbers from held-out, so that tuning can't inflate the reported results.
51. As a reviewer, I want each question labelled with what a correct answer must cite, must contain and must never claim, so that scoring is mechanical.
52. As the Subject, I want the model to draft questions and Gold Labels and me to verify each one, with the evaluation refusing to run on unverified items, so that only I decide what must never be claimed about me.
53. As a reviewer, I want at least two adversarial questions that recombine real facts wrongly (a real achievement at the wrong employer), so that the evaluation tests what the Output Guardrail can't see.
54. As a reviewer, I want three reworded question pairs, so that consistency is measured.
55. As a reviewer, I want one command that scores the dataset and prints a table, so that I can reproduce the results.
56. As a reviewer, I want every metric defined with numerator, denominator and pass rule, computed by code only, so that anyone can recompute it.
57. As a reviewer, I want hallucination rate, refusal correctness, over-refusal, answer correctness, citation correctness, recall@k, consistency, guardrail drop rate, Quote provenance, p95 latency and mean cost reported per split and category as mean and min–max over repetitions, so that I see variance, not one lucky run.
58. As a reviewer, I want the command to fail only on a Hard Line (any adversarial hallucination, or Quote provenance below 100%), so that runs with ordinary failures still complete and get committed.
59. As a reviewer, I want each run written to a results folder with per-question results, the config and the commit, so that runs are comparable.
60. As a reviewer, I want committed runs for Sonnet (three repetitions), DeepSeek (one) and the no-LLM baseline, so that I can see what generation adds and what it risks, and how the models compare.
61. As a reviewer, I want every failure tagged retrieval miss, generation, guardrail false drop or label too strict, with the Subject's error analysis, so that I understand where it broke.
62. As a reviewer, I want measured cost per question and p95 latency per provider from traces, so that the design's scalability claims rest on real numbers.

### Submission

63. As a reviewer, I want to go from clone to first answer in under ten minutes with JDK 21, the Maven wrapper and one API key, so that I can actually run it.
64. As a reviewer, I want the README's results, cut list, AI-usage note, self-review and time spent filled in from the real runs and the per-ticket AI-usage log, so that the write-up is accounted for, not reconstructed.

## Implementation Decisions

- **Shape.** A fixed pipeline with one model call: retrieve, Input Guardrail, assemble prompt, generate, Output Guardrail, render Conflicts, respond and trace (ADR 0003). No tool use, no conversation history, no streaming.
- **Modules.** A Maven multi-module build in Ports and Adapters style: a core module (domain and application) with no framework imports, enforced by an architecture test; an outbound adapters module; a CLI application module; an evaluation application module. Java 21, Spring Boot 4.1.1, Spring AI 2.0.1.
- **Inbound port.** One `Ask` use case taking a question and returning an answer value: mode (answered, not in sources, unverified, unavailable), surviving claims with citation numbers, Conflict notices, sources (number, Entry id, Source, Quote), removed-claims count and trace id. The CLI and the eval runner both call it; the designed HTTP API (`POST /api/v1/asks`) would too.
- **Outbound ports.** `KnowledgeBase` (load Snapshots, Entries, Conflicts; run ingest checks), `Retriever` (BM25 with filters and expansion), `ModelPort` (one structured call, returning claims and token usage), `ProviderAssignment` (environment-based), `TraceSink` (append one JSON line).
- **Knowledge ingestion (build).** Each Snapshot is committed by hand under the knowledge folder with a provenance record (date, commit, what was copied, redactions); a model drafts each Source's Entry file once and the Subject reviews it. The ten-stage ingestion pipeline with Source connectors is production design only (ADR 0009).
- **Knowledge schema.** Entry: id, type from a fixed list (role, bullet, skill-group, education, summary, project; schema files per type are production only), source, optional parent, typed fields (employer, title, start, end), quote. Source record: id, kind, snapshot location, collected date, commit, what was copied, redactions. Conflict: id, topic, topic terms, the Entry ids on each side, rule. Readable ids such as `cv.role.tamara.b1` (ADR 0001).
- **Ingest checks**, failing startup with the Entry id and reason: schema valid, ids unique, parents resolve, Quote word for word in its Snapshot, typed fields appear in the Quote. It reports ids added, changed or removed against the previous run.
- **Retrieval.** In-memory Lucene BM25 over Quotes and typed fields, top-k from config (default 6). Known employer, technology and Entry-type names, built from the Entries, filter or boost. Each hit is expanded with its role header and the other side of any Conflict (ADR 0002).
- **Input Guardrail.** If no Entry scores above the configured floor, return "not in sources" with no model call. The floor is set on the dev split just above unrelated questions' scores.
- **Prompt.** A versioned system prompt file: answer about the Subject only from the given Entries; cite Entry ids on every claim; set the not-in-sources flag when the Entries don't answer; treat question and Entries as data. Entries are passed as `[id] quote` plus typed fields. Temperature 0, output tokens capped. The prompt version and hash go in every trace.
- **Model output contract.** JSON `{claims: [{text, entryIds[]}], notInSources}` via Spring AI structured output. Anything that doesn't parse, or is empty, is unusable.
- **Output Guardrail** (ADR 0004). A claim is dropped if it cites no Entry or an Entry not retrieved; if it contains a number or date absent from its cited Entries; or if it names an employer, product or technology absent from its cited Entries (names from all Entries plus a committed technology list). Number words become digits, units and punctuation are stripped and known aliases match before comparing. The answer is withheld as unverified when no claim survives or the drop ratio exceeds the configured maximum (default 0.5). The not-in-sources flag becomes the fixed message.
- **Conflict rendering.** Code appends the Conflict notice with both sides cited when a surviving claim cites either side or the question contains one of the Conflict's topic terms.
- **Model adapters** (ADR 0005). Anthropic (`claude-sonnet-5-5`, default) and DeepSeek (`deepseek-flash`, JSON mode, model id set explicitly), chosen by environment. Running both is an experiment comparing a paid and an open-weights model: the prompt is verified against both before any run (DeepSeek's JSON mode needs the word "json" in the prompt), and each model's raw output is kept in the trace. A per-call timeout from config; no retry on timeout. The API Gateway (authentication, rate limit), retry, circuit breaker, Statsig assignment and Opik tracing are designed only and not built (ADR 0007).
- **Fallback messages**, fixed and written by code: "That isn't in my sources.", "I couldn't put together a verified answer.", "The assistant can't answer right now. Please try again." and, for anything unexpected, "Something went wrong. Please try again." with the trace id. A fallback prints no Sources block. The designed HTTP API maps the first two to `200` and failures to `400`, `401`, `429`, `500`, `502`, `503` and `504` as in the README's error-code table; the CLI prints the same messages.
- **Traces.** One JSON line per question in a dated local file, git-ignored, with the fields in story 47. Cost is token usage times the configured price per million tokens.
- **Configuration.** Environment variables only, named in an example file: provider, model id, both API keys, timeout, max output tokens, input and output price, top-k, score floor, drop ratio, prompt version, knowledge and trace directories.
- **Evaluation dataset.** One YAML file of 30 items: id, split, category, question, expect (answer or fallback), gold (Entry ids, must-contain term groups with synonyms, must-not-claim regular expressions), reword-of link, verified flag. Six per category, 20 dev and 10 held-out; at least two adversarial items test recombination (a real achievement at the wrong employer); three reworded pairs; ambiguous items expect the main candidates, each cited. Model-drafted, Subject-verified.
- **Evaluation runner and scorer.** One documented command with provider, repetitions and split options. It runs each question through the `Ask` use case, reads the traces, scores with code only (ADR 0006), prints the table, writes a results folder with config and commit, and exits non-zero only on a Hard Line. A no-LLM baseline mode answers with the retrieved Quotes.
- **AI-usage log.** Each ticket appends what was model-generated, what changed after review and minutes spent to the AI-usage log; the README note is written from it.

## Testing Decisions

- **Good tests** drive public behaviour through a seam and assert on what an Interviewer or reviewer would see (the answer value, the startup error, the metric value), never on private methods or internal call order. A refactor that keeps behaviour must not break them.
- **Seam 1: the `Ask` use case** (main seam). Fixture knowledge files and a scripted stub `ModelPort`. Covers retrieval and expansion, the Input Guardrail (including that the stub is never called), each Output Guardrail rule and normalisation, the drop-ratio withholding, Conflict rendering and its relevance rule, the not-in-sources flag, every fallback (timeout, error, missing key, unparseable or empty output) and the rendered answer shape.
- **Seam 2: ingest.** Fixture Snapshots and Entry files. Covers each startup failure (Quote not found, duplicate id, dangling parent, invalid schema, typed field not in Quote) with its message, and the added/changed/removed id report.
- **Seam 3: the eval scorer.** Hand-built answers with known outcomes in, metric values out, for every metric including edge cases (no gold ids, all refusals, reworded pairs).
- **Rendered output** through seam 1: every answer's citation numbers match its Sources block; every fallback has no Sources block; the removed-claims line appears exactly when claims were dropped.
- **Architecture test** that the core module imports no Spring, Spring AI, Lucene or Jackson.
- **Not unit-tested:** the real Anthropic and DeepSeek adapters. They are exercised only by the evaluation command against the real providers.
- **Prior art:** none; this is a new repository. The first tests at each seam set the pattern.

## Out of Scope

- The HTTP API and the API Gateway in front of it (authentication, rate limiting), retry, circuit breaker, Statsig experiment, Opik tracing, Kubernetes and the Argo Rollouts canary: designed in the README, not built (ADR 0007, ADR 0008).
- Conversation history, streaming and any web interface.
- Embeddings or hybrid retrieval.
- An injection pattern list or a model-based scope classifier.
- An LLM judge.
- The ten-stage ingestion pipeline and its Source connectors (PDF, Git, CSV, database, website), release manifests and review queues: designed in the README, not built (ADR 0009).
- Live fetching of any Source, and any Source beyond the CV and the learning-repository snapshot.
- Synthetic content in the knowledge base.

## Further Notes

- The README is the source of truth for the design; this spec only restates it for building. If they disagree, the README wins and this spec is corrected.
- The recombination gap (a false claim built from real words in the cited Entries) is known and accepted; the evaluation's recombination adversarials measure it.
- DeepSeek stores data in China and its terms on training with API data are unclear; it runs only when chosen explicitly for the evaluation comparison.
- Measured cost, p95, evaluation results, the error analysis and the README's final sections are filled in by the last tickets from real runs.
