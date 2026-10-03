# Kafka Fanout Prototype — Reconciliation & Data Insights Report

**Generated At:** `2026-09-02T06:56:03Z`  
**Reconciliation Duration:** `301ms`  
**Overall Reconciliation Status:** ✅ **ALL BUSINESS INVARIANTS SATISFIED**

## 1. Executive Reconciliation Summary

| Consumer Group | Target Table | Entity Grain | Primary Invariant | Expected | Actual | Missing | Mismatches / Dups | Result |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `order-notifications` | `order_notification_dispatches` | Transition Event | Exactly-Once Delivery Audit | 61086 | 61086 | 0 | 0 | ✅ PASS |
| `order-analytics` | `analytics_order_status_rows` | Transition Event | Complete Immutable Ledger | 61086 | 61086 | 0 | 0 | ✅ PASS |
| `order-search-indexer` | `search_recent_orders` | Order (`order_id`) | Monotonic Latest Order State | 12218 | 12218 | 0 | 0 | ✅ PASS |
| `order-fraud-scoring` | `fraud_customer_state` | Customer (`customer_id`) | Strict FIFO Customer State | 200 | 200 | 0 | 0 | ✅ PASS |

---

## 2. Producer Baseline Insights

- **Total Events Produced:** `61086`
- **Unique Orders:** `12218`
- **Unique Customers:** `200`
- **Production Timeline:** `06:37:09` → `06:39:39` (Duration: `149.9s`)
- **Average Production Throughput:** `407.4 events/sec`

### Status Breakdown (Produced Events)

| Status | Count | Share |
| :--- | :--- | :--- |
| `CREATED` | 12218 | 20.0% |
| `PAID` | 12218 | 20.0% |
| `FULFILLING` | 12218 | 20.0% |
| `SHIPPED` | 12217 | 20.0% |
| `DELIVERED` | 12215 | 20.0% |

---

## 3. Consumer Group Insights & Visibility

### A. Notifications Consumer (`order-notifications`)

- **Total Dispatches:** `61086` (Sent: `61086`, Failed: `0`, Skipped: `0`)
- **Dispatch Latency:** Min: `10ms` | Mean: `20.4ms` | p50: `17.0ms` | p95: `42.0ms` | Max: `125ms`
- **Idempotency Status:** Missing: `0` | Duplicates: `0` → **✅ PASS**

### B. Analytics Consumer (`order-analytics`)

- **Total Ledger Rows:** `61086`
- **Average Ingestion Pipeline Latency:** `69.0ms`
- **Ledger Integrity:** Missing: `0` | Duplicates: `0` → **✅ PASS**

#### Kafka Partition Distribution (Analytics Table)

| Partition | Records Stored | Min Offset | Max Offset |
| :--- | :--- | :--- | :--- |
| Partition  0 | 3365 | 0 | 3364 |
| Partition  1 | 3670 | 0 | 3669 |
| Partition  2 | 3058 | 0 | 3057 |
| Partition  3 | 3360 | 0 | 3359 |
| Partition  4 | 3055 | 0 | 3054 |
| Partition  5 | 2745 | 0 | 2744 |
| Partition  6 | 2745 | 0 | 2744 |
| Partition  7 | 2440 | 0 | 2439 |
| Partition  8 | 2140 | 0 | 2139 |
| Partition  9 | 2745 | 0 | 2744 |
| Partition 10 | 3055 | 0 | 3054 |
| Partition 11 | 2445 | 0 | 2444 |
| Partition 12 | 2750 | 0 | 2749 |
| Partition 13 | 2745 | 0 | 2744 |
| Partition 14 | 2745 | 0 | 2744 |
| Partition 15 | 3360 | 0 | 3359 |
| Partition 16 | 3973 | 0 | 3972 |
| Partition 17 | 3670 | 0 | 3669 |
| Partition 18 | 3665 | 0 | 3664 |
| Partition 19 | 3355 | 0 | 3354 |

### C. Search Indexer (`order-search-indexer`)

- **Indexed Active Orders:** `12218` / `12218`
- **Monotonic Progression Check:** Missing: `0` | State Mismatches: `0` → **✅ PASS**

#### Current Indexed Orders Status Breakdown

| Current Status | Order Count |
| :--- | :--- |
| `CREATED` | 0 |
| `PAID` | 0 |
| `FULFILLING` | 1 |
| `SHIPPED` | 2 |
| `DELIVERED` | 12215 |

### D. Fraud Scoring (`order-fraud-scoring`)

- **Tracked Customer Profiles:** `200` / `200`
- **Strict FIFO State Check:** Missing: `0` | State Mismatches: `0` → **✅ PASS**

#### Customer Profile Last Status Breakdown

| Last Customer Status | Customer Count |
| :--- | :--- |
| `CREATED` | 0 |
| `PAID` | 0 |
| `FULFILLING` | 1 |
| `SHIPPED` | 2 |
| `DELIVERED` | 197 |
