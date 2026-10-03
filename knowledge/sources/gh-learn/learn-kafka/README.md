# Kafka Order Event Fanout Prototype

A runnable local demonstration of Kafka fanout: one `OrderStatusChanged` topic feeds four independent consumer groups with deliberately different latency, batching, ordering, and failure characteristics.

## Prerequisites

- Docker Engine with Docker Compose v2
- `make`

No local Kafka, Postgres, or Go installation is required to run the containers. Go is only needed to build the binary outside Docker.

## Quick start

Start Kafka, Postgres, all consumers, Prometheus, and Grafana:

```bash
make up
```

Run the producer profile:

```bash
make produce
```

The producer sends 50 events/second, bursts to 500 events/second for 30 seconds, then returns to 50 events/second. Every order emits:

```text
CREATED → PAID → FULFILLING → SHIPPED → DELIVERED
```

### Customizing Producer Phases

You can customize the production phases via `PHASES` using the compact `rate:seconds` syntax or JSON:

```bash
# Run only a burst phase (e.g. 500 events/sec for 30s)
make produce PHASES="500:30"

# Custom multi-phase scenario (100/s for 10s -> 1000/s for 20s -> 50/s for 60s)
make produce PHASES="100:10,1000:20,50:60"

# JSON format
make produce PHASES='[{"rate":500,"seconds":30}]'
```

Monitor consumer groups while it runs:

```bash
make lag
make graphs
make ui
```

- **Grafana**: Available at <http://localhost:3000> (`admin` / `admin`). Open **Kafka fanout / Order fanout experiment** to see consumer lag, notification p99 latency, processing rate, live business invariant checks, search index status distributions, customer fraud profiles, and Kafka partition load balancing.
- **Kafka UI**: Available at <http://localhost:8080> to inspect topics, partitions, messages, and consumer groups.
- **Prometheus**: Available at <http://localhost:9090>.

## Run the business invariant reconciliation

To evaluate business invariants across all 4 consumer groups and export a detailed Markdown audit report:

```bash
make reconcile
```

This runs the reconciliation container, compares `producer_audit` against all downstream tables (`order_notification_dispatches`, `analytics_order_status_rows`, `search_recent_orders`, `fraud_customer_state`), and generates `reconciliation_report.md` with:
- Executive pass/fail summary
- Producer baseline throughput and status breakdown
- Notification dispatch latency percentiles and delivery status counts
- Analytics Kafka partition distributions and ingestion pipeline lag
- Search index order state distribution and monotonic sequence validation
- Customer fraud scoring state distribution and chronological ordering check

## Run the crash-recovery experiment

While `make produce` is running, kill analytics and restart it after five seconds:

```bash
make analytics-kill
sleep 5
make analytics-restart
```

Wait for analytics lag to reach zero, then validate the sink:

```bash
make verify-analytics
```

The final result must report `missing = 0` and `duplicates = 0`. Before analytics has drained, `missing` simply represents records still waiting to be processed.

To prove the crash window after a successful database write but before Kafka offset commit, run:

```bash
make analytics-failpoint
```

That process exits immediately after its analytics insert. Restart the normal analytics service with `make analytics-restart`; replayed records are harmless because Postgres enforces `UNIQUE (order_id, status)` and the insert uses `ON CONFLICT DO NOTHING`.

`make unsafe-demo` is intentionally unsafe: it commits the offset before writing to Postgres and then crashes. It exists to demonstrate the gap that commit-before-write can create. Reset the local environment with `make down` and start again with `make up` after using it.

## What the prototype demonstrates

| Consumer group | Behavior | Expected outcome |
| --- | --- | --- |
| `order-notifications` | 5–10 ms simulated delivery, persists to `order_notification_dispatches` | Low notification latency, idempotent dispatch history (`UNIQUE(order_id, status)`) |
| `order-search-indexer` | Flushes batches of 100 or every 2 seconds | Batch writes with safe replay after an uncommitted crash |
| `order-fraud-scoring` | 200 ms synchronous call per assigned partition | Burst lag grows, then drains at the 50/sec normal rate |
| `order-analytics` | Flushes 50 records or every 2 seconds | At-least-once Kafka delivery with exactly-once effect in Postgres |

All four consumers read the same 20-partition `order-status-changed.v1` topic but use separate consumer groups. A slow or stopped group builds only its own lag; it does not move the committed offsets of another group.

Kafka preserves record order inside a partition, not across the whole topic. The producer keys every event by `customer_id`, so all of a customer's events map to one partition. Fraud processes each assigned partition serially and stores its per-customer state in Postgres, preserving per-customer processing order across restarts and rebalances.

Twenty partitions are deliberate: fraud can process roughly 5 events/sec per partition because of its 200 ms call, giving about 100 events/sec total. That is above the 50/sec steady rate, but below the 500/sec burst, making the lag-and-recovery behavior observable.

## Useful commands

```bash
make build                 # Build consumer and producer images
make up                    # Start the complete stack
make produce               # Run the full traffic profile (baseline -> burst -> recovery)
make produce PHASES="500:30" # Run only a burst phase (or custom phases)
make reconcile             # Run reconciliation and export reconciliation_report.md
make lag                   # Describe every consumer group's lag
make graphs                # Show Grafana URL
make ui                    # Show Kafka UI URL
make logs                  # Follow consumer logs
make verify-analytics      # Compare producer audit records with analytics rows
make verify-notifications  # Compare producer audit records with notification dispatches
make down                  # Stop containers and delete local volumes/data
```

`make down` removes the local Docker volumes, including all Kafka and Postgres data.

