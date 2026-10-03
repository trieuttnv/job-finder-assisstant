# Typed Entries with verbatim Quotes, not fixed-size chunks

The knowledge base is a reviewed YAML file of typed **Entries** per **Source**: one per CV bullet, linked to its role, each with a readable stable id and the verbatim **Quote** it came from. A model drafts the Entries once, the Subject reviews them, and ingest fails if any Quote does not appear word for word in its Snapshot. We chose this over automatic fixed-size chunking because the brief's one hard line is "never invent": a citation must point at a precise, human-checked statement, typed fields (employer, dates) let code verify claims instead of a model, and readable ids keep eval gold labels stable across re-ingests.

## Considered Options

- **Parse the PDF and split every N words.** No manual step, but chunks cut across roles, ids shift on every re-ingest, and nothing typed is left to check.
- **Markdown per Source, chunked by heading.** Readable, but citations point at whole sections and dates stay free text.

## Consequences

- Adding a Source costs a drafting and review pass, which is cheap for a CV-sized corpus and does not scale to thousands of documents (see Future Considerations).
- The Quote check proves an Entry is in its Source; it does not prove the Source is true.
