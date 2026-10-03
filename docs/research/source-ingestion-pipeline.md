# Research: one ingestion pipeline for many Source types

**Date:** 2026-10-03
**Question.** How should the Interview Assistant fetch, validate, transform and save knowledge from many kinds of Source (PDF CV, Git repository files, a relational database, a website, CSV exports such as LinkedIn, later others) into one defined Entry schema, so that a new Source type is a new connector and never a redesign? The constraints are fixed by [ADR 0001](../adr/0001-typed-entries-with-verbatim-quotes.md) and the README "Knowledge model": every Quote must appear word for word in a committed Snapshot, ids are stable, and everything is reproducible and auditable.

Only primary sources (specs, official docs, javadoc) are cited. Where none was found, the text says so.

## 1. Connector and plug-in models for extract

**Airbyte Protocol.** A source implements `spec`, `check`, `discover` and `read(Config, ConfiguredAirbyteCatalog, State)`. Output is `AirbyteMessage`s of type RECORD, STATE, LOG, SPEC, CONNECTION_STATUS, CATALOG and TRACE. The schema is declared per stream in an `AirbyteCatalog`, where each `AirbyteStream` has a `json_schema`. Incremental sync works through STATE checkpoints (STREAM, GLOBAL or LEGACY). ([Airbyte Protocol: Source, Messages, Catalog, State & Checkpointing](https://docs.airbyte.com/platform/understanding-airbyte/airbyte-protocol)). Connectors ship as Docker images and are run as `docker run … read --config … --catalog …`, writing JSON lines to stdout ([Airbyte Protocol Docker Interface](https://docs.airbyte.com/platform/understanding-airbyte/airbyte-protocol-docker)).

**Singer.** A tap is an executable that reads config, state and catalog files and writes SCHEMA, RECORD and STATE messages as one JSON line each to stdout. SCHEMA carries a JSON Schema plus `key_properties` (primary key) and optional `bookmark_properties`. RECORD may carry `time_extracted`. STATE carries an opaque `value` for resuming ([Singer SPEC.md: Output, Input](https://github.com/singer-io/getting-started/blob/master/docs/SPEC.md)).

**Kafka Connect.** A `SourceConnector` "pull[s] data from another system and send[s] it to Kafka". It exposes `taskClass()`, `taskConfigs(int)`, `config()` (a `ConfigDef`) and `version()` ([SourceConnector javadoc](https://kafka.apache.org/39/javadoc/org/apache/kafka/connect/source/SourceConnector.html)). A `SourceTask` implements `start`, `poll`, `stop` and `commit` ([SourceTask javadoc](https://kafka.apache.org/39/javadoc/org/apache/kafka/connect/source/SourceTask.html)). Each `SourceRecord` carries a `sourcePartition` (for example a filename or table) and a `sourceOffset` "which can be used to resume consumption" ([SourceRecord javadoc](https://kafka.apache.org/39/javadoc/org/apache/kafka/connect/source/SourceRecord.html)). Bad records can go to a dead-letter topic (`errors.tolerance`, `errors.deadletterqueue.topic.name`). That queue exists for sink connectors ([KIP-298](https://cwiki.apache.org/confluence/display/KAFKA/KIP-298%3A+Error+Handling+in+Connect)).

**Spring AI ETL.** The pipeline has three functional interfaces: `DocumentReader extends Supplier<List<Document>>`, `DocumentTransformer extends Function<List<Document>, List<Document>>` and `DocumentWriter extends Consumer<List<Document>>`. Readers include JSON (with JSON Pointer), Text, HTML (`JsoupDocumentReader` with CSS selector), Markdown, PDF Page and PDF Paragraph (PDFBox), and Tika. There is no dedicated web or URL crawler: readers take a Spring `Resource`, and the docs warn against building them from user-supplied URLs. Transformers include `TokenTextSplitter`, `ContentFormatTransformer`, and the model-based `KeywordMetadataEnricher` and `SummaryMetadataEnricher`. Writers are `FileDocumentWriter` and `VectorStore`. There is no schema or state concept ([Spring AI ETL Pipeline](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html)).

**Apache Camel.** A component is "a factory and manager of Endpoints". It is discovered through `META-INF/services/org/apache/camel/component/<scheme>`, and Endpoints create Consumers and Producers configured with `@UriEndpoint` and `@UriParam`. The guide does not cover schema declaration ([Writing Components](https://camel.apache.org/manual/writing-components.html)).

**LangChain4j.** It splits loading from parsing. Loaders: FileSystem, ClassPath, Url, AmazonS3, AzureBlob, GitHub, GCS, Selenium, Playwright. Parsers: Text, PDFBox, POI, Tika, Docling, Markdown, YAML. It also has an `HtmlToTextDocumentTransformer` and an `EmbeddingStoreIngestor`. There is no schema or state concept ([LangChain4j RAG tutorial](https://docs.langchain4j.dev/tutorials/rag/)).

### Comparison

| Option | Extension point | Schema declaration | Incremental state | Java / in-process fit | Weight for a tiny corpus |
|---|---|---|---|---|---|
| Airbyte Protocol | Docker image implementing spec/check/discover/read | JSON Schema per stream in catalog | STATE messages (stream/global) | Language-neutral; runs out of process | Heavy (Docker, orchestrator) |
| Singer | Executable tap emitting JSON lines | SCHEMA message (JSON Schema + `key_properties`) | Opaque STATE `value` | Language-neutral; out of process | Medium (subprocess, no platform needed) |
| Kafka Connect | `SourceConnector` + `SourceTask` (Java) | Connect `Schema`/`Struct` | `sourcePartition` / `sourceOffset` | Native Java, but needs a Connect worker and Kafka | Heavy |
| Spring AI ETL | `DocumentReader` / `Transformer` / `Writer` | None (free-form metadata map) | None | Native, already on the stack | Light |
| Apache Camel | Component / Endpoint / Consumer | None in the component model | Per component | Native Java | Medium (routing runtime) |
| LangChain4j | `DocumentLoader` + `DocumentParser` | None | None | Native Java, second AI framework | Light, but duplicates Spring AI |

**Takeaway.** The protocols (Airbyte, Singer, Kafka Connect) agree on three ideas worth copying: a named **stream** with a **declared JSON Schema and primary key**, **records** with an extraction time, and an opaque **state/offset** for resuming. The Java libraries (Spring AI, LangChain4j) give file-format parsers but no schema, ids or state. None of them knows about Quotes.

## 2. Validation against a defined schema

- **JSON Schema 2020-12.** It provides `enum` (6.1.2), `const` (6.1.3), `pattern` (6.3.3) and `required` (6.5.3). `format` is only an annotation unless the format-assertion vocabulary is enabled (7.2.1 vs 7.2.2) ([JSON Schema Validation 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation)). For this system that means date patterns must use `pattern`; `format: date` alone is not enough.
- **networknt json-schema-validator** supports 2020-12. It has a 3.x line for Java 17+ / Jackson 3 and accepts YAML for both schemas and data. Its output formats are Flag, List and Hierarchical ([README](https://github.com/networknt/json-schema-validator)). Entry YAML files can therefore be validated directly against a schema.
- **Jakarta Validation 3.1** validates annotated JavaBeans and records, and needs Java 17+ ([spec page](https://jakarta.ee/specifications/bean-validation/3.1/)). It suits in-code invariants once YAML is bound to records. JSON Schema is better as the language-neutral contract that a connector author reads.
- **Data contracts.** ODCS is a YAML standard with Fundamentals, Schema, Data Quality, SLA, Team and other sections ([ODCS](https://bitol-io.github.io/open-data-contract-standard/latest/)). It is much more than this system needs. Its useful idea is a versioned schema plus quality rules per dataset.
- **Where validation sits.** In the medallion pattern, bronze holds raw data with "minimal validation", and schema validation and deduplication happen on the way to silver ([Databricks medallion](https://docs.databricks.com/aws/en/lakehouse/medallion)). For this system, the parallel is: validate the *Snapshot manifest* at extract time, and validate *Entries* after transform. **Quarantine:** Kafka Connect's DLQ is the primary-source precedent for routing bad records aside instead of failing (KIP-298 above). The current README rule, "stops on the first problem", is the right choice for the startup ingest. An offline drafting step can quarantine instead.

## 3. Provenance and reproducibility

- **PROV-O** models provenance as Entity, Activity and Agent, linked by `wasGeneratedBy`, `used`, `wasDerivedFrom`, `wasAttributedTo` and `wasAssociatedWith`, with `startedAtTime`/`endedAtTime` (W3C Recommendation 2013-04-30, §3.1 Starting Point Terms) ([PROV-O](https://www.w3.org/TR/prov-o/)). Mapped here: a Snapshot is an Entity `wasGeneratedBy` an extract Activity that `used` the live Source; an Entry `wasDerivedFrom` its Snapshot; the drafting model and the reviewing Subject are Agents. `sources.yaml` can carry these fields without adopting RDF.
- **Content hashing.** Git stores every blob under the SHA-1 of `blob <size>\0` + content, so identical content always gets the same key ([Git Internals: Git Objects](https://git-scm.com/book/en/v2/Git-Internals-Git-Objects)). Because Snapshots are committed, the git commit already addresses them by content. A per-file SHA-256 in the manifest makes the check explicit inside the image.
- **Immutable raw zone.** Bronze "maintains the raw state of the data source in its original formats", is append-only and "enables reprocessing and auditing" (Databricks, above). Applied here, a Snapshot is never edited in place. A re-extract produces a new dated Snapshot, and the diff between the two Snapshots drives Entry review.
- **Stable ids.** Singer's `key_properties` is the precedent for ids that come from the source's own keys (Singer SPEC, above). Kafka Connect's partition/offset pair is the precedent for resuming (SourceRecord, above).
- **Text normalisation for verbatim matching.** Under Unicode NFC, canonically equivalent strings have "precisely the same normalized form" ([UAX #15 §1.1, §1.2](https://unicode.org/reports/tr15/)). Normalise Snapshot text and Quotes to NFC before the word-for-word check, so that a PDF ligature or a composed accent cannot cause a false failure.

## 4. Website and database extraction specifics

- **robots.txt (RFC 9309).** User-agent groups are matched case-insensitively (§2.2.1). The most specific (longest-octet) rule wins, and a URL with no matching rule is allowed (§2.2.2). A 4xx response means everything may be fetched; a 5xx means "MUST assume complete disallow" (§2.3.1). A crawler should not use a cached copy for more than 24 hours (§2.4) and must parse at least 500 KiB (§2.5). The protocol is "not a substitute for valid content security measures" (§3) ([RFC 9309](https://www.rfc-editor.org/rfc/rfc9309.html)).
- **Main-content extraction.** jsoup `select()` takes CSS selectors in the style of `querySelectorAll` ([jsoup selector syntax](https://jsoup.org/cookbook/extracting-data/selector-syntax)). Spring AI's `JsoupDocumentReader` exposes this as `selector`, `allElements` and `groupByElement` (Spring AI ETL, above). Tika's `BoilerpipeContentHandler` "uses the boilerpipe library to automatically extract the main content from a web page" ([Tika 2.7.0 javadoc](https://tika.apache.org/2.7.0/api/org/apache/tika/sax/boilerpipe/BoilerpipeContentHandler.html)). For a known personal site, an explicit CSS selector per site is deterministic and reviewable. Boilerpipe is heuristic, so the same page can produce different Snapshot text across library versions.
- **JDBC.** `Connection.setReadOnly(true)` is only "a hint to the driver" ([java.sql.Connection](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html)). PostgreSQL enforces `READ ONLY` (it rejects INSERT, UPDATE, DDL and similar statements). Under `REPEATABLE READ`, every statement sees only rows committed before the transaction's first query. `SERIALIZABLE READ ONLY DEFERRABLE` is "well suited for long-running reports or backups" ([PostgreSQL SET TRANSACTION](https://www.postgresql.org/docs/current/sql-set-transaction.html)). Use a read-only database role as well, because the JDBC flag alone is not a guarantee.
- **Redaction at extract.** None of the cited protocols defines PII redaction. In this design, redaction stays a connector step that runs *before* the Snapshot is written, so the Snapshot (and therefore every Quote) never contains the raw value. This matches CONTEXT.md "Redaction".

## 5. Transform to typed Entries

- **Structured sources (DB, CSV, LinkedIn).** The mapping is deterministic. The connector renders each row to one **canonical text line** in the Snapshot, for example `Position | Tamara | Engineering Manager | 2022-10 | 2026-09`, with fields in a fixed order and escaping. The Entry's Quote is that line, and its typed fields are parsed from it. The Quote check and the "typed fields appear in the Quote" check then hold *by construction*, and the mapper can be unit-tested instead of reviewed Entry by Entry. Ids come from the source key (`linkedin.position.<hash-of-key>` or a readable slug).
- **Unstructured sources (PDF, README, web page).** The model drafts Entries, and code then rejects any Entry whose NFC-normalised Quote is not a substring of the Snapshot. Spring AI's model-based enrichers write *metadata*, not Quotes (Spring AI ETL, above), so they cannot satisfy ADR 0001 on their own.
- **Scaling human review.** I found **no primary guidance** on how much to sample when reviewing LLM-drafted extractions. A secondary summary points to NIST AI 600-1 (MG-3.2-008, human moderation of generated content), but I could not read the PDF to confirm the text. The design therefore has to choose its own rule. Proposed: deterministic mappers are reviewed through their tests; model drafts are reviewed only as a **diff** against the previous Entry file; and changed Entries cited by Gold Labels are always reviewed.
- **LinkedIn export.** LinkedIn's help page confirms the "Download your data" flow (data within minutes or 24 hours, link valid 72 hours) but does **not** state the file format ([LinkedIn help a1339364](https://www.linkedin.com/help/linkedin/answer/a1339364)). The fact that the archive is CSV is unconfirmed.

## Candidate designs

### (a) Connector SPI + raw Snapshot zone + schema-validated Entry zone, all offline before build

- **Components.** A `SourceConnector` port with `id()`, `extract(config) → SnapshotDraft` (files + manifest: SHA-256, extracted-at, source locator, connector version) and `entries(snapshot) → EntryDraft[]`. Connector adapters: `pdf`, `git`, `csv`, `jdbc`, `web`. Each declares its streams and the per-type JSON Schemas it emits. Adapters may use Spring AI readers internally as parsers.
- **Where.** *Fetch* is an `ingest extract <source>` CLI. *Validate 1* checks the manifest and hashes. *Transform* uses the deterministic mapper or a model draft. *Validate 2* runs JSON Schema 2020-12 per Entry type, plus unique ids, `parent` resolution and the NFC Quote check; failures go to `knowledge/quarantine/<source>.yaml`. *Save* commits Snapshot + Entries. The startup ingest re-runs Validate 2 and stops on the first problem, as it does today.
- **Adding a DB or website.** One adapter class, its config schema, and an Entry-type schema if the type is new. The Entry `type` enum becomes a registry of schema files, so a new type adds a file and changes no code.
- **Guarantees.** Quotes hold by construction for structured sources and by the check for unstructured ones. Ids come from source keys, or are kept across re-extracts by matching on Quote hash.
- **Trade-offs.** It adds a small new SPI and an extract CLI. It is all in-process Java and keeps "nothing is read live" in production.

### (b) Spring AI ETL chain (`DocumentReader → DocumentTransformer → DocumentWriter`)

- **Components.** One `DocumentReader` per Source type, a transformer that turns `Document`s into Entries, and an Entry-file `DocumentWriter`.
- **Where.** Validation would have to live in a custom transformer or writer, because the interfaces have no schema, id or state concept. `Document` metadata is an untyped map.
- **Adding a DB or website.** Web is `JsoupDocumentReader` with a selector. DB needs a custom reader, since none exists.
- **Guarantees.** Nothing in the chain enforces them. The Snapshot zone and the Quote check must be added around it anyway.
- **Trade-offs.** It is the least new code for files, but it shapes the pipeline around chunked `Document`s, the exact model ADR 0001 rejected.

### (c) Adopt the Singer (or Airbyte) protocol

- **Components.** Existing taps and connectors emit SCHEMA, RECORD and STATE. A Java "target" writes Snapshots and Entries.
- **Adding a source.** Often zero code if a tap exists. Otherwise, a tap in any language.
- **Guarantees.** `key_properties` gives stable ids, and STATE gives resumption. PDFs and model-drafted Entries do not fit the record model.
- **Trade-offs.** It needs Python or Docker processes and a second toolchain, and Airbyte adds an orchestrator. That is far too heavy for five Sources and hard to justify to a reviewer who must run the project in ten minutes ([ADR 0007](../adr/0007-cli-built-service-designed.md)).

## Recommendation

**Design (a).** It borrows the protocols' proven concepts without their runtime: a declared JSON Schema per stream, a source primary key for ids, and opaque state per Source. Spring AI readers are used only as parsers inside adapters. The pipeline stays offline, so the baked-image, eval-gated canary model is unchanged. The Quote guarantee becomes structural for DB, CSV and LinkedIn data (canonical lines) and stays mechanically checked for PDFs and web pages. Bronze-style immutable Snapshots with hashes plus a PROV-shaped manifest give the auditor a chain from Entry to Snapshot to the extract run. Validation runs in two places: at extract on the manifest, and after transform on Entries, with quarantine offline and fail-fast at startup.

## Open questions

1. May the Entry `type` enum become a schema-file registry, which changes ADR 0001, or should new types still require a code change?
2. Is a DB Source a real target (which engine, and is a read-only role available), or only designed?
3. For the website: a list of fixed URLs with CSS selectors, or crawling? Crawling adds RFC 9309 handling and non-determinism.
4. Review policy: is "diff-only review plus mapper tests" acceptable to the financial-industry reviewer, or must every model-drafted Entry be signed off?
5. Should the manifest record the extract run in PROV terms explicitly (agent, model id, prompt hash), or are the git commit and `sources.yaml` enough?

## Second pass: the ten-stage pipeline (2026-10-03)

**Brief.** The Subject wants ingestion as ten explicit stages: Discover → Fetch → Snapshot → Extract/Map → Normalise → Validate → Provenance → Diff → Review/Quarantine → Publish. Already decided: connectors for PDF (the CV) and Git (learning-repo files) are built; DB (read-only JDBC), website (fixed URLs plus CSS selectors) and CSV/LinkedIn are designed only. The Subject reviews every model-drafted Entry on a Source's first ingest. After that, only added or changed Entries from the Diff are reviewed, and a changed Entry cited by a Gold Label is always reviewed. Structured Sources are covered by mapper tests. Hard constraint: every Quote appears word for word in a committed Snapshot. Citations from the first pass are not repeated.

### Correction to the first pass

§3 above says NFC stops "a PDF ligature" from causing a false Quote failure. **That is wrong.** NFC is canonical composition only (Java [`Normalizer.Form`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/Normalizer.Form.html)). Ligatures such as "ﬁ" are *compatibility* characters, which only NFKC/NFKD fold. UAX #15 §1.2 warns that the K forms "must not be blindly applied to arbitrary text" because they erase formatting distinctions (Figure 6 shows ﬁ → f i) ([UAX #15](https://unicode.org/reports/tr15/)). The design below therefore applies a K-form only to a throwaway *match key*, never to stored text.

### Stage table

| # | Stage | Purpose | Input → output artifact | Built / designed | Failure behaviour | Idempotency | Primary-source basis |
|---|---|---|---|---|---|---|---|
| 1 | Discover | List what exists, with change hints, without fetching content | `sources.yaml` record → `runs/<run>/discovery.json`: items `{locator, etag?, lastModified?, blobId?}` | Built: PDF (one fixed path), Git. Designed: DB, web, CSV | **Fail** the Source (a missing commit, path or URL means bad config) | Deterministic for a pinned commit, fixed URL list or fixed query catalogue | Git `ls-tree` output `mode type object path`; path args are "patterns to match" ([git-ls-tree](https://git-scm.com/docs/git-ls-tree)). Sitemap `lastmod` "must be set to the date the linked page was last modified" ([sitemaps.org](https://www.sitemaps.org/protocol.html)). `DatabaseMetaData.getTables/getColumns/getPrimaryKeys`, which return an empty `ResultSet` when the driver lacks the metadata ([javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/DatabaseMetaData.html)). Airbyte `discover` (first pass) |
| 2 | Fetch | Get bytes for changed items only | discovery → in-memory `FetchResult` (`Fetched(bytes, validators)` or `NotModified`) | Built: PDF, Git. Designed: DB, web, CSV | Transient errors (5xx, 429, timeout): **retry**, then **fail** the Source. A robots.txt disallow: **skip** the item and log it | GET is idempotent (RFC 9110 §9.2.2). A content hash equal to the previous one means no new Snapshot | RFC 9110: `ETag` is "an opaque validator" (§8.8.3), `If-None-Match` uses weak comparison (§13.1.2), and the server "MUST ignore" `If-Modified-Since` when `If-None-Match` is present (§13.1.3), giving `304` (§15.4.5) ([RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html)). `429` "MAY include a Retry-After" ([RFC 6585 §4](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)). Git `<rev>:<path>` names the blob at a commit ([gitrevisions](https://git-scm.com/docs/gitrevisions)) |
| 3 | Snapshot | Freeze the raw bytes and the text every Quote is checked against | FetchResults → `knowledge/raw/<source>/<snapshot-id>/` (`original.*`, `text.txt`, `manifest.json` with SHA-256 per file) | Built (shared) | **Fail** on a hash mismatch or an unwritable store | Snapshot id = SHA-256 of the canonical manifest, so equal content gives the same id and the write is a no-op | OCI descriptor: the digest is "a content identifier, enabling content addressability", written as `algorithm:encoded`, SHA-256 recommended, and content verified against the digest ([OCI descriptor](https://github.com/opencontainers/image-spec/blob/main/descriptor.md)). Git object model (first pass) |
| 4 | Extract/Map | Turn Snapshot text into Entry drafts | `text.txt` → `EntryDraft[]` | Built: PDF (model draft), Git (model draft). Designed: DB/CSV (deterministic mapper), web (model draft) | A draft that cannot be parsed goes to **quarantine** | Mappers are pure. Model drafts are cached by `(snapshot-id, model-id, prompt-sha256)`, so a re-run reuses them | Spring AI readers as parsers (first pass); none for drafting |
| 5 | Normalise | Make typed fields canonical; repair the Quote to an exact span | drafts → `EntryCandidate[]` (each with `quote` + `span`) | Built (shared) | A field that cannot be normalised (for example, an unparseable date) goes to **quarantine** | Pure function of draft + Snapshot + `aliases.yaml` | UAX #15 §1.2 (K forms), §1.4 (forms "not closed under string concatenation"). `\s` is ASCII-only unless `UNICODE_CHARACTER_CLASS` (`\p{IsWhite_Space}`) is set ([Pattern](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html)). RFC 3339 is "a profile of ISO 8601" and its `full-date` (§5.6) needs a day; `YYYY-MM` is valid only in ISO 8601's wider grammar (Appendix A) ([RFC 3339](https://www.rfc-editor.org/rfc/rfc3339.html)) |
| 6 | Validate | Enforce the schema and the hard constraint | candidates → `valid[]` + `invalid[]` (with reasons) | Built (shared) | Per Entry: **quarantine**. Per run (schema file missing, Snapshot unreadable): **fail** | Pure | JSON Schema 2020-12 (first pass); the Quote check is our own rule (ADR 0001) |
| 7 | Provenance | Record who/what produced each artifact | run facts → `runs/<run>/provenance.json` | Built (shared) | **Fail** the run if it cannot be written, because a release without lineage is not publishable | Same run id gives the same file. A new run gets a new UUID | PROV-O (first pass). OpenLineage: Run identified by a UUID `runId`; Job and Dataset have namespace + name; facets extend each ([object model](https://openlineage.io/docs/spec/object-model)); run states START…FAIL ([run cycle](https://openlineage.io/docs/spec/run-cycle)) |
| 8 | Diff | Find what changed against the last release | `valid[]` + previous `knowledge/entries/<source>.yaml` + Gold Labels → `runs/<run>/diff.json` (added / changed / removed / unchanged, plus Gold Label impact) | Built (shared) | Removing an id that a Gold Label cites: **fail** (it needs an explicit retire decision) | Pure. Comparison by id and `sha256(canonical Entry)` | Singer `key_properties` (first pass); content hashing as in Snapshot |
| 9 | Review/Quarantine | Human gate; park bad items | diff + policy → `knowledge/review/<run>/<source>.yaml` (pending / approved / rejected); `knowledge/quarantine/<run>/<source>.yaml` | Built (shared) | Publish is blocked while anything is pending. Quarantined items are never published | Re-running a run keeps earlier decisions, keyed by `(id, contentHash)` | KIP-298 DLQ (first pass). Spring Batch `SkipListener`: called "only once per item", and its common use is to "log out a skipped item, so that another batch process or even human process can be used to evaluate and fix" it ([intercepting execution](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing/intercepting-execution.html)) |
| 10 | Publish | Produce one atomic, versioned knowledge release | approved Entries → `knowledge/entries/<source>.yaml` + `knowledge/releases/<version>.json` + git annotated tag `knowledge-<version>` | Built (shared) | **Fail** with no partial write: temp file then atomic rename; the tag is created last | Same content hash means no new version | Annotated tags carry date, tagger and message; for a published tag "use a different name", never re-tag ([git-tag](https://git-scm.com/docs/git-tag)). OCI annotations `org.opencontainers.image.version` / `.revision` / `.created` (RFC 3339) ([annotations](https://github.com/opencontainers/image-spec/blob/main/annotations.md)) |

### Proposed solution

**Connector vs shared stages.** A connector supplies Discover, Fetch, text rendering and Extract/Map for one Source type. Normalise through Publish is shared code that never knows the Source type.

```java
// port/out: one per Source type (pdf, git built; jdbc, web, csv designed)
interface SourceConnector {
  SourceType type();
  Discovery discover(SourceConfig cfg, Optional<SnapshotManifest> previous);
  FetchResult fetch(DiscoveredItem item, Optional<ManifestItem> previous); // Fetched | NotModified
  String renderText(FetchedItem item);                // deterministic; renderer version goes in the manifest
  List<EntryDraft> extract(Snapshot snapshot);         // mapper, or model drafter via a ModelPort
}
record DiscoveredItem(String locator, Optional<String> etag, Optional<Instant> lastModified, Optional<String> blobId) {}

// shared stages: plain functions over on-disk artifacts
interface SnapshotStore { SnapshotId write(SourceId s, List<FetchResult> r, SourceConnector c); Snapshot read(SnapshotId id); }
interface Normaliser    { NormaliseResult normalise(EntryDraft d, Snapshot s, Aliases a); } // Candidate | Rejected(reason)
interface EntryValidator{ List<Violation> validate(EntryCandidate c, KnowledgeView ctx); }
interface ProvenanceLog { void record(RunRecord run); }
interface EntryDiffer   { EntryDiff diff(List<Entry> previous, List<Entry> next, GoldLabelIndex gold); }
interface ReviewGate    { ReviewQueue open(EntryDiff d, ReviewPolicy p, Optional<ReviewQueue> prior); }
interface Publisher     { Release publish(ReleaseCandidate c); }  // all-or-nothing
```

**On disk** (all committed except `runs/` scratch, which is kept for the audit):

```
knowledge/sources.yaml                       # Source config: type, locator, pinned commit / URL list / query catalogue
knowledge/raw/<source>/<snapshot-id>/        # immutable: original.pdf|*.md|page.html|rows.csv, text.txt, manifest.json
knowledge/runs/<run-id>/                     # discovery.json, diff.json, provenance.json (PROV-shaped)
knowledge/review/<run-id>/<source>.yaml      # pending | approved | rejected, keyed by (id, contentHash)
knowledge/quarantine/<run-id>/<source>.yaml  # item, stage, reason
knowledge/entries/<source>.yaml              # last published Entries (what the service reads)
knowledge/aliases.yaml                       # employer / technology aliases → canonical name
knowledge/releases/<version>.json            # release manifest
```

**Normalise without breaking the verbatim guarantee.** Three rules:
1. The raw Snapshot bytes and `text.txt` are never edited. `text.txt` is produced once, at Snapshot time, by a renderer whose version is recorded in the manifest.
2. Matching uses a *key* function on both sides: `key(x) = collapse(\p{IsWhite_Space}+ → " ", NFKC(x)).strip()`, with an offset map back to `text.txt`. If a model-drafted Quote matches only under the key, Normalise **replaces it with the exact substring of `text.txt` at the mapped span** and stores `span: [start, end]`. The stored Quote is therefore always a raw substring, and Validate checks `text.substring(start, end).equals(quote)`, a stricter check than the README's. NFKC is safe here because the key is never stored. Matching runs on the whole text, not on concatenated fragments (UAX #15 §1.4).
3. Typed fields are normalised separately. Dates become ISO 8601 `YYYY-MM` (reduced precision; *not* RFC 3339 `full-date`, which needs a day), or `YYYY-MM-DD` where the Source gives a day. `present` becomes null. Employer and technology names map through `aliases.yaml` (for example "Tamara Inc." → `Tamara`). The README's "typed fields appear in the Quote" check then compares the *alias set* or the *rendered date* against the Quote's key, not the canonical value.

**Validate** checks, per Entry: JSON Schema 2020-12 for its type; unique id; `parent` resolves; every id in `conflicts.yaml` and every Gold Label resolves; the exact-span Quote check; typed fields appear in the Quote. Startup re-runs exactly this stage and fails fast.

**Diff and review policy.** `contentHash = sha256(canonical YAML of the Entry without provenance fields)`. The ReviewPolicy produces pending items as follows:
- (a) every model-drafted Entry when the Source has no published Entries yet;
- (b) after that, model-drafted Entries that are added or changed;
- (c) any changed Entry cited by a Gold Label, whatever its Source type;
- (d) mapper-produced Entries are auto-approved only if the mapper's test suite passed at the recorded mapper version.

A removed id cited by a Gold Label fails the run until the Gold Label is updated.

**CLI** (offline, the existing CLI app; ADR 0007):

```
ingest run <source>        # Discover → … → Diff, opens the review queue, stops
ingest review <source>     # lists pending; --approve <id>… / --reject <id> --reason …
ingest quarantine <source> # lists parked items and reasons
ingest publish             # refuses if anything is pending; writes the release; tags it
ingest verify <version>    # what the service does at startup; also runs in CI
```

**Release and image.** `releases/<version>.json` holds `version` (monotonic, e.g. `2026.10.03-1`), `gitCommit`, the per-Source `snapshotId`, the `sha256` of every `entries/*.yaml`, `aliases.yaml`, `conflicts.yaml` and schema file, the Entry counts, `diffFrom` (the previous version) and the review run ids. The Docker build copies `knowledge/` plus the chosen release manifest into the image and sets `org.opencontainers.image.version` to the knowledge version. At startup the service reads the manifest, re-hashes every file, re-runs Validate and builds the index; any mismatch stops startup. The image then goes through the existing eval gate and canary (ADR 0008). Rollback means redeploying the previous image. A bad release is superseded by a new version, never re-tagged (git-tag, "On Re-tagging").

**Plugging in a DB Source (designed).** *Discover*: an explicit query catalogue in `sources.yaml` (named SELECTs with an `ORDER BY` primary key). `DatabaseMetaData` is used only to assert that the referenced columns exist, since drivers may return empty metadata. *Fetch*: a read-only role, plus `READ ONLY` and `REPEATABLE READ` (first pass). *Snapshot*: `rows.csv`, and `text.txt` as one canonical line per row. *Extract/Map*: a deterministic mapper, so each Quote is its line by construction. Change detection is the content hash; JDBC has no ETag.

**Plugging in a website (designed).** *Discover*: a fixed URL list, with an optional sitemap `lastmod` as a hint only, and a robots.txt check (RFC 9309, first pass). *Fetch*: a conditional GET with the stored `ETag`/`Last-Modified`, one request at a time with a fixed delay, honouring `Retry-After` on 429/503. *Snapshot*: `page.html`, and `text.txt` rendered from the configured CSS selector. *Extract*: a model draft. Everything from Normalise onwards is unchanged.

### Framework choice: plain Java stages, not Spring Batch

Spring Batch fits a different problem.
- Its core value is chunk-oriented transactions: items are read until the commit interval, then "the entire chunk is written out… and then the transaction is committed" ([chunk-oriented processing](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html)).
- Restart works by reloading the `ExecutionContext` "from the database" for the same `JobInstance` (= Job + identifying JobParameters) ([domain](https://docs.spring.io/spring-batch/reference/domain.html)). But the default `ResourcelessJobRepository` "does not use or store batch meta-data" and does not support restartability, so restart needs `@EnableJdbcJobRepository` and a database ([configuring the repository](https://docs.spring.io/spring-batch/reference/job/configuring-repository.html)).
- Skip limits and `SkipPolicy` cap how many bad items a high-volume step tolerates ([configuring skip](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing/configuring-skip.html)).

Here, the corpus is tens of Entries, so there are no transactions to chunk. Every stage writes a content-addressed artifact, which makes re-running the restart mechanism with no metadata database. And the review gate is a pause of hours or days between two CLI commands, not a step inside a job. Plain functions behind the interfaces above are easier to unit-test, and the reviewer can run them in ten minutes. **Borrowed from Spring Batch:** the `SkipListener` contract (each bad item is reported exactly once, for a human to fix) becomes the quarantine writer, and a per-run quarantine limit fails the run if more than N items are parked. If the corpus ever grows to bulk DB extracts, a stage can be wrapped as a Spring Batch `Step` without changing its contract. Apache Beam and Camel were not researched further: there is no distributed or streaming need, and Camel's routing adds nothing to a linear offline run (first pass).

**Provenance: a PROV-shaped manifest, not OpenLineage (yet).** OpenLineage's model maps cleanly: Run (UUID) ↔ our run id, Job ↔ stage, Dataset ↔ Snapshot or Entry file. Its Java client even has a file transport ("designed mainly for integration testing") ([client configuration](https://openlineage.io/docs/client/java/configuration)). But it has no consumer here, since there is no lineage server. `provenance.json` uses OpenLineage-compatible names (`runId` UUID, `job.namespace/name`, `inputs[]/outputs[]` with namespace + name) and PROV relations, so emitting real events later is a mapping, not a redesign.

### Open questions

1. Snapshots move from `knowledge/sources/<source>/` (README) to `knowledge/raw/<source>/<snapshot-id>/`. Should both CV and `gh-learn` be re-snapshotted under content-addressed ids now, or migrated with their current text unchanged?
2. Is the Quote-repair step acceptable? Normalise silently replaces a model's near-match Quote with the exact Snapshot span. The Subject still reviews it, but the stored Quote is not what the model wrote.
3. Release versioning: a date-plus-counter (`2026.10.03-1`) or semver? Should the knowledge version be independent of the application version in the image tag?
4. What is the per-run quarantine limit that fails the run instead of parking items (e.g. more than 20% of a Source's drafts)?
5. For the designed website connector, what fixed politeness delay should apply between requests? Neither RFC 9309 nor sitemaps.org defines a crawl rate, so this is our own choice.
