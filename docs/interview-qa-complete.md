# T20 Live Scoring – Interview Q&A

Comprehensive technical interview questions covering the T20 Live Scoring system.

---

## Quick Reference Table

| # | Question | Key Answer |
|---|----------|------------|
| 1 | How guarantee ordering per match? | `matchId` as partition key → same partition → one thread |
| 2 | Why one topic vs topic-per-match? | One topic: no metadata explosion; keying provides isolation |
| 3 | Why 192 partitions? | 1.6× peak load (120 matches), 1:1 thread mapping, RF=3 compatible |
| 4 | How retry/DLQ preserve ordering? | Same partition count + same key → same partition hash |
| 5 | How ordering survives restarts? | Offset commit after ACK + cooperative sticky + static membership |
| 6 | How prevent duplicates? | `eventId` uniqueness via DynamoDB conditional write |
| 7 | Exactly-once semantics? | Kafka→Kafka: transactions; Kafka→DB: idempotent handlers |
| 8 | How monitor consumer lag? | Micrometer + CloudWatch + Kafka Lag Exporter → Prometheus |
| 9 | How autoscale consumers? | ECS autoscale on lag metric; keep threads ≤ partitions |
| 10 | How replay after 10 days? | DynamoDB authoritative source via `/api/v1/replay/{matchId}` |
| 11 | Why `matchId` not `eventId` as key? | `eventId` is unique → random partitions → no ordering |
| 12 | What happens during rebalance? | Cooperative sticky: only reassigned partitions pause |
| 13 | What is static membership? | Persistent `group.instance.id` → no rebalance on restart |
| 14 | Why `AckMode.RECORD` not `BATCH`? | Commits after each message → prevents message loss |
| 15 | How implement idempotency? | DynamoDB conditional write: `attribute_not_exists(eventId)` |
| 16 | Why materialized view? | O(1) read vs O(n) query; 125× cost reduction |
| 17 | How alert on DLT messages? | DLT listener → CloudWatch metric → SNS → PagerDuty |
| 18 | What are scaling limits? | Current: 192 partitions; Max: 2,000 (MSK limit) |
| 19 | How does multi-AZ work? | RF=3, min.insync=2; survives 1 AZ failure |
| 20 | How producer idempotence works? | Kafka tracks sequence numbers per partition |
| 21 | How propagate traces? | Inject `traceId` in Kafka headers; extract on consume |
| 22 | How handle eventual consistency? | Strong reads for replay; eventual for dashboards |
| 23 | How handle backpressure? | `max.poll.records`, autoscaling, circuit breaker |
| 24 | How handle schema evolution? | Backward-compatible changes + `@JsonIgnoreProperties` |
| 25 | Disaster recovery strategy? | Multi-AZ + DynamoDB backups + Kafka retention |

---

## Detailed Answers

### 1. How do you guarantee strict ordering per match?

Use `matchId` as Kafka partition key. All events for same match hash to same partition → consumed by single thread in sequence.

```java
ProducerRecord<String, ScoreEvent> record = new ProducerRecord<>(
    "t20-match-scores",
    event.matchId(),  // ← PARTITION KEY
    event
);
```

With `AckMode.RECORD`, thread blocks until DynamoDB write completes before processing next message.

---

### 2. Why one topic instead of topic-per-match?

| Approach | Pros | Cons |
|----------|------|------|
| One topic per match | Perfect isolation | 130 topics × 3 replicas = metadata explosion |
| One topic, keyed by matchId ✅ | Same isolation; 1 consumer group | Requires correct key |

One topic scales to 1000s of matches without operational overhead.

---

### 3. Why 192 partitions specifically?

```
192 partitions = 24 ECS tasks × 8 threads
               = 1:1 thread-to-partition mapping
               = 1.6× peak load (120 concurrent matches)
```

Power-of-2 friendly, RF=3 compatible, allows future growth to 512.

---

### 4. How do retry and DLT topics preserve ordering?

Same partition count (192) + same key (`matchId`) ensures retries hash to same partition:

```
hash("IPL-2025-MI-CSK-001") % 192 = 17
→ Main topic: Partition 17
→ Retry-1: Partition 17
→ DLT: Partition 17
```

---

### 5. How does ordering survive consumer restarts?

Three mechanisms:
1. **Offset commit after ACK**: `AckMode.RECORD` + `enable-auto-commit: false`
2. **Cooperative sticky**: Only reassigned partitions pause
3. **Static membership**: `group.instance.id` → no rebalance if restart < 45s

---

### 6. How do you prevent duplicate event processing?

DynamoDB conditional write:

```java
dynamoDb.putItem(item, "attribute_not_exists(eventId)");
```

Atomic check prevents race conditions even with concurrent writes.

---

### 7. Can you achieve exactly-once semantics?

**Kafka→Kafka**: Yes, via Spring Kafka transactions  
**Kafka→DynamoDB**: At-least-once + idempotency = effectively exactly-once

---

### 8. How do you monitor consumer lag?

- **Micrometer**: `kafka.consumer.records.lag.max`
- **CloudWatch**: Custom metric with alarm at 10,000
- **Kafka Lag Exporter**: Prometheus metrics for Grafana

---

### 9. How does consumer autoscaling work?

ECS target tracking on consumer lag metric:
- Lag > 5,000 → scale out (24 → 48 tasks)
- Lag < 5,000 for 5 min → scale in

⚠️ Constraint: Total threads ≤ 192 partitions

---

### 10. How do you replay events after 10 days?

DynamoDB is authoritative source (Kafka retention only 7 days):

```bash
curl -X POST https://alb/api/v1/replay/IPL-2025-MI-CSK-001
```

Query DynamoDB by `matchId`, sort by `eventSequence`, re-publish to Kafka.

---

### 11. Why `matchId` as partition key, not `eventId`?

`eventId` is UUID → every event goes to different partition → **no ordering**  
`matchId` → all events for match go to same partition → **ordered**

---

### 12. What happens during consumer rebalance?

With `CooperativeStickyAssignor`, only reassigned partitions pause briefly. Other partitions continue processing without interruption.

---

### 13. What is static membership and why use it?

Persistent `group.instance.id` per consumer. If restart within `session.timeout.ms` (45s), Kafka skips rebalance and reassigns same partitions.

---

### 14. Why `AckMode.RECORD` instead of `BATCH`?

`RECORD` commits offset after each message → prevents message loss on crash.  
`BATCH` commits after entire batch → crash loses partial batch.

---

### 15. How is idempotency implemented in DynamoDB?

```java
try {
    dynamoDb.putItem(item, "attribute_not_exists(eventId)");
} catch (ConditionalCheckFailedException e) {
    // Duplicate detected, safe to ignore
}
```

---

### 16. Why maintain materialized view in `t20-live-scores`?

| Approach | Read Cost | Latency |
|----------|-----------|---------|
| On-demand (query + aggregate) | 120 reads | ~50ms |
| Materialized view ✅ | 1 read | ~2ms |

At 100k viewers × 0.33 req/s = 125× cost reduction.

---

### 17. How do you alert on DLT messages?

DLT listener increments CloudWatch metric → Alarm triggers on any DLT message → SNS → PagerDuty.

---

### 18. What are the scaling limits?

| Component | Current | Max |
|-----------|---------|-----|
| Kafka partitions | 192 | 2,000 (MSK limit) |
| Consumer threads | 192 | = partition count |
| ECS tasks | 24 | 48 (if 4 threads/task) |

---

### 19. How does multi-AZ deployment work?

- **Kafka**: RF=3, min.insync=2 → survives 1 AZ failure
- **ECS**: Spread across 3 AZs
- **DynamoDB**: Auto-replicated across 3 AZs

---

### 20. How does producer idempotence work?

Kafka assigns `producer.id` and tracks sequence numbers per partition. Duplicate sequence numbers are ignored.

---

### 21. How do you propagate traces through Kafka?

Inject `traceId` in Kafka headers on produce; extract on consume:

```java
record.headers().add("X-Trace-Id", traceId.getBytes());
```

---

### 22. How do you handle DynamoDB eventual consistency?

Use **strongly consistent reads** for replay/audit; eventual consistency acceptable for dashboards.

---

### 23. How do you handle backpressure from slow consumers?

- `max.poll.records: 50` limits batch size
- ECS autoscaling adds capacity
- Circuit breaker fails fast on persistent errors

---

### 24. How do you handle schema evolution?

Backward-compatible changes only:
- ✅ Add optional field
- ✅ Remove field (with `@JsonIgnoreProperties`)
- ❌ Rename field
- ❌ Change type

---

### 25. What is your disaster recovery strategy?

- **Multi-AZ**: Survives 1 AZ failure
- **DynamoDB**: Point-in-time recovery enabled
- **Kafka**: 7-day retention + replay from DynamoDB
- **RTO**: 15 minutes
- **RPO**: 0 (no data loss with RF=3, min.insync=2)
