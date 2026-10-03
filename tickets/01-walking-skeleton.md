# 01: Walking skeleton

**What to build:** An Interviewer runs `./ask "<question>"` and gets an answer with numbered Sources, end to end through every layer, on a few hand-written fixture Entries. The project builds as a Maven multi-module (core, adapters, CLI app, eval app) on Java 21 and Spring Boot 4.1.1, and the core imports no framework. The `Ask` use case retrieves with in-memory Lucene BM25, makes one Anthropic call (Sonnet 5.5, temperature 0, capped output) through `ModelPort` and gets back claims JSON. It keeps only claims that cite a retrieved Entry, renders the answer with its Sources and appends one trace line.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [x] `./ask "<question>"` prints claims with numbered citations and a Sources block (Entry id, Source, Quote) from fixture Entries
- [x] The model returns `{claims: [{text, entryIds}], notInSources}`; a claim citing nothing, or an Entry not retrieved for this question, is not shown
- [x] One JSON trace line per question (question, retrieved ids and scores, prompt version and hash, model id, raw output, kept and dropped claims, tokens, latency) in a git-ignored folder
- [x] An architecture test fails if the core module imports Spring, Spring AI, Lucene or Jackson
- [x] `.env.example` names `ANTHROPIC_API_KEY`, `MODEL_PROVIDER`, `MODEL_ID`, `MODEL_TIMEOUT_MS`, `MODEL_MAX_OUTPUT_TOKENS`, `RETRIEVAL_TOP_K`; no real key committed
- [x] A test through the `Ask` use case with a stub `ModelPort` covers the kept and dropped claim
- [x] AI-usage log entry appended
