# Transactional Outbox Prototype — Reconciliation Report

**Generated At:** `2026-09-12T09:36:16Z`  
**Lost events:** `0` → ✅ PASS

## 1. Executive summary

| Metric | Value |
| :--- | :--- |
| Orders committed | `1496` |
| Outbox rows (current) | `1496` |
| Events published (recorded) | `1496` |
| Distinct `event_id`s | `1496` |
| Outbox rows with duplicate publishes | `0` |
| Extra publishes (duplicates beyond the first) | `0` |
| **Lost events** (`orders` with no published event) | `0` → ✅ PASS |

---

## 2. Duplicate publishes

| `outbox_row_id` | `event_id` | Publishes | Relays (in publish order) |
| :--- | :--- | :--- | :--- |
| — | — | — | _none_ |

_Total outbox rows published more than once: `0` (showing up to 20)._

---

## 3. Duplicates by phase

| Phase | Publishes | Extra publishes | Duplicate rate |
| :--- | :--- | :--- | :--- |
| `baseline` | `1496` | `0` | `0.00%` |

---

## 4. Publish latency

SLO: p99 commit-to-publish latency `< 2s`.

| Scope | p50 | p95 | p99 | max | Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Overall | `0.189s` | `0.281s` | `0.294s` | `0.302s` | ✅ PASS |
| Phase `baseline` | `0.189s` | `0.281s` | `0.294s` | `0.302s` | ✅ PASS |
| Phase `burst` | — | — | — | — | _no data_ |
| Phase `recovery` | — | — | — | — | _no data_ |

---

## 5. Poll degradation (plain vs partial index)

| Table size | Index | p50 poll | p99 poll | Samples |
| :--- | :--- | :--- | :--- | :--- |
| ~1K | `partial` | `0.713 ms` | `1.121 ms` | `16` |

---

## 6. CDC comparison

Reading the WAL (e.g. Debezium) removes the poll query entirely, so the
table-growth degradation in §5 disappears and publish latency stops being a
poll-interval trade-off.

It does **not** remove duplicates. The connector's own offset commit is a second
non-atomic write, so a connector crash after produce replays — the same
at-least-once boundary the relay has, moved into the connector.

It trades a query-tuning problem for an operational one: replication-slot
management. A stalled slot pins WAL and can fill the disk.

---

_Reconciliation completed in `26ms`._
