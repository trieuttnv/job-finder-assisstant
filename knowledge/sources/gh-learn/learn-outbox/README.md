# Transactional Outbox Prototype (`learn-outbox`)

The producer-side mirror of `learn-kafka/`. A service must atomically commit an
order to Postgres **and** guarantee an `OrderCreated` event reaches Kafka, with
no distributed transaction between the two systems. This is a rig that makes the
three irreducible tensions measurable:

| Tension | How the rig exposes it |
| --- | --- |
| Atomicity vs. no 2PC | Order + outbox row in one `pgx` transaction; the writer role imports no Kafka client |
| Publish latency vs. poll frequency vs. DB load | `RELAY_POLL_INTERVAL` against a p99 < 2s target, poll-query duration histogrammed |
| Table growth vs. query performance | Ballast grows `outbox` to 500K+ published rows; poll latency measured under plain vs. partial index |

## Prerequisites

- Docker Engine with Docker Compose v2
- `make`
- Go 1.23 only if building or testing outside Docker

## Roles

| `ROLE` | What it does |
| --- | --- |
| `order-writer` | One transaction per tick: insert `orders` + insert `outbox`. No `kafka.Writer`. |
| `relay` | Poll `outbox`, publish to Kafka, mark published. `RELAY_CLAIM_MODE=none\|skip-locked\|lease`. |
| `verifier` | Consume the topic, append every delivery to `published_events`, count duplicates. |
| `ballast` | One `INSERT ... SELECT FROM generate_series` to grow the table to `BALLAST_ROWS`. |
| `reconciler` | Query the tables, write `outbox_report.md`. |

## Quick start

```bash
make up          # kafka, postgres, one relay (claim=none), verifier, monitoring
make write       # order-writer: 50/15s -> 500/30s -> 50/120s (override with PHASES=)
make verify      # lost=0, extra_publishes=0 with one relay
make latency     # p99 < 2s
```

`make write PHASES="50:10,500:30"` (or with 's' suffix `50:10s,500:30s`, or JSON `PHASES='[{"rate":500,"seconds":30}]'`)
to customize traffic phases formatted as `<rate>:<seconds>`.

## The experiment

```bash
make up-2relays          # turn on relay-b; duplicates start immediately under claim=none
make mode-skip-locked    # concurrency duplicates -> 0
make relay-failpoint     # crash between produce and mark: duplicates return (still at-least-once)
make mode-lease          # decouple lock hold time from broker latency

make ballast             # 500K already-published rows
make poll-explain        # EXPLAIN (ANALYZE, BUFFERS) on the exact claim query
make index-plain         # swap to a plain (published_at, id) index, rerun load
make index-partial       # restore the partial index

make cleanup-sweep       # retention-based delete; table shrinks, poll p99 recovers
make report              # writes outbox_report.md
```

See `IMPLEMENTATION_PLAN.md` for the full sequence and the column decisions.

## Monitoring

- **Grafana** <http://localhost:3000> (`admin` / `admin`) — **Transactional
  outbox** dashboard: publish latency p99 against the 2s line, poll duration
  split by `index_kind`, backlog during the burst, per-relay publish rates, and
  Postgres panels for the live invariant row and outbox table/index size.
- **Kafka UI** <http://localhost:8080>
- **Prometheus** <http://localhost:9090>

## What the prototype answers

1. **Same-transaction proof** — the writer constructs no Kafka client;
   `WRITER_FAIL_BETWEEN_WRITES=true` panics between the two inserts and leaves
   zero orphans.
2. **Duplicate mechanism** — measured counts under all three claim modes.
   `skip-locked` is the recommended default; `lease` when broker latency is high.
3. **Growth** — the plain-vs-partial index delta, plus the cleanup trade-off
   (`delete` loses the audit trail, `sweep` with retention is the recommendation).
4. **Do consumers still need idempotency?** — yes, unchanged. Outbox makes the
   database write atomic; it does not make delivery exactly-once. The failure
   point moved from "DB committed but the Kafka call was lost" to "Kafka
   published but the outbox row was not marked".
5. **CDC comparison** — reading the WAL removes the poll query but not the
   duplicates, and trades query tuning for replication-slot management.

## Useful commands

```bash
make build | up | up-2relays | down
make write | ballast
make mode-none | mode-skip-locked | mode-lease
make cleanup-none | cleanup-delete | cleanup-sweep
make index-plain | index-partial
make recreate-relays
make relay-b-kill | relay-b-restart | relay-failpoint
make duplicates | backlog | latency | throughput | poll-explain | verify | report
make cpu | lag | graphs | ui | logs | psql | clean-db
```

`make cpu` (or `make cpu STREAM=1`) displays real-time CPU % and memory metrics for active relay workers.
`make recreate-relays POLL_INTERVAL=50ms BATCH_SIZE=500` (or `RELAY_POLL_INTERVAL=50ms RELAY_BATCH_SIZE=500`) to recreate relay containers with updated poll interval and batch size.
`make clean-db` truncates all application tables (`orders`, `outbox`, `published_events`, `outbox_size_samples`) and restarts their identity sequences without tearing down containers.
`make down` removes the local Docker volumes, including all Kafka and Postgres
data.