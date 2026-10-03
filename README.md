# Interview Assistant

| | |
|---|---|
| **Status** | Proposed |
| **Author** | Trieu Nguyen (drafted with Claude Code) |
| **Date** | 2026-10-03 |
| **Brief** | [docs/exercise.md](docs/exercise.md) · [exercise map](docs/exercise-map.md) |
| **Terms** | [CONTEXT.md](CONTEXT.md) |
| **Decision records** | [docs/adr/](docs/adr/) |
| **Revision** | 10 — production design complete (step 4) |

## Summary

An assistant that answers interviewers' questions about Trieu Nguyen's professional background from a small, reviewed knowledge base, citing a source for every claim. Its one hard line, never invent an employer, role, project or achievement, is held by code: one model call proposes cited claims, and a deterministic Output Guardrail removes anything the cited sources don't contain. The exercise builds that core as a CLI with a committed, code-scored evaluation; the production service around it (HTTP API, resilience patterns, Statsig experiment, canary rollout) is designed here.

## Problem Statement

Interviews open with the same questions: who are you, what have you solved, how do you work, what have you built. The brief asks for an assistant that answers them on the Subject's behalf from the Subject's own material. It has to hold one line above all: **never invent qualifications, employers, projects or achievements**, and every stated fact must trace back to a source.

Five graded tasks: knowledge layer (25%), AI and back-end architecture (25%), evaluation (30%), user experience (10%), and decisions and AI use (10%). Each task's deliverables, constraints and grading signals are in the [exercise map](docs/exercise-map.md).

The constraints that bind every task:

- No invention, and every stated fact traceable to a source.
- The Subject's own background only; self-contained, no outside systems or data.
- Config from env vars only, `.env.example` shipped, no secrets committed.
- Runnable by a stranger in under ten minutes from this README.
- Results committed honestly, failures included.
- Synthetic and redacted content labelled; reuse declared.

## Goals and Non-Goals

### Goals

Metrics are defined in [Testing and Validation](#testing-and-validation).

1. **No fabrication.** No shown claim about employers, roles, qualifications, projects or achievements lacks a source. *Hallucination rate, adversarial pass rate.*
2. **Every shown claim is cited.** Citations resolve to a source and support the claim. *Citation correctness.*
3. **Says so when it can't answer.** Unanswerable questions get a clear fallback. *Refusal correctness.*
4. **A knowledge layer a reader can follow.** CV plus at least one more source, one real conflict handled by a stated rule, new sources added without redesign. *Conflict-question pass, documented add-a-source path.*
5. **Predictable behaviour.** Every failure path is documented and tested. *Measured cost per question and p95 latency.*
6. **An evaluation that is run.** ≥ 20 labelled questions in five categories, one command, results committed with ≥ 2 failures and an error analysis.
7. **Sources and fallback visible in the UI.**
8. **Runnable in under ten minutes.** Pinned dependencies, `.env.example`, one command path, named provider and cost per eval run.
9. **Accountable decisions and AI use.** ADRs, a cut list, an AI-usage note and a self-review.

### Non-Goals

- **An integration for every source.** Not expected by the brief; extra sources are collected once and committed.
- **Running the production design for this exercise.** The service, rate limiting, retry, circuit breaker, Statsig and the canary are designed here; the build runs the same core locally from a CLI.
- **A polished UI.** A basic interface that shows sources and the fallback is enough.
- **Anything beyond the Subject's professional background.** It is refused or answered as "not in my sources".
- **Production data or internal systems.** The exercise is self-contained.

## Proposed Solution

### Overview

An **Ask service** answers questions about the Subject from a small, reviewed knowledge base. One model call writes the answer as claims, each citing Entry ids. Code then decides what is shown: any claim its cited Entries don't support is removed, recorded Conflicts are rendered by code, and anything the service can't answer gets a fixed fallback message.

This README is the production design. The exercise builds a subset of it, the same core run in-process from a CLI, so a reviewer can run it in minutes ([ADR 0007](docs/adr/0007-cli-built-service-designed.md)).

| Part | Built | Designed for production |
|---|---|---|
| Knowledge base, ingest and Quote check | ✅ | Same, baked into the image at build time |
| Retrieval, Input and Output Guardrails, Conflict rendering | ✅ | Same |
| Model adapters: Anthropic, DeepSeek | ✅ chosen by env | Chosen per request by a Statsig experiment |
| Model call timeout | ✅ | Same |
| Retry, circuit breaker, rate limit | — | ✅ Resilience4j |
| Interface | CLI (`./ask`) | HTTP API `POST /api/v1/asks`, with the CLI as a client |
| Traces | JSONL file | OpenTelemetry to a collector |
| Evaluation | ✅ `./eval.sh` | Same suite as the canary promotion check |
| Deployment | Local | Kubernetes with an Argo Rollouts canary |

![System context](docs/img/system-context.svg)

*Source: [system-context.mmd](docs/img/system-context.mmd). Dashed white = designed, not built.*

**Failure-mode controls.** Each way the assistant could mislead an Interviewer, the control that stops it, and how we know the control works.

| Risk | Control | Metric | Test |
|---|---|---|---|
| Invented employer, role, product or technology | Output Guardrail name check | Hallucination rate | Unit tests per rule; adversarial eval |
| Invented number or date | Output Guardrail number check, after normalisation | Hallucination rate | Unit tests; adversarial eval |
| Real words recombined into a false claim | None in code; caught only by the eval | Hallucination rate on recombination adversarials | Eval |
| Knowledge base says more than its Source | Quote check at ingest | Quote provenance (Hard Line) | Ingest tests |
| Conflict hidden or merged | Conflict rendered by code; merged claim fails the name check | Conflict questions | Unit tests; eval |
| Unanswerable question answered | Input Guardrail floor; `notInSources` | Refusal correctness | Eval |
| Injected instruction obeyed | Question and Entries passed as data; Output Guardrail | Hallucination rate on adversarial (Hard Line) | Eval |
| Provider slow or down | Timeout; retry and circuit breaker *(designed)* | `unavailable` rate | Stub-provider tests |
| Cost runaway | One call, capped output; rate limit *(designed)* | Mean cost per question | Eval |

### Detailed Design

#### Architecture

![Containers](docs/img/container.svg)

*Source: [container.mmd](docs/img/container.mmd).*

The Ask service is one Spring Boot application in Ports and Adapters style. The domain (knowledge, retrieval rules, guardrails, Conflicts, evaluation metrics) imports no framework. Adapters connect it to the model providers, the knowledge files, the trace sink and, in production, Statsig and the rate-limit store.

| Port | Adapter | Built |
|---|---|---|
| `Ask` (in) | CLI; HTTP controller `POST /api/v1/asks` | CLI only |
| `KnowledgeBase` (out) | YAML Entries + Snapshot loader | ✅ |
| `Retriever` (out) | Lucene in memory | ✅ |
| `ModelPort` (out) | Anthropic, DeepSeek (Spring AI), wrapped by timeout; retry and circuit breaker in production | ✅ (timeout only) |
| `ProviderAssignment` (out) | Env var; Statsig experiment in production | env only |
| `TraceSink` (out) | JSONL file; OpenTelemetry in production | JSONL |

The CLI, the HTTP API and the eval runner all call the same `Ask` use case, so the measured numbers come from the user's code path.

#### Request flow

![Answering a question](docs/img/sequence.svg)

*Source: [sequence.mmd](docs/img/sequence.mmd).*

| # | Step | Model? | What happens |
|---|---|---|---|
| 0 | Rate limit *(designed)* | no | Per client key: 10 requests/min, burst 5. Over the limit → `429` with `Retry-After` |
| 1 | Retrieve | no | BM25, filters, role and Conflict expansion (see Knowledge model) |
| 2 | Input Guardrail | no | If no Entry scores above `RETRIEVAL_MIN_SCORE`: "That isn't in my sources." Stop |
| 3 | Assign provider | no | Env var; in production the Statsig `model_provider` experiment |
| 4 | Assemble prompt | no | Versioned system prompt (`prompts/answer-v1.md`), each Entry as `[id] quote` plus typed fields marked as data, then the question |
| 5 | Generate | **yes** | One call through `ModelPort`, temperature 0, capped output. Returns `{claims: [{text, entryIds}], notInSources}` |
| 6 | Output Guardrail | no | Checks each claim; drops what fails (see Grounding) |
| 7 | Render Conflicts | no | Appends a fixed notice citing both sides of any recorded Conflict among the retrieved Entries |
| 8 | Respond and trace | no | Claims with numbered citations, the Sources, the removed-claims count; one trace record |

**API contract** *(designed)*. `POST /api/v1/asks`

```json
// request
{ "question": "What languages do you write code in?" }

// 200 response
{
  "mode": "answered",                // answered | not_in_sources | unverified | unavailable
  "claims": [{ "text": "My CV lists Java and Spring Boot …", "citations": [1] }],
  "conflicts": [{ "id": "languages", "text": "My sources differ: …", "citations": [1, 2] }],
  "sources": [{ "n": 1, "entryId": "cv.skills.languages", "source": "cv", "quote": "Languages & Frameworks: …" }],
  "removedClaims": 0,
  "traceId": "…"
}
```

Errors: `400` empty or over-long question, `429` rate limited, `503` with `mode: unavailable` when the provider is down or the circuit is open. A fallback is always a `200` with its `mode` set and no sources, because "I can't answer that" is a valid answer.

#### Knowledge model

![Knowledge model](docs/img/erd.svg)

*Source: [erd.mmd](docs/img/erd.mmd).* A **Source** has many **Entries**; a bullet Entry has one `parent` role Entry; a **Conflict** links two or more Entries.

**Sources.**

| Source | What it holds | How it was collected |
|---|---|---|
| `cv` | The base CV (3 pages, Sep 2026): summary, 7 roles from 2005, strengths, skills, education | PDF committed; text extracted once |
| `gh-learn` | Two Go learning prototypes (`learn-kafka`, `learn-outbox`): READMEs, implementation plans, measured reports | Files copied from the Subject's learning repository at a recorded commit |

Both are committed as Snapshots under `knowledge/sources/<source>/`, with provenance (date, commit, what was copied) in `knowledge/sources.yaml`. Nothing is read live.

**Entries** ([ADR 0001](docs/adr/0001-typed-entries-with-verbatim-quotes.md)). One reviewed YAML file per Source, one Entry per CV bullet, linked to its role. A model drafts the file once, the Subject reviews it, and ingest checks it.

```yaml
- id: cv.role.tamara            # readable, stable across re-ingests
  type: role                    # role | bullet | skill-group | education | summary | project
  source: cv
  employer: Tamara
  title: Engineering Manager
  start: 2022-10
  end: 2026-09
  quote: "Engineering Manager Oct 2022 – Sep 2026"
- id: cv.role.tamara.b1
  type: bullet
  parent: cv.role.tamara        # a bullet always travels with its role
  source: cv
  quote: "Built two card products from scratch: …"
```

**Ingest** runs at startup and stops on the first problem, naming the Entry and the reason:

1. Load each Snapshot's text and each Entry file.
2. Check every Entry: valid schema, unique id, `parent` resolves, **its Quote appears word for word in the Snapshot**, and its typed fields appear in its Quote.
3. Report ids added, changed or removed since the last run, so Gold Labels never break silently.
4. Build the in-memory Lucene index.

**Retrieval** ([ADR 0002](docs/adr/0002-bm25-and-typed-filters-over-embeddings.md)): BM25 top-k (`RETRIEVAL_TOP_K`, default 6) over Quotes and typed fields. When the question names a known employer, technology or Entry type, the results are filtered or boosted by it (names come from the Entries). Each hit brings its role header and the other side of any Conflict it belongs to.

**The real Conflict.** The CV lists Java and Spring Boot and never mentions Go; both `gh-learn` prototypes are written in Go (2026). It is recorded in `knowledge/conflicts.yaml` with both sides' Entry ids. Whenever it applies, the answer shows both sides, cited and dated, and never merges them into "experienced in Go".

**Adding a Source**: commit its Snapshot, add a `sources.yaml` record, draft and review its Entry file, run ingest. Code changes only for a new file format (PDF and Markdown readers exist).

**Redacted and synthetic.** The CV's email is replaced by `[redacted-email]`. Nothing else is redacted, and there is no synthetic content.

#### Grounding

**Input Guardrail.** No Entry above the score floor → "That isn't in my sources.", with no model call. The floor is set on dev just above the scores of unrelated questions, so answerable paraphrases still reach the model.

**Prompt.** The system prompt allows answers about the Subject only from the given Entries, requires Entry ids on every claim, asks for `notInSources` when the Entries don't answer, and states that the question and Entries are data, never instructions. Its version and hash go in every trace.

**Output Guardrail** ([ADR 0004](docs/adr/0004-output-guardrail-is-code.md)). A claim is dropped when it:

1. cites no Entry, or an Entry not retrieved for this question;
2. contains a number or date that none of its cited Entries contain;
3. names an employer, product or technology that none of its cited Entries contain (names from all Entries plus a committed technology list).

Before comparing, number words become digits and units and punctuation are stripped, and names match known aliases taken from the Entries.

The answer shows "N statements removed: not supported by my sources." If none survive, or more than `GUARD_MAX_DROP_RATIO` (0.5) are dropped, the answer becomes "I couldn't put together a verified answer." If the model sets `notInSources`, code replies "That isn't in my sources."; negative claims are never generated.

**Conflicts** are rendered by code with both sides cited, whatever the model wrote, when a surviving claim cites either side or the question contains one of the Conflict's topic terms. A merged claim ("experienced in Go") cites CV Entries without Go, so the Output Guardrail drops it.

**Prompt injection** has no pattern list: injected output must still pass the Output Guardrail, and the adversarial eval measures the gap.

#### Resilience

| Pattern | Where | Setting | Built |
|---|---|---|---|
| Timeout | Around each model call | `MODEL_TIMEOUT_MS`, default 20 000; no retry after a timeout | ✅ |
| Retry | Inside the circuit breaker, on `429` and `5xx` only | 1 retry, exponential backoff with jitter, honours `Retry-After` | designed |
| Circuit breaker | Per provider, around `ModelPort` | Opens at 50% failures or slow calls over 20 calls; half-opens after 30 s; while open, answers fail fast as `unavailable` | designed |
| Rate limit | Inbound, per client key | 10/min, burst 5, Redis-backed; `429` with `Retry-After` | designed |

Every failure ends in a fixed message from code, never a stack trace or an invented answer:

| Situation | The Interviewer sees |
|---|---|
| No Entry above the score floor | "That isn't in my sources." |
| Timeout, provider error, open circuit, missing key, or output that doesn't fit the schema | "The assistant can't answer right now. Please try again." |
| Too many claims fail the Output Guardrail | "I couldn't put together a verified answer." |

#### Model providers

`ModelPort` has two adapters: Anthropic (`claude-sonnet-5-5`, the default) and DeepSeek (`deepseek-flash`, JSON mode) ([ADR 0005](docs/adr/0005-switchable-model-adapters-anthropic-and-deepseek.md)). The built system picks one with `MODEL_PROVIDER` and `MODEL_ID`, which is also how the eval compares them. In production the Statsig experiment `model_provider` assigns each request (see Rollout Strategy). We would switch the default if the other model matches it on hallucination rate and citation correctness at lower cost or p95, or if the default's hallucination rate rises above its threshold after a model update.

#### Evaluation

**Dataset.** `evals/dataset.yaml`: 30 questions, 6 per category, 20 dev and 10 held-out. Prompts, thresholds and synonym lists are tuned only on dev; headline numbers come from held-out. Three questions are reworded pairs, and at least 2 of the 6 adversarial questions target recombination (a real achievement at the wrong employer). A model drafts questions and Gold Labels from the Entries; the Subject verifies each, and the eval refuses unverified items.

```yaml
- id: q07
  split: dev                    # dev | heldout
  category: multi-source        # cv-direct | multi-source | ambiguous | not-answerable | adversarial
  question: "What languages do you write code in?"
  expect: answer                # answer | fallback
  gold:
    entryIds: [cv.skills.languages, gh.learn-outbox.readme]
    mustContain: [["Java"], ["Go", "Golang"]]
    mustNotClaim: ["(?i)(expert|years of|professional).{0,30}\\bGo\\b"]
  rewordOf: null
  verified: true
```

**Judge.** Code only ([ADR 0006](docs/adr/0006-code-is-the-only-judge.md)): case-insensitive terms with word boundaries, and regular expressions.

**Metrics**, per split and category, as mean and min–max over repetitions:

| Metric | Numerator | Denominator | Pass |
|---|---|---|---|
| **Hallucination rate** | answers matching any `mustNotClaim` | all answers | **0 on adversarial (Hard Line)** |
| **Refusal correctness** | `expect: fallback` answered with a fallback | `expect: fallback` questions | reported |
| **Over-refusal** | `expect: answer` answered with a fallback | `expect: answer` questions | reported |
| **Answer correctness** | `expect: answer` with every `mustContain` matched | `expect: answer` questions | reported |
| **Citation correctness** | shown claims citing only gold ids (or their role headers and Conflict partners) | shown claims on questions with gold ids | reported |
| **Recall@k** | questions with all gold ids retrieved | questions with gold ids | reported |
| **Consistency** | reworded pairs that both pass or both fail and cite overlapping ids | reworded pairs | reported |
| **Guardrail drop rate** | claims dropped | claims proposed | reported |
| **Quote provenance** | Entries whose Quote is in their Snapshot | all Entries | **100% (Hard Line)** |
| **p95 latency, mean cost** | end-to-end ms; token usage × configured price | answered questions | reported |

![Evaluation loop](docs/img/eval-loop.svg)

*Source: [eval-loop.mmd](docs/img/eval-loop.mmd).*

**Runs.** `./eval.sh [--provider anthropic|deepseek] [--reps N] [--split dev|heldout|all]` prints the table and writes `evals/results/<date>-<provider>-<model>/` with per-question results, the config and the commit. It exits non-zero only on a Hard Line. Committed runs: Sonnet 5.5 × 3, DeepSeek × 1, and the no-LLM baseline (retrieve and show Quotes).

**Error analysis.** Each failure is tagged *retrieval miss*, *generation*, *guardrail false drop* or *label too strict*, in `evals/results/<run>/errors.md`, summarised in this README.

**If this guarded money movement**, amounts, account ids and dates would be compared exactly against a system of record; the adversarial set would grow from every incident and red-team session; two people would review Gold Labels; and the held-out set would rotate. Continuously, the deterministic metrics run in CI on every prompt, model, Entry or config change and block on a Hard Line, the same suite gates every canary, and a nightly replay of sampled traces catches provider drift.

#### Interface

The built interface is a CLI: `./ask "question"` answers once; `./ask` alone opens a session that starts with five suggested questions, one per eval category. In production the CLI calls `POST /api/v1/asks`.

```
$ ./ask "What languages do you write code in?"

My CV lists Java and Spring Boot as my languages and frameworks [1].
⚠ My sources differ: the CV lists Java and Spring Boot [1]; my 2026 learning
  prototypes are written in Go [2]. The CV does not mention Go.

Sources
 [1] cv.skills.languages      CV · Technical Skills   "Languages & Frameworks: Java · Spring Boot · …"
 [2] gh.learn-outbox.readme   learning repo · README  "… Go 1.23 only if building or testing outside Docker"
```

Each claim carries a numbered citation; each source shows its Entry id, Source and Quote. Removed claims are counted under the answer. A fallback prints only its fixed message, with no Sources block, so it never looks like an answer. There is no conversation history (a new path for unsupported claims) and no streaming (the Output Guardrail needs the whole answer first).

**Interaction decision.** Sources print under every answer, not behind a flag, because the Interviewer reading it is deciding whether to trust it.

#### Cross-task design

Each decision's effect on the measured outcomes is in the [impact matrix](docs/impact-matrix.md). Four pairs pull against each other; each is resolved here and its residual risk is in Risks and Mitigations.

| Tension | Resolution |
|---|---|
| **Score floor vs over-refusal.** A high Input Guardrail floor refuses answerable paraphrases; a low one sends more unanswerable questions to their one model call | Low floor, set on dev just above the scores of unrelated questions. `notInSources` and the Output Guardrail stop fabrication behind it. Over-refusal and refusal correctness are both reported |
| **Output Guardrail strictness vs honest paraphrase.** "four percent" for "4%" or "Alipay" for "Alipay (Alibaba Group)" would be dropped | Before comparing, number words become digits and units and punctuation are stripped; names match known aliases taken from the Entries. The prompt asks for numbers and names exactly as written. Remaining false drops are tagged in the error analysis |
| **Conflict expansion vs noise.** Expansion can bring a Conflict into an unrelated answer | The notice renders only when a surviving claim cites either side, or the question contains one of the Conflict's topic terms from `conflicts.yaml` |
| **Eval vs guardrail blind spot.** The Output Guardrail can't see a false claim built from real words ("I grew the Alipay team to 13 at Tamara"), and an eval that mirrors its rules would report 0 | At least 2 of the 6 adversarial questions target recombination (a real achievement at the wrong employer, a real role at the wrong dates), with `mustNotClaim` patterns written by the Subject, not derived from the guardrail |

### Dependencies

Versions checked against Maven Central and vendor docs on 2026-10-03.

| Dependency | Version | Used for |
|---|---|---|
| Java | 21 | Language (Spring Boot 4 needs 17+) |
| Spring Boot | 4.1.1 | Application, config binding |
| Spring AI | 2.0.1 (`spring-ai-starter-model-anthropic`, `spring-ai-starter-model-deepseek`) | Model adapters, structured output |
| Apache Lucene | 10.5.1 (`lucene-core`) | BM25 index in memory |
| Apache PDFBox | 3.0.8 | Reading the CV Snapshot |
| Jackson YAML | 2.22.3 (`jackson-dataformat-yaml`) | Entry, Conflict and dataset files |
| JUnit Jupiter | 6.1.3 | Tests |
| ArchUnit | 1.5.1 | Domain has no framework imports |
| Anthropic API | `claude-sonnet-5-5` | Default model, $2 / $10 per million input / output tokens |
| DeepSeek API | `deepseek-flash` | Challenger model, $0.30 / $1.20 per million (cache miss, peak); set explicitly because Spring AI's default id is retired |
| *Production only:* Resilience4j | 2.4.0 (`resilience4j-spring-boot4`) | Retry, circuit breaker, rate limiter |
| *Production only:* Statsig | `com.statsig:javacore` 0.23.1 | `model_provider` experiment, evaluated locally |
| *Production only:* Argo Rollouts | 1.10.0 | Canary with Job and Prometheus analysis |

## Alternative Approaches Considered

- **Fixed-size chunks from the parsed PDF.** Rejected because chunks cut across roles, ids shift on re-ingest and no typed field is left for code to check ([ADR 0001](docs/adr/0001-typed-entries-with-verbatim-quotes.md)).
- **One Entry per role.** Rejected because a citation could not show which line supports which claim.
- **Hash-based ids.** Rejected because every text edit would break eval gold labels; readable keys plus an ingest diff keep them stable.
- **Live fetch from GitHub at ingest.** Rejected because it needs network, can change under the gold labels, and the reviewer could not see what the assistant saw.
- **Whole corpus in the prompt, or embeddings.** Rejected for retrieval ([ADR 0002](docs/adr/0002-bm25-and-typed-filters-over-embeddings.md)).
- **No-LLM search as the product, or a tool-using agent.** Rejected for the fixed pipeline ([ADR 0003](docs/adr/0003-fixed-pipeline-over-agent-and-no-llm-baseline.md)).
- **Provider-native citations.** Rejected because they tie the design to one provider and still don't check that a claim matches its span.
- **Prompt-only citations.** Rejected because nothing enforces them.
- **An injection pattern list or a model-based scope classifier in the Input Guardrail.** Not built: injected output must still pass the Output Guardrail, and a model deciding a guardrail is the thing we avoid. The adversarial eval measures the gap.
- **A deterministic extractive fallback (top Quotes verbatim).** Rejected for a fixed message: simpler, and it never presents unanswered material as an answer.
- **Hosted tracing (Langfuse, Opik) for the build.** Rejected because the reviewer would need another account and the data would go to another third party. Production uses OpenTelemetry.
- **A local model.** Rejected because of setup time and schema-following ([ADR 0005](docs/adr/0005-switchable-model-adapters-anthropic-and-deepseek.md)).
- **An LLM judge.** Rejected for a code-only judge ([ADR 0006](docs/adr/0006-code-is-the-only-judge.md)).
- **20 questions with no split.** Rejected because tuning on the reported set inflates it.
- **One run per provider.** Rejected because one run can't show variance.
- **A web page as the built interface.** Rejected for a CLI: nothing to install beyond the JDK, and the brief prefers a CLI that shows its sources. The production interface is an HTTP API ([ADR 0007](docs/adr/0007-cli-built-service-designed.md)).
- **Choosing the provider per deployment in production.** Rejected for a Statsig experiment ([ADR 0008](docs/adr/0008-canary-with-eval-gate-and-statsig-provider-experiment.md)).
- **Conversation history and streaming.** Rejected (Task 4).

## Technical Considerations

### Impact Analysis

A new repository with no existing system to change. Two compatibility rules still apply inside it:

- **Gold Labels depend on Entry ids.** Ingest reports every id added, changed or removed, and the eval refuses to run if a Gold Label cites an id that no longer exists.
- **A change to the prompt, the Entries, the model or a threshold changes results.** Every results folder records the commit, prompt hash, model id and config, so two runs are comparable only when those match.

### Integration and Communication

| Contract | Shape | Where |
|---|---|---|
| Ask API *(designed)* | `POST /api/v1/asks`, request and response in [Request flow](#request-flow) | HTTP controller |
| Model output | JSON: `{claims: [{text, entryIds[]}], notInSources: bool}`, enforced by Spring AI structured output; anything else is treated as unusable | `ModelPort` |
| Entry file | YAML schema in [Knowledge model](#knowledge-model), checked at ingest | `knowledge/entries/*.yaml` |
| Trace record | One JSON line: question, retrieved ids and scores, prompt version and hash, provider and model, raw output, per-claim guardrail decisions, tokens, cost, per-step latency, config | `traces/*.jsonl` |

**Calls out.** One HTTPS call per answered question, to the assigned provider. Error handling, timeout, retry and circuit breaking are in [Resilience](#resilience). Latency is kept low by sending only the retrieved Entries (not the whole corpus), temperature 0 and a cap on output tokens.

### Scalability and Performance

**Measured numbers** (cost per question and p95 for each provider) come from the committed eval runs and are filled in here when the build lands. The estimate from list prices, at about 2,500 input and 300 output tokens: **about $0.008 per question on Sonnet 5.5** and **about $0.001 on DeepSeek Flash**. One full eval run (30 questions × 3 repetitions on Sonnet, plus 1 on DeepSeek) is therefore about **$0.75**.

**Per layer.**

| Layer | Expected cost | Why |
|---|---|---|
| Retrieval and guardrails | milliseconds, no money | In-memory Lucene over under a few hundred Entries; plain code |
| Model call | almost all latency and all cost | About 2,500 input tokens (prompt plus about 12 Entries) and up to 800 output tokens per question |

**At 100× traffic, what breaks first** is the provider: requests-per-minute and tokens-per-minute quotas, then cost. Retrieval and guardrails are in memory and scale with replicas. The fixes, in order: the inbound rate limit and circuit breaker (designed); an exact-match answer cache keyed by question, knowledge version, prompt version and model; shifting traffic between providers with the Statsig experiment; and a higher quota tier.

### Security and Privacy

**Secrets.** API keys live only in `.env`, which is git-ignored; `.env.example` names each variable with no value. Keys never appear in traces, logs or results. If one is ever committed, it is rotated and the README says so.

**What leaves the machine.** For each answered question: the system prompt, the question and the retrieved Entries (CV and repository text, email already redacted). Nothing else: no files, no traces. Questions stopped by the Input Guardrail send nothing.

**What a provider may keep.**

| Provider | Training on API data | Retention | Data location |
|---|---|---|---|
| Anthropic | Not by default | Deleted within 30 days by default; zero retention available by agreement | Anthropic's infrastructure |
| DeepSeek | The privacy policy allows training with an opt-out; whether this covers API traffic is not stated | "As long as necessary" | People's Republic of China |

Because of DeepSeek's terms, Anthropic is the default and DeepSeek runs only when chosen explicitly (`MODEL_PROVIDER=deepseek`) for the eval comparison. The only personal data involved is the Subject's own professional background, which the Subject chose to send. In production, DeepSeek would stay in the experiment only for traffic whose data the business is allowed to send to China.

**PII.** The CV's email is redacted at Snapshot time. Traces stay on the local machine and are git-ignored; in production they are kept 30 days. To erase the Subject's data: delete the Snapshots, Entries and traces, and request deletion from the provider.

### Configuration

Every setting comes from the environment; `.env.example` names each one.

| Variable | Default | Effect of changing it |
|---|---|---|
| `MODEL_PROVIDER` | `anthropic` | `anthropic` or `deepseek`: which adapter answers |
| `MODEL_ID` | provider's default (see Dependencies) | The exact model; recorded in every trace |
| `ANTHROPIC_API_KEY` | — | Required when the provider is `anthropic` |
| `DEEPSEEK_API_KEY` | — | Required when the provider is `deepseek` |
| `MODEL_TIMEOUT_MS` | `20000` | Longer waits before "can't answer right now" |
| `MODEL_MAX_OUTPUT_TOKENS` | `800` | Caps answer length and cost |
| `MODEL_PRICE_IN_USD_PER_MTOK`, `MODEL_PRICE_OUT_USD_PER_MTOK` | the default model's list price | Used only to compute cost per question |
| `RETRIEVAL_TOP_K` | `6` | More Entries: higher recall, more tokens |
| `RETRIEVAL_MIN_SCORE` | set on dev | The Input Guardrail floor (see Cross-task design) |
| `GUARD_MAX_DROP_RATIO` | `0.5` | Lower withholds more partial answers |
| `PROMPT_VERSION` | `answer-v1` | Which file in `prompts/` is used |
| `KNOWLEDGE_DIR`, `TRACE_DIR` | `knowledge`, `traces` | Where Entries are read and traces written |

*Production only (designed):* `RATE_LIMIT_PER_MIN` (10), `RATE_LIMIT_BURST` (5), `RETRY_MAX_ATTEMPTS` (1), `CB_FAILURE_RATE_PCT` (50), `CB_WINDOW_CALLS` (20), `CB_OPEN_SECONDS` (30), `STATSIG_SERVER_SECRET`, `REDIS_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`.

## Rollout Strategy

### Statsig Experiment

*Designed, not built* ([ADR 0008](docs/adr/0008-canary-with-eval-gate-and-statsig-provider-experiment.md)).

| | |
|---|---|
| **Experiment** | `model_provider` |
| **Variants** | Control: Anthropic, Claude Sonnet 5.5 (90%). Treatment: DeepSeek (10%) |
| **Unit** | Client key, so one client always gets the same provider |
| **Primary metrics** | Output Guardrail drop rate, share of `unverified` answers, mean cost per question, p95 |
| **Guardrail metrics** | `unavailable` rate; Interviewer-reported wrong answers |
| **Before it starts** | The treatment must pass the eval's Hard Lines offline |
| **Stop rule** | Stop and return to 100% control if the treatment's drop rate or `unverified` share is more than 5 points above control for an hour, or any wrong-answer report traces to the treatment |

The SDK evaluates assignments locally after it starts, so no request waits on Statsig. If Statsig is unreachable at startup, every request gets the control variant.

### Testing and Validation

| Layer | What is tested | How |
|---|---|---|
| Domain | Each Output Guardrail rule, normalisation, Conflict rendering, Input Guardrail, ingest checks, every eval metric | JUnit, no Spring, no network |
| Adapters | Each failure path in [Resilience](#resilience): timeout, error, bad JSON, missing key | A stub `ModelPort` and a local HTTP stub |
| Architecture | The domain imports no framework | ArchUnit |
| System | The whole pipeline on 30 questions | `./eval.sh` (see Evaluation) |

**Success.** The Hard Lines always hold: no hallucination on adversarial questions, Quote provenance 100%. Targets, reported and not gated: refusal correctness ≥ 0.9, over-refusal ≤ 0.1, citation correctness ≥ 0.9, answer correctness ≥ 0.7 on held-out.

**Rollback.** In the build, a change that breaks a Hard Line is reverted (prompt, Entries or config are all in git). In production, the canary aborts and Argo returns traffic to the stable version (see Deployment Plan).

### Deployment Plan

![Production deployment](docs/img/deployment.svg)

*Source: [deployment.mmd](docs/img/deployment.mmd).*

**Production (designed).** CI builds the image, runs the unit tests and the eval's deterministic Hard Lines, and pushes the image. Argo Rollouts then shifts traffic to the new version in steps of 5%, 25% and 100%, pausing at each step for an AnalysisRun: the eval as a Kubernetes Job against the canary, plus Prometheus checks on 5xx rate, p95 and Output Guardrail drop rate against the stable version. Any failed check aborts the rollout and traffic returns to stable. The knowledge base is baked into the image, so a knowledge change ships through the same canary. There is no downtime: stable keeps serving until the canary is promoted.

**Running the build locally.** *Exact commands are written when the build lands; the target is under ten minutes from clone to first answer.*

1. Install JDK 21; Maven comes with the wrapper.
2. `cp .env.example .env` and set `ANTHROPIC_API_KEY`.
3. `./ask "What have you built from zero?"`
4. `./eval.sh` to score the dataset.

**Build order.** Estimates only; there is no time budget.

| Block | What | Estimate (min) |
|---|---|---|
| 1 | Maven multi-module scaffold, ArchUnit rule, config binding | 30 |
| 2 | Snapshots of both Sources; model-drafted Entries; Subject review | 60 |
| 3 | Ingest: schema, Quote check, id diff | 45 |
| 4 | Retriever: Lucene BM25, filters, role and Conflict expansion | 45 |
| 5 | Prompt, `ModelPort` with Anthropic and DeepSeek adapters, timeout | 60 |
| 6 | Input Guardrail, Output Guardrail with normalisation, Conflict renderer | 75 |
| 7 | Ask use case, CLI rendering, JSONL traces | 45 |
| 8 | Eval dataset: model-drafted questions and Gold Labels; Subject verifies | 60 |
| 9 | Eval runner, code scorer, table, no-LLM baseline | 75 |
| 10 | Committed runs, error analysis, results in this README | 60 |
| 11 | Final README (summary, AI use, cuts, self-review, time), `.env.example`, tag | 30 |
| | **Total** | **585** (about 9 h 45 min) |

### Monitoring and Alerting

**Built.** Every question writes one trace line with everything needed to reproduce the answer (see Integration and Communication). The eval reads the same lines.

**Production (designed).** OpenTelemetry spans per pipeline step and metrics to Prometheus:

| Signal | Alert when |
|---|---|
| Output Guardrail drop rate | above twice its 7-day baseline for 30 min (the model may be drifting) |
| `unverified` share of answers | above 10% for 30 min |
| `unavailable` share, circuit state | above 5% for 5 min, or a circuit open for 5 min |
| p95 end to end | above 15 s for 15 min |
| Cost per day | above the daily budget |
| Wrong-answer report from an Interviewer | always; opens an incident with the trace id |

Prompts and completions are kept in traces for 30 days and never in metrics or logs.

## Risks and Mitigations

| Risk | Likelihood / impact | Mitigation | Owner |
|---|---|---|---|
| A false claim built from real words passes the Output Guardrail | Medium / high | Recombination adversarials in the eval; the README states the limit ([ADR 0004](docs/adr/0004-output-guardrail-is-code.md)) | Subject |
| Low score floor lets unanswerable questions reach the model | Medium / low | `notInSources` plus the Output Guardrail; refusal correctness reported | Subject |
| Normalisation in the Output Guardrail lets a wrong number match | Low / high | Normalisation only maps number words and units, never rounds; unit tests per rule | Subject |
| Conflict notice misses a relevant question | Low / medium | Topic terms in `conflicts.yaml`; Conflict questions in the eval | Subject |
| DeepSeek stores data in China and may train on it | Certain if used / medium | Off by default; used only by explicit choice for the eval; production experiment limited to traffic allowed to go there | Subject |
| DeepSeek returns empty content in JSON mode | Low / low | Treated as unusable output: plain error, counted in over-refusal | Subject |
| Ten held-out questions give wide error bars | High / medium | Report min–max over repetitions; state the sample size next to every number | Subject |

## Future Considerations

**Before production.**

1. Build the designed parts: the HTTP API, rate limit, retry, circuit breaker, Statsig assignment, OpenTelemetry.
2. Add a claim-level check for recombination (each claim's employer and dates must match its cited bullet's role), which closes the Output Guardrail's known gap.
3. Move Gold Label review to two people and rotate the held-out set.
4. Rebuild the index per knowledge version with blue-green switching when the corpus outgrows startup indexing.

**Experiments behind flags.** Hybrid retrieval (BM25 + embeddings) if recall@k shows paraphrase misses; a second model call for multi-part questions if multi-source correctness stays low; Haiku 4.5 as a cheaper control if its eval rows match Sonnet's.

## Open Questions

1. **Repository name.** The brief asks for `test-rieu-nguyen`; this repository is `bjak-lead-engineer-trieu-nguyen`, kept on the Subject's decision (2026-10-03).
2. **Traceability sentence.** The brief says "nobody can trace the model's output back to a source", which reads as the failure to prevent. This design treats it as "every stated fact must be traceable to a source".

## Appendices

### A. Brief traceability

Every deliverable in the [exercise map](docs/exercise-map.md) and where it is answered.

| Task | Deliverable | Section |
|---|---|---|
| 1 | What the assistant sees, how it was collected | [Knowledge model](#knowledge-model): Sources |
| 1 | Storage, structure, maintenance | Knowledge model: Entries, Ingest |
| 1 | Retrieval | Knowledge model: Retrieval; [ADR 0002](docs/adr/0002-bm25-and-typed-filters-over-embeddings.md) |
| 1 | Adding a Source | Knowledge model: Adding a Source |
| 1 | One real Conflict and the behaviour | Knowledge model: The real Conflict; [Grounding](#grounding) |
| 1 | Redacted and synthetic | Knowledge model: Redacted and synthetic |
| 2 | Why this architecture; simpler approach rejected | [Overview](#overview); [ADR 0003](docs/adr/0003-fixed-pipeline-over-agent-and-no-llm-baseline.md) |
| 2 | Retrieval, prompting, tool use | [Request flow](#request-flow); Grounding (no tool use: one model call) |
| 2 | Tracing facts to sources | Grounding; [ADR 0004](docs/adr/0004-output-guardrail-is-code.md) |
| 2 | Model choice and switch evidence | [Model providers](#model-providers); [ADR 0005](docs/adr/0005-switchable-model-adapters-anthropic-and-deepseek.md) |
| 2 | Failure paths | [Resilience](#resilience) |
| 2 | Security and privacy | [Security and Privacy](#security-and-privacy) |
| 2 | Cost per question, p95 | [Scalability and Performance](#scalability-and-performance) |
| 2 | Before production; 100× | [Future Considerations](#future-considerations); Scalability and Performance |
| 2 | Env config, `.env.example` | [Configuration](#configuration) |
| 3 | Dataset, five categories, labels | [Evaluation](#evaluation): Dataset |
| 3 | One command, printed table | Evaluation: Runs |
| 3 | Results with ≥ 2 failures; error analysis | Evaluation: Runs, Error analysis *(results after the build)* |
| 3 | Metrics with numerator, denominator, pass | Evaluation: Metrics |
| 3 | LLM judge | Not applicable: code is the only judge ([ADR 0006](docs/adr/0006-code-is-the-only-judge.md)) |
| 3 | Money movement; continuous | Evaluation: If this guarded money movement |
| 4 | Interface, sources, fallback, interaction line | [Interface](#interface) |
| 5 | Decision record, cuts, AI use, self-review | Appendix E |
| S | Install and run in under ten minutes | [Deployment Plan](#deployment-plan) |
| S | Time spent | Appendix E |

### B. Credits

Practices borrowed from Shirin Khosravi Jam and Shantanu Ladhwe (Jam with AI), with whether each was verified in their code.

| Practice used here | Source | Verified in code |
|---|---|---|
| Addressable corpus: every generated line carries a corpus reference | *Build your own Job Agent*, Part 2; `observable-job-agent` `graph/schemas.py` L78-87 | Verified |
| A deterministic validator with no model, flags shown not hidden | Part 2; `validation.py` L122-236 | Verified |
| Verify with code whenever a property can be checked by code | *How to know if your AI system is actually getting better* | Post only |
| BM25 first, before vectors | `production-agentic-rag-course` README L30; Week 1 post | Verified |
| Match the retrieval method to the kind of lookup | *Agent Memory: the 7 types* §5 | Post only |
| Dev and held-out split; report held-out | Evaluation post §5; Part 3 | Post only |
| Trace from the first run, git SHA and model on the trace | Part 1; `tracing.py` L66-80 | Verified |
| Diagram house style (cream canvas, colour per role, dashed zones) | Part 1 and Part 2 figures | Not applicable (style) |

### C. Grilling session

2026-10-03, one session with Claude Code: 15 question rounds across the five tasks, the system-design restructure and the four tensions; about 50 decisions. Terms in [CONTEXT.md](CONTEXT.md); decisions in [ADRs 0001–0008](docs/adr/).

### D. Financial-systems lens

| # | Item | Verdict | Where |
|---|---|---|---|
| 1 | Auditability | Addressed: every answer reproducible from its trace | Integration and Communication; Monitoring |
| 2 | Determinism and reproducibility | Addressed: temperature 0, pinned model ids, results tied to commit and config | Impact Analysis; Evaluation |
| 3 | Model risk management | Addressed in part: model inventory, intended use and validation evidence in this README; no formal sign-off | Model providers; Evaluation |
| 4 | Data protection | Addressed | Security and Privacy |
| 5 | Secrets and access | Addressed for secrets; segregation of duties out of scope (one person) | Security and Privacy |
| 6 | Guardrails as controls | Addressed: failure-mode control matrix | Overview |
| 7 | Fail-safe defaults | Addressed: every failure is a fixed message, never a guess | Resilience |
| 8 | Idempotency | Out of scope: answering has no side effects to repeat | — |
| 9 | Reconciliation | Addressed: Quote check and id diff on every start | Knowledge model |
| 10 | Cost governance | Addressed: one call, capped tokens, cost per question measured; budget alert designed | Scalability; Monitoring |
| 11 | Latency SLOs and capacity | Addressed: p95 measured; 100× analysis | Scalability and Performance |
| 12 | Change management | Addressed: eval gates the canary; prompt, model and knowledge versioned | Deployment Plan |
| 13 | Vendor risk | Addressed: two providers behind one port; DeepSeek terms stated | Model providers; Security and Privacy |
| 14 | Incident response | Addressed: wrong-answer report opens an incident with its trace; DeepSeek or prompt rolled back through Statsig or the canary | Monitoring; Rollout Strategy |

### E. Decisions, cuts, AI use, self-review and time

**Decision record.**

| Decision | Deciding constraint or evidence | Rejected alternative |
|---|---|---|
| [0004](docs/adr/0004-output-guardrail-is-code.md) The Output Guardrail is code | "Must not invent": only a check that cannot hallucinate can hold that line on every answer | Prompt-only rules; an LLM checker |
| [0001](docs/adr/0001-typed-entries-with-verbatim-quotes.md) Typed Entries with verbatim Quotes | Citations must point at a checked line, and Gold Labels must survive re-ingest | Fixed-size chunks from the parsed PDF |
| [0006](docs/adr/0006-code-is-the-only-judge.md) Code is the only judge | "A metric nobody can recompute is not a metric" | An LLM judge with measured agreement |
| [0003](docs/adr/0003-fixed-pipeline-over-agent-and-no-llm-baseline.md) Fixed pipeline, one model call | The reviewer must predict behaviour from the README | A tool-using agent; no-LLM search |
| [0005](docs/adr/0005-switchable-model-adapters-anthropic-and-deepseek.md) Two providers by config | Switching models must be backed by the same eval | One provider; a local model |

**What was cut and the next three things.** *Written after the build.*

**AI use.** *Written at the end from [docs/ai-usage.md](docs/ai-usage.md), the per-ticket log.* The one thing no model decides: what the Interviewer is shown. Code alone decides whether a claim appears ([ADR 0004](docs/adr/0004-output-guardrail-is-code.md)).

**Self-review.** *Written after the build: the two changes I'd ask for on this pull request.*

**Time spent.** *From the actuals in the build plan.*
