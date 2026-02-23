# T20 Live Scoring – Interview Q&A

> **Status:** Placeholder – will be completed in TASK-16.3

## 25 Questions with Ideal Answers

See REQUIREMENTS.md for the full list. This document will contain formatted Q&A with code examples.

### Quick Reference

| # | Question | Key Answer |
|---|----------|------------|
| 1 | Ordering per match? | Key by `matchId` → same partition → one thread |
| 2 | Topic-per-match vs one topic? | One topic: no metadata explosion; keying preserves isolation |
| 3 | Why 192 partitions? | 1.5× peak (120 matches), power-of-2 friendly, RF=3 compatible |
| 4 | Retry/DLQ ordering? | Same partition count + same key → same partition hash |
| 5 | Restart ordering? | Offset commit after ACK; cooperative sticky + static membership |
| 6 | Duplicate prevention? | `eventId` uniqueness in DynamoDB conditional write |
| 7 | Exactly-once? | Kafka→Kafka: Spring transactions; Kafka→DB: idempotent handlers |
| 8 | Monitor consumer lag? | Lag Exporter/Burrow → Prometheus → Grafana; Micrometer |
| 9 | Autoscale? | ECS autoscale on lag custom metric; keep threads ≤ partitions |
| 10 | Replay after 10 days? | DynamoDB authoritative replay via `POST /api/v1/replay/{matchId}` |
