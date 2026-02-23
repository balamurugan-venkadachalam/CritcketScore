# T20 Live Scoring – Detailed Requirements

## System Overview
A real-time T20 cricket scoring system built on an event-driven architecture using Apache Kafka (AWS MSK), Spring Boot microservices, and AWS cloud-native services. The system streams live scores for 100–130 concurrent matches with strict ordering guarantees, high availability, and full event replay capability.

---

## Functional Requirements

### FR-1: Score Event Production
- FR-1.1: Produce `ScoreEvent` messages to Kafka topic `t20-match-scores` for every legal ball (up to 300 per match innings).
- FR-1.2: Partition key must be `matchId` to guarantee per-match ordering.
- FR-1.3: Each `ScoreEvent` must contain: `eventId`, `matchId`, `inning`, `over`, `ball`, `team`, `runs`, `extras`, `wicket`, `totalRuns`, `wickets`, `timestamp`.
- FR-1.4: Producer must support idempotent sends (enable.idempotence=true, acks=all).
- FR-1.5: Events must carry OpenTelemetry `traceId` in Kafka headers.
- FR-1.6: Producer must use Avro schema (via Schema Registry) or compact JSON with versioning.

### FR-2: Score Event Consumption
- FR-2.1: Consume `ScoreEvent` messages from topic `t20-match-scores` and process in strict per-match order.
- FR-2.2: Persist each event to the Event Store (DynamoDB) with a unique composite key (`matchId#inning#over#ball`).
- FR-2.3: Handlers must be idempotent — duplicate events must not corrupt the state.
- FR-2.4: Maintain materialized views (live score snapshot per match) in DynamoDB.
- FR-2.5: Emit processed score notifications for downstream WebSocket/API consumers.

### FR-3: Retry & Dead Letter Queue
- FR-3.1: Retry failed events up to 3 times via topics: `t20-match-scores-retry-1`, `t20-match-scores-retry-2`, `t20-match-scores-retry-3`.
- FR-3.2: Non-retryable errors must be routed to `t20-match-scores-dlt` (Dead Letter Topic).
- FR-3.3: All retry/DLT topics must have the same partition count (192) and use the same partition key (`matchId`).
- FR-3.4: DLT listener must create alerts/tickets for manual intervention.

### FR-4: Event Replay
- FR-4.1: Replay all events for a given `matchId` from DynamoDB (authoritative source).
- FR-4.2: Support re-consumption from earliest Kafka offset (if retention >= 10 days).
- FR-4.3: Replay must preserve original ordering (`inning` → `over` → `ball` sequence).
- FR-4.4: Expose a replay API endpoint: `POST /replay/{matchId}`.

### FR-5: Observability
- FR-5.1: Expose consumer lag metrics via Kafka Lag Exporter/Burrow to Prometheus.
- FR-5.2: Grafana dashboards for: consumer lag per group, DLT message rate, processing latency (P99), broker health.
- FR-5.3: OpenTelemetry traces propagated via Kafka headers with correlation to logs.
- FR-5.4: Structured JSON logs (Logstash encoder) for all services.

### FR-6: Security
- FR-6.1: TLS encryption for all Kafka connections.
- FR-6.2: SASL/IAM authentication for MSK access.
- FR-6.3: All secrets (Kafka credentials, DB keys) via AWS Secrets Manager.
- FR-6.4: DynamoDB encrypted at rest via AWS KMS.
- FR-6.5: Least-privilege IAM roles per service (producer role, consumer role).

---

## Non-Functional Requirements

### NFR-1: Performance
- NFR-1.1: P99 end-to-end message latency < 1 second (producer → consumer → DB write).
- NFR-1.2: Support 100–130 concurrent matches; size for 2× peak (260 concurrent matches).
- NFR-1.3: Throughput target: 120 matches × ~6 events/over × 10 overs/active = ~7,200 events/minute peak.

### NFR-2: Availability
- NFR-2.1: System availability SLO: 99.9% (≤ 8.7 hours downtime/year).
- NFR-2.2: Kafka RF=3, one replica per AZ; min.insync.replicas=2.
- NFR-2.3: No single point of failure; multi-AZ deployment for all components.

### NFR-3: Scalability
- NFR-3.1: 192 Kafka partitions supporting current load with ~1.5× headroom.
- NFR-3.2: Horizontal autoscaling of consumer pods based on consumer lag.
- NFR-3.3: Future path to 512 partitions for 300–500 concurrent matches.
- NFR-3.4: 24 ECS Fargate pods × 8 threads = 192 total consumer threads (1:1 partition mapping).

### NFR-4: Reliability
- NFR-4.1: Exactly-once semantics for Kafka→Kafka flows via Spring Kafka transactions.
- NFR-4.2: At-least-once with idempotent handlers for Kafka→DynamoDB writes.
- NFR-4.3: Zero data loss on consumer restart (offset commit after successful processing).
- NFR-4.4: Cooperative sticky partition assignment + static membership to minimize rebalance storms.

### NFR-5: Operational
- NFR-5.1: Rolling deployments with minHealthy=100%, maxSurge=200%.
- NFR-5.2: CloudWatch alarms: consumer lag threshold, DLT rate, broker health, API latency.
- NFR-5.3: Message retention: main topic 7 days, retry topics 24 hours, DLT 14 days, DynamoDB unlimited.
- NFR-5.4: CI/CD pipeline via GitHub Actions or AWS CodePipeline.

---

## Technology Stack

| Layer              | Technology                          |
|--------------------|-------------------------------------|
| Message Broker     | Apache Kafka on AWS MSK             |
| Producer Service   | Java 17 + Spring Boot 3 + Gradle    |
| Consumer Service   | Java 17 + Spring Boot 3 + Gradle    |
| Event Store        | AWS DynamoDB                        |
| Schema Registry    | Confluent Schema Registry / Glue    |
| Monitoring         | Prometheus + Grafana + Burrow       |
| Tracing            | OpenTelemetry + AWS X-Ray           |
| Logging            | CloudWatch Logs + Logstash JSON     |
| Infrastructure     | AWS CDK (TypeScript)                |
| Compute            | AWS ECS Fargate                     |
| Networking         | AWS VPC + ALB                       |
| Secrets            | AWS Secrets Manager                 |
| Encryption         | AWS KMS                             |
| Container Registry | AWS ECR                             |
| Storage            | AWS S3 (artifacts, config)          |

---

## Data Model

### ScoreEvent (Kafka Message)
```json
{
  "eventId": "uuid-v4",
  "matchId": "IPL-2025-MI-CSK-001",
  "inning": 1,
  "over": 3,
  "ball": 4,
  "team": "MI",
  "runs": 4,
  "extras": 0,
  "wicket": false,
  "totalRuns": 54,
  "wickets": 2,
  "timestamp": "2025-04-15T14:32:01Z"
}
```

### DynamoDB Schema

**Table: `t20-score-events`**
- Partition Key: `matchId` (String)
- Sort Key: `eventSequence` = `inning#over#ball` (String)
- Attributes: all ScoreEvent fields + `processedAt`, `ttl`
- GSI: `matchId-timestamp-index` for time-range queries

**Table: `t20-live-scores`** (Materialized View)
- Partition Key: `matchId` (String)
- Attributes: `currentInning`, `currentOver`, `currentBall`, `totalRuns`, `wickets`, `lastUpdated`

---

## Project Structure

```
CrickeScore/
├── REQUIREMENTS.md          ← This file
├── TASKS.md                 ← Task breakdown and status tracking
├── t20-score-producer/      ← Spring Boot Producer Application
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/
├── t20-score-consumer/      ← Spring Boot Consumer Application
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/
├── cdk-infra/               ← AWS CDK Infrastructure (TypeScript)
│   ├── package.json
│   ├── cdk.json
│   ├── bin/
│   │   └── app.ts
│   └── lib/
│       ├── vpc-stack.ts
│       ├── subnet-stack.ts
│       ├── security-groups-stack.ts
│       ├── msk-stack.ts
│       ├── dynamodb-stack.ts
│       ├── s3-stack.ts
│       ├── ecr-stack.ts
│       ├── ecs-cluster-stack.ts
│       ├── ecs-producer-service-stack.ts
│       ├── ecs-consumer-service-stack.ts
│       ├── alb-stack.ts
│       ├── secrets-manager-stack.ts
│       ├── kms-stack.ts
│       ├── iam-stack.ts
│       ├── cloudwatch-stack.ts
│       └── waf-stack.ts
└── docs/
    ├── architecture-diagram.md
    ├── runbook.md
    └── interview-qa.md
```
