---
status: accepted (2026-10-03), designed only
---

# A ten-stage ingestion pipeline with Source connectors

Knowledge enters the system only through one offline pipeline of ten stages: **Discover → Fetch → Snapshot → Extract/Map → Normalise → Validate → Provenance → Diff → Review/Quarantine → Publish**. A **Source Connector** supplies the first four stages for one kind of Source (Discover, Fetch, rendering Snapshot text, Extract/Map). The last six are shared code that never knows which kind of Source it is handling. Adding a database, a website or a LinkedIn export is therefore one new connector, plus one Entry-type schema file if the type is new. Nothing downstream changes: retrieval, the Guardrails, Conflicts and the evaluation all read published Entries. We chose this because hand-copied Sources and a fixed Entry-type list meant every new kind of Source changed the design, and because an auditor must be able to follow any citation back to how its text was fetched. The research is in [docs/research/source-ingestion-pipeline.md](../research/source-ingestion-pipeline.md), second pass.

| # | Stage | Produces | On failure |
|---|---|---|---|
| 1 | Discover | The items to fetch, with change hints (Git blob id, ETag, sitemap `lastmod`) | Fail the Source |
| 2 | Fetch | Bytes for changed items only (conditional GET; Git blob at a pinned commit; read-only `REPEATABLE READ` query) | Retry transient errors, then fail; skip items robots.txt disallows |
| 3 | Snapshot | An immutable, content-addressed folder: original file, rendered `text.txt`, manifest with SHA-256 per file | Fail |
| 4 | Extract/Map | Entry drafts: a deterministic mapper for structured Sources, a model draft for unstructured ones | Quarantine the draft |
| 5 | Normalise | Typed fields made canonical (dates as ISO 8601 `YYYY-MM`, names through `aliases.yaml`). Each Quote is replaced by the exact `text.txt` substring it matches, with its span | Quarantine |
| 6 | Validate | JSON Schema 2020-12 per Entry type, unique ids, `parent` and Conflict and Gold Label references resolve, `text.substring(span) == quote` | Quarantine the Entry; fail the run if a schema or Snapshot is unreadable |
| 7 | Provenance | A PROV-shaped run record using OpenLineage field names: run id, stage, inputs, outputs, model id, prompt hash, connector version | Fail (no lineage, no release) |
| 8 | Diff | Entries added, changed, removed or unchanged against the last release, and which Gold Labels are affected | Fail if an id cited by a Gold Label would disappear |
| 9 | Review/Quarantine | A review queue (pending, approved, rejected) and a quarantine list with reasons | Publish is blocked while anything is pending |
| 10 | Publish | `entries/*.yaml`, a release manifest (version, Snapshot ids, file hashes, previous version) and a git tag `knowledge-<version>`, written all at once or not at all | Fail with no partial write |

**Rules that keep the "word for word" guarantee.** Raw Snapshot bytes and `text.txt` are never edited after they are written. Matching uses a throwaway key (NFKC plus whitespace collapsing) on both sides, and only the exact original substring is ever stored as a Quote. Structured Sources render each row as one fixed-format line, so their Quotes hold by construction.

**Review.** The Subject reviews every model-drafted Entry on a Source's first ingest. After that, only added or changed Entries from the Diff are reviewed, plus any changed Entry a Gold Label cites. Mapper-produced Entries are approved by the mapper's tests.

**Designed, not built** ([ADR 0007](0007-cli-built-service-designed.md)). The exercise has two Sources, so the build keeps ingestion simple: Snapshots committed by hand, a model-drafted Entry file the Subject reviews once, and the Validate checks (schema, ids, `parent`, word-for-word Quote, typed fields) plus the id diff run at every startup. Those are the same checks stage 6 runs, so moving to the pipeline adds connectors and the stages around them without changing the Entry shape or anything downstream. In production the Subject drives the pipeline with `ingest run | review | quarantine | publish | verify`, the service runs `verify` on its release at startup and refuses to start on any mismatch, and the release is baked into the image and ships through the eval-gated canary (ADR 0008).

**Entry types become data in production.** Each type is a JSON Schema file, so a talk or a blog post adds a file, not code. This amends ADR 0001; `role` and `parent` keep their special meaning. The build keeps the fixed type list.

## Considered Options

- **Spring Batch.** It fits high-volume chunked jobs. Here, restart would need a metadata database, the corpus is tens of Entries, and the review gate is a pause of days between two commands, not a step inside a job. We borrow only its skip-listener idea, which becomes the quarantine writer. Any single stage can later be wrapped as a Batch `Step` without changing its contract.
- **Spring AI's ETL chain.** It has no schema, id or state concept, and it is built around chunks (rejected in ADR 0001). Its PDF, HTML and Tika readers are still used inside connectors.
- **The Singer or Airbyte protocol.** It needs Python or Docker and an orchestrator, which breaks the ten-minute setup (ADR 0007). Their ideas are kept: a schema per stream, ids from source keys, saved progress.
- **OpenLineage events.** There is no lineage server to receive them. `provenance.json` uses OpenLineage-compatible names, so emitting real events later is a mapping, not a redesign.

## Consequences

- Six new artifact folders under `knowledge/` (`raw/`, `runs/`, `review/`, `quarantine/`, `releases/`, plus `aliases.yaml`), one new port (`SourceConnector`), and the `ingest` commands.
- The stored Quote is the Snapshot's exact text, which may differ from what the model wrote. The Subject reviews the repaired Quote.
- A knowledge change is a release: reviewed, tagged, baked into an image and canaried like code. Rollback means redeploying the previous image.
- No build time now: the build keeps hand-committed Snapshots. Building the pipeline is the first item in Future Considerations.
