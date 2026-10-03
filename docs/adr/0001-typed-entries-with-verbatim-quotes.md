# Typed Entries with verbatim Quotes, not fixed-size chunks

The knowledge base is cut at fact boundaries, not by length. Each **Entry** is one role, one CV bullet linked to its role, one skill group, one education line or one project. It has a readable stable id, typed fields (employer, title, start, end) and the verbatim **Quote** it came from. A model drafts the Entries once, the Subject reviews them, and ingest fails if any Quote does not appear word for word in its Snapshot. The brief's one hard line is "never invent": a citation is only as precise as the thing it points at, and only stable ids keep eval Gold Labels and Conflict records valid across re-ingests.

## Considered Options

- **Fixed-size splitting (about 300–500 tokens with overlap) into a vector store.** No curation, scales to any corpus, handles paraphrase well. Rejected for three reasons, each visible on the real CV:
  - **Merged facts pass the Output Guardrail.** A window spanning the end of the Tamara role and the start of the Alipay role holds both "Tamara" and "100+ banks", so the fabrication "At Tamara, I integrated 100+ banks" cites one chunk whose words all check out. With one Entry per bullet, the same claim cites an Alipay bullet that doesn't contain "Tamara", and is dropped.
  - **Exact facts become generated.** "How long at Tamara?" would be read from text by the model instead of computed by code from `start` and `end`.
  - **Ids drift.** Re-splitting an updated CV moves the boundaries, which silently breaks every Gold Label and Conflict record that points at a chunk.
- **One Entry per role.** Fewer Entries to review, but a long cited Entry "supports" any number anywhere in the role, which weakens the Output Guardrail.
- **Markdown per Source, chunked by heading.** Readable, but citations point at whole sections and dates stay free text.

Jam with AI's `beginner-local-rag-system` takes the fixed-size route (300 words, 100 words of overlap) and attaches no source name to its chunks, so its answers cannot be cited (`.claude/skills/grill-exercise/references/jam-with-ai.md` §3).

## Consequences

- Adding a Source costs a drafting and review pass: about 60 Entries for the CV plus one repository. That is cheap at this size; the production ingestion pipeline ([ADR 0009](0009-ten-stage-ingestion-pipeline-with-source-connectors.md)) adds deterministic mappers for structured Sources and diff-only review, so review cost scales with change, not size.
- Paraphrase recall is the weak spot. If recall@k measures short, embeddings can be added over the same Entries later; ids, Guardrails and Gold Labels stay unchanged.
- In production, Entry types become one JSON Schema file per type (amended by ADR 0009); the build keeps a fixed list.
- The Quote check proves an Entry is in its Source; it does not prove the Source is true.
