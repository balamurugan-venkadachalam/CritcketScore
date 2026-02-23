# T20 Live Scoring – Architecture Diagram

## System Architecture

```mermaid
graph TB
    subgraph Internet
        Client[Score API Clients / Simulators]
    end

    subgraph AWS["AWS (ap-southeast-2)"]
        subgraph VPC["VPC 10.0.0.0/16"]
            ALB[Application Load Balancer]

            subgraph PublicSubnets["Public Subnets (3 AZs)"]
                ALB
            end

            subgraph PrivateSubnets["Private Subnets (3 AZs)"]
                Producer[ECS Fargate\nt20-score-producer\n2 tasks × 1vCPU/2GB]
                Consumer[ECS Fargate\nt20-score-consumer\n24 tasks × 8 threads]
                MSK[AWS MSK\n3 Brokers\n192 Partitions / RF=3]
            end

            subgraph IsolatedSubnets["Isolated Subnets (3 AZs)"]
                DDB[(DynamoDB\nt20-score-events\nt20-live-scores)]
            end
        end

        ECR[ECR Repositories]
        SM[Secrets Manager]
        CW[CloudWatch\nLogs + Dashboards]
        OTEL[OpenTelemetry\nCollector]
    end

    Client -->|HTTPS POST /api/v1/scores| ALB
    ALB --> Producer
    Producer -->|"Kafka produce\nkey=matchId → same partition\nguarantees per-match ordering"| MSK
    MSK -->|"Kafka consume\ngroup=t20-score-consumer-group\n1 thread per partition"| Consumer
    Consumer -->|PutItem / UpdateItem| DDB
    Consumer -->|Retry / DLQ| MSK
    Producer & Consumer --> CW
    Producer & Consumer --> OTEL
```

---

## Kafka Partitioning Strategy

### Why `matchId` is the Partition Key

Kafka only guarantees message ordering **within a single partition**. All balls for the same match must land on the same partition so the consumer can write them to DynamoDB in strict delivery order (Ball 1 → Ball 2 → Ball 3).

```mermaid
graph LR
    subgraph Producer["Producer (key = matchId)"]
        E1["Ball 1\nmatchId=MI-CSK"]
        E2["Ball 2\nmatchId=MI-CSK"]
        E3["Ball 1\nmatchId=RCB-KKR"]
    end

    subgraph MSK["MSK Topic: t20-match-scores (192 partitions)"]
        P17["Partition 17\n[MI-CSK Ball 1]\n[MI-CSK Ball 2]"]
        P84["Partition 84\n[RCB-KKR Ball 1]"]
    end

    subgraph Consumer["Consumer threads (1 thread per partition)"]
        T17["Thread → Partition 17\nWrites MI-CSK balls in order"]
        T84["Thread → Partition 84\nWrites RCB-KKR balls in order"]
    end

    E1 -->|hash| P17
    E2 -->|hash| P17
    E3 -->|hash| P84
    P17 --> T17
    P84 --> T84
```

### Single Partition vs matchId Key — Comparison

| | Single Partition + Filter | `matchId` as Partition Key ✅ |
|---|---|---|
| **Ordering guarantee** | ❌ None across concurrent matches | ✅ Strict per-match order guaranteed |
| **Parallelism** | ❌ One consumer thread handles all matches serially | ✅ Each match processed on its own independent thread |
| **Throughput** | ❌ 8 simultaneous matches queue behind each other | ✅ 8 matches processed fully in parallel |
| **Isolation** | ❌ Slow DynamoDB write on one match delays all others | ✅ One match's retry never affects another match |
| **Retry safety** | ❌ Retries can interleave with original messages, reordering balls | ✅ Retries hash to the same partition, preserving order |
| **DynamoDB consistency** | ❌ Ball 3 may commit before Ball 1, corrupting live score view | ✅ `AckMode.RECORD` ensures Ball N+1 only starts after Ball N is written |

### Why "Filter by matchId" Doesn't Work

#### ❌ Problem — Single Partition, Thread Pool (race condition)

The consumer polls a batch and hands all messages to a thread pool simultaneously. DynamoDB writes are network I/O — completion order is **non-deterministic**:

```
Time  0ms  Thread pool picks up Ball 1, Ball 2, Ball 3 at the same time
           Worker 1 → Ball 1 DynamoDB write starts  (takes 50ms, slow network)
           Worker 2 → Ball 2 DynamoDB write starts  (takes 15ms, fast)
           Worker 3 → Ball 3 DynamoDB write starts  (takes 20ms, fast)

Time 15ms  Ball 2 committed to DynamoDB ✓
Time 20ms  Ball 3 committed to DynamoDB ✓
Time 25ms  Someone reads live score → Ball 1 run is MISSING, Ball 3 recorded without Ball 1
Time 50ms  Ball 1 committed to DynamoDB ✓  (too late, score already served incorrectly)
```

> **Result:** Live score view shows wrong totals mid-match. Ball ordering in DynamoDB is corrupted.

#### ✅ Fix — `matchId` Partition Key + `AckMode.RECORD` + Single Thread

When all balls of a match go to **one partition** assigned to **one thread**, and `AckMode.RECORD` makes the thread **block** until each DynamoDB write completes before moving on:

```
Time  0ms  Thread 17 polls Partition 17 → gets [Ball 1, Ball 2, Ball 3]
           → processes Ball 1 → DynamoDB write → BLOCKS (AckMode.RECORD)

Time 50ms  Ball 1 write DONE ✓ → offset committed
           → NOW processes Ball 2 → DynamoDB write → BLOCKS

Time 65ms  Ball 2 write DONE ✓ → offset committed
           → NOW processes Ball 3 → DynamoDB write → BLOCKS

Time 85ms  Ball 3 write DONE ✓ → offset committed
```

> **Result:** DynamoDB always has Ball 1 before Ball 2, Ball 2 before Ball 3. Guaranteed.

The two guarantees that make this work:
1. **`matchId` partition key** → all balls of a match go to the same partition, consumed by one thread
2. **`AckMode.RECORD` + `enable-auto-commit: false`** → thread does not move to Ball N+1 until Ball N is fully written to DynamoDB and offset committed

### Partition Count Rationale

```
192 partitions = 24 ECS consumer tasks × 8 listener threads per task
               = ~1.6× peak concurrent IPL matches (~120 matches/day peak)
```

- **1:1 thread-to-partition mapping** — no idle threads, no partition contention
- **Kafka's default partitioner** (`murmur2` hash of `matchId`) distributes matches evenly across all 192 partitions
- **Rebalance cost is low** — `CooperativeStickyAssignor` used in prod to avoid stop-the-world rebalances on rolling deploys

---

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Message key | `matchId` | Guarantees per-match ordering — all balls of a match land on one partition and are processed by one thread in sequence |
| Partitions | 192 | 24 consumer tasks × 8 threads = 192 concurrent processors; ~1.6× peak load headroom |
| Consumer threads | 192 (24 pods × 8) | 1:1 mapping with partitions — no idle capacity, no contention |
| Replication | RF=3, min.insync=2 | Survives 1 AZ failure without data loss |
| Isolation level | `read_committed` | Consumer only reads producer-committed messages — pairs with `enable.idempotence=true` on producer |
| ACK mode | `AckMode.RECORD` | Offset committed only after DynamoDB write succeeds — no message dropped silently |
| Rebalance strategy | `CooperativeStickyAssignor` | Rolling deploys don't pause all partition consumption; only reassigned partitions pause |
| Static membership | `group.instance.id` set | Consumer pod restart within `session.timeout.ms` rejoins without triggering a rebalance |
| Storage | DynamoDB PAY_PER_REQUEST | No capacity planning, scales elastically with burst traffic during popular matches |
