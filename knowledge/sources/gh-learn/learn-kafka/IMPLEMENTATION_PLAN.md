# Kafka Order Event Fanout Prototype

## Design

- One 20-partition `order-status-changed.v1` topic and four independent consumer groups: `order-notifications`, `order-search-indexer`, `order-fraud-scoring`, and `order-analytics`.
- Records are keyed by `customer_id`. Kafka preserves append order within a partition, and every event for a customer maps to the same partition. Fraud processes each assigned partition serially, so it sees each customer in produce order.
- Twenty partitions support the fraud bottleneck: 200 ms synchronous work gives about 5 events/sec/partition or 100 events/sec total. A 500/sec, 30-second burst creates lag; the 50/sec normal rate leaves roughly 50/sec of drain capacity.
- Postgres holds fraud state, simulated search state, analytics rows, and a producer audit table. Analytics has a database-enforced unique `(order_id, status)` constraint.

## Runtime experiment

1. Run `make up`, then `make produce`.
2. Run `make lag` during and after the 500/sec phase. Fraud should accumulate lag and later drain; other groups are isolated.
3. During production, run `make analytics-kill`, wait five seconds, then `make analytics-restart`.
4. Run `make verify-analytics`. After consumers drain, `missing` and `duplicates` must both be zero.
5. Use `make analytics-failpoint` to crash immediately after the analytics database insert and before offset commit. Replay must produce duplicate no-ops, not duplicate rows.
6. `make unsafe-demo` exists only to demonstrate why committing before the database write loses rows when killed.

Grafana at `http://localhost:3000` shows consumer-group lag and notification p99 latency. Kafka exporter provides lag independently for each group; an analytics failure must not change notification lag or latency.
