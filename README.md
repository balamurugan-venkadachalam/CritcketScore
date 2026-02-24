# T20 Live Scoring System

[![Java 24](https://img.shields.io/badge/Java-24-blue)](https://openjdk.org/projects/jdk/24/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-green)](https://spring.io/projects/spring-boot) [![Kafka](https://img.shields.io/badge/Kafka-MSK-orange)](https://aws.amazon.com/msk/) [![CDK](https://img.shields.io/badge/AWS%20CDK-2.130-yellow)](https://aws.amazon.com/cdk/)

Real-time T20 cricket score streaming system built on event-driven architecture with Apache Kafka, Spring Boot, and AWS cloud-native services. Handles **100–130 concurrent matches** with strict per-match ordering, high availability, and full event replay capability.

---

## 🏗️ Architecture Overview

```
HTTP POST → ALB → Producer (ECS) → MSK Kafka (192 partitions) → Consumer (ECS) → DynamoDB
                                                                                    ↓
                                                                            Live Score API
```

**Key Design Decisions:**
- **Partition key**: `matchId` ensures all events for a match go to same partition → strict ordering
- **192 partitions**: 1.6× peak load (120 matches), 1:1 thread-to-partition mapping
- **Replication**: RF=3, min.insync=2 → survives 1 AZ failure without data loss
- **Consumer threads**: 24 ECS tasks × 8 threads = 192 concurrent processors

See [`docs/architecture-diagram.md`](./docs/architecture-diagram.md) for detailed architecture diagrams and design rationale.

---

## 📁 Project Structure

```
CrickeScore/
├── t20-score-producer/          # Spring Boot 3 Kafka producer (port 8081)
│   ├── src/main/java/com/crickscore/producer/
│   │   ├── api/                 # REST controllers (scores, simulator)
│   │   ├── kafka/               # Kafka producer service & config
│   │   └── model/               # ScoreEvent domain model
│   └── src/test/java/           # Unit & integration tests
│
├── t20-score-consumer/          # Spring Boot 3 Kafka consumer (port 8082)
│   ├── src/main/java/com/crickscore/consumer/
│   │   ├── kafka/               # Kafka consumer & retry/DLQ
│   │   ├── dynamodb/            # DynamoDB repositories
│   │   └── api/                 # Replay API
│   └── src/test/java/           # Unit & integration tests
│
├── cdk-infra/                   # AWS CDK TypeScript infrastructure
│   ├── lib/                     # 16 CDK stacks (VPC, MSK, ECS, etc.)
│   └── bin/app.ts               # CDK app entry point
│
├── docs/                        # Documentation
│   ├── architecture-diagram.md  # System architecture & design decisions
│   ├── runbook.md               # Operational procedures & troubleshooting
│   ├── interview-qa-complete.md # 25 technical interview Q&A
│   └── local-setup.md           # Local development guide
│
├── localstack/                  # LocalStack init scripts
│   └── init/                    # DynamoDB tables, Secrets Manager
│
├── docker-compose.yml           # Local dev environment (Kafka + LocalStack)
├── Makefile                     # 18 developer commands
├── REQUIREMENTS.md              # Functional & non-functional requirements
└── TASKS.md                     # Task breakdown with status tracking
```

---

## 🚀 Quick Start (Local Development)

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 24+ | Application runtime |
| **Gradle** | 8.14 (via wrapper) | Build tool |
| **Docker** | 24+ | Local Kafka + DynamoDB |
| **Make** | Any | Developer commands |
| **Node.js** | 18+ (optional) | CDK deployment |
| **AWS CLI** | 2+ (optional) | AWS deployment |

### 1. Clone and Build

```bash
git clone <repo-url>
cd CrickeScore

# Build both applications
./gradlew build

# Run tests
./gradlew test
```

### 2. Start Local Infrastructure

```bash
# Start Kafka (Confluent Platform) + LocalStack (DynamoDB, Secrets Manager)
make up

# Verify services are healthy
make health

# View logs
make logs
```

**What starts:**
- Kafka broker (KRaft mode) on `localhost:9092`
- Schema Registry on `localhost:8081`
- Confluent Control Center on `http://localhost:9021`
- LocalStack on `localhost:4566`
- DynamoDB tables: `t20-score-events`, `t20-live-scores`, `t20-replay-state`

### 3. Run Producer

```bash
# Terminal 1: Start producer
make producer
# OR
./gradlew :t20-score-producer:bootRun --args='--spring.profiles.active=local'

# Producer runs on http://localhost:8081
```

**Health check:**
```bash
curl http://localhost:8081/actuator/health
```

### 4. Run Consumer

```bash
# Terminal 2: Start consumer
make consumer
# OR
./gradlew :t20-score-consumer:bootRun --args='--spring.profiles.active=local'

# Consumer runs on http://localhost:8082
```

### 5. Test the System

```bash
# Start a match simulation (generates realistic ball-by-ball events)
curl -X POST http://localhost:8081/api/v1/simulate \
  -H "Content-Type: application/json" \
  -d '{"matchIds": ["IPL-2025-MI-CSK-001"], "ballDelayMs": 1000}'

# Check simulation status
curl http://localhost:8081/api/v1/simulate/status

# View Kafka messages
make kafka-consume

# Check DynamoDB events
make dynamo-scan-events

# Check live scores
make dynamo-scan-live-scores
```

### 6. Cleanup

```bash
# Stop all services
make down

# Reset (stop + remove volumes)
make reset
```

---

## 🔧 Makefile Commands

| Command | Description |
|---------|-------------|
| `make up` | Start all services (Kafka + LocalStack) |
| `make up-minimal` | Start only Kafka (no UI) |
| `make down` | Stop all services |
| `make reset` | Stop and remove volumes |
| `make health` | Check service health |
| `make logs` | View all service logs |
| `make topics` | List Kafka topics |
| `make kafka-produce` | Produce test message |
| `make kafka-consume` | Consume messages from main topic |
| `make dynamo-list` | List DynamoDB tables |
| `make dynamo-scan-events` | Scan event store table |
| `make dynamo-scan-live-scores` | Scan live scores table |
| `make secrets` | List Secrets Manager secrets |
| `make producer` | Run producer application |
| `make consumer` | Run consumer application |

See [`docs/local-setup.md`](./docs/local-setup.md) for detailed local development guide.

---

## ☁️ AWS Deployment

### Prerequisites

```bash
# Install AWS CDK CLI
npm install -g aws-cdk

# Configure AWS credentials
aws configure
```

### Deploy Infrastructure

```bash
cd cdk-infra

# Install dependencies
npm install

# Bootstrap CDK (first time only)
cdk bootstrap aws://ACCOUNT-ID/ap-southeast-2

# Preview changes
cdk diff

# Deploy all stacks
cdk deploy --all
```

### Stack Deployment Order

CDK automatically manages dependencies. Stacks deploy in this order:

1. **Foundation**: `T20KmsStack` → `T20VpcStack` → `T20SubnetStack` → `T20SecurityGroupsStack`
2. **Data Layer**: `T20MskStack`, `T20DynamoDbStack`, `T20S3Stack`, `T20EcrStack`
3. **Security**: `T20SecretsManagerStack`, `T20IamStack`
4. **Compute**: `T20EcsClusterStack` → `T20AlbStack` → `T20EcsProducerServiceStack`, `T20EcsConsumerServiceStack`
5. **Observability**: `T20CloudWatchStack`, `T20WafStack`

### Build and Push Docker Images

```bash
# Build producer image
cd t20-score-producer
docker build -t <account>.dkr.ecr.ap-southeast-2.amazonaws.com/t20-score-producer:latest .

# Build consumer image
cd t20-score-consumer
docker build -t <account>.dkr.ecr.ap-southeast-2.amazonaws.com/t20-score-consumer:latest .

# Login to ECR
aws ecr get-login-password --region ap-southeast-2 | docker login --username AWS --password-stdin <account>.dkr.ecr.ap-southeast-2.amazonaws.com

# Push images
docker push <account>.dkr.ecr.ap-southeast-2.amazonaws.com/t20-score-producer:latest
docker push <account>.dkr.ecr.ap-southeast-2.amazonaws.com/t20-score-consumer:latest
```

---

## 📡 API Endpoints

### Producer Service (port 8081)

#### Score Ingestion

```bash
# Submit a score event
POST /api/v1/scores
Content-Type: application/json

{
  "matchId": "IPL-2025-MI-CSK-001",
  "inning": 1,
  "over": 3,
  "ball": 4,
  "team": "MI",
  "runs": 4,
  "wicket": false,
  "totalRuns": 54,
  "wickets": 2
}

# Response: 202 Accepted
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "matchId": "IPL-2025-MI-CSK-001",
  "message": "Score event accepted for publishing"
}
```

#### Match Simulation

```bash
# Start simulation for multiple matches
POST /api/v1/simulate
Content-Type: application/json

{
  "matchIds": ["IPL-2025-MI-CSK-001", "IPL-2025-RCB-KKR-001"],
  "ballDelayMs": 5000
}

# Stop simulation
DELETE /api/v1/simulate/IPL-2025-MI-CSK-001

# Check simulation status
GET /api/v1/simulate/status
```

#### Observability

```bash
# Health check
GET /actuator/health

# Prometheus metrics
GET /actuator/prometheus

# Application info
GET /actuator/info
```

### Consumer Service (port 8082)

#### Event Replay

```bash
# Trigger replay from DynamoDB
POST /api/v1/replay/IPL-2025-MI-CSK-001

# Response: 202 Accepted
{
  "status": "STARTED",
  "matchId": "IPL-2025-MI-CSK-001",
  "eventCount": 300
}

# Check replay status
GET /api/v1/replay/IPL-2025-MI-CSK-001/status

# Response
{
  "status": "IN_PROGRESS",
  "processed": 150,
  "total": 300
}
```

See [`openapi.yaml`](./openapi.yaml) for complete API specification.

---

## 🔐 Environment Variables

### Producer Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | - | `local`, `dev`, or `prod` |
| `SERVER_PORT` | No | `8081` | HTTP server port |
| `MSK_BOOTSTRAP_SERVERS` | Yes (prod) | `localhost:9092` | Kafka bootstrap servers |
| `KAFKA_TOPIC_NAME` | No | `t20-match-scores` | Main topic name |
| `KAFKA_TOPIC_PARTITIONS` | No | `192` | Partition count |
| `AWS_REGION` | Yes (prod) | `ap-southeast-2` | AWS region |
| `SECRETS_MANAGER_SECRET_NAME` | Yes (prod) | - | Secret name for Kafka credentials |

### Consumer Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | - | `local`, `dev`, or `prod` |
| `SERVER_PORT` | No | `8082` | HTTP server port |
| `MSK_BOOTSTRAP_SERVERS` | Yes (prod) | `localhost:9092` | Kafka bootstrap servers |
| `KAFKA_CONSUMER_GROUP_ID` | No | `t20-score-consumer-group` | Consumer group ID |
| `KAFKA_CONCURRENCY` | No | `8` | Listener threads per pod |
| `DYNAMODB_TABLE_EVENTS` | No | `t20-score-events` | Event store table |
| `DYNAMODB_TABLE_LIVE_SCORES` | No | `t20-live-scores` | Live scores table |
| `DYNAMODB_TABLE_REPLAY_STATE` | No | `t20-replay-state` | Replay state table |
| `DYNAMODB_ENDPOINT` | No (local) | `http://localhost:4566` | LocalStack endpoint |
| `AWS_REGION` | Yes (prod) | `ap-southeast-2` | AWS region |

---

## 📊 Monitoring & Observability

### Metrics

Both services expose Prometheus metrics at `/actuator/prometheus`:

**Producer Metrics:**
- `score.events.sent.total` - Total events sent to Kafka
- `score.events.failed.total` - Failed send attempts
- `score.event.send.latency` - Send latency histogram
- `kafka.producer.record.send.rate` - Records sent per second

**Consumer Metrics:**
- `score.events.consumed.total` - Total events consumed
- `score.events.processed.total` - Successfully processed events
- `score.events.duplicate.total` - Duplicate events detected
- `score.events.dlt.total` - Events sent to DLT
- `kafka.consumer.records.lag.max` - Maximum consumer lag

### Logs

Structured JSON logs with trace correlation:

```json
{
  "timestamp": "2025-04-15T14:32:01.123Z",
  "level": "INFO",
  "logger": "com.crickscore.producer.kafka.ScoreEventProducerService",
  "message": "Score event sent to Kafka",
  "traceId": "550e8400e29b41d4a716446655440000",
  "spanId": "a716446655440000",
  "matchId": "IPL-2025-MI-CSK-001",
  "eventId": "550e8400-e29b-41d4-a716-446655440001"
}
```

### Dashboards

- **Confluent Control Center**: http://localhost:9021 (local)
- **CloudWatch Dashboards**: Consumer lag, DLT rate, API latency (AWS)
- **Grafana**: System-wide health (AWS)

---

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Run producer tests only
./gradlew :t20-score-producer:test

# Run consumer tests only
./gradlew :t20-score-consumer:test

# Run integration tests
./gradlew :t20-score-producer:test --tests "*IntegrationTest"

# Run with coverage
./gradlew test jacocoTestReport
```

**Test Coverage:**
- Producer: 39 tests (unit + integration with embedded Kafka)
- Consumer: 28 tests (unit + integration with LocalStack DynamoDB)

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [`REQUIREMENTS.md`](./REQUIREMENTS.md) | Functional & non-functional requirements |
| [`TASKS.md`](./TASKS.md) | Task breakdown with status tracking |
| [`docs/architecture-diagram.md`](./docs/architecture-diagram.md) | System architecture & design decisions |
| [`docs/runbook.md`](./docs/runbook.md) | Operational procedures & troubleshooting |
| [`docs/interview-qa-complete.md`](./docs/interview-qa-complete.md) | 25 technical interview Q&A |
| [`docs/local-setup.md`](./docs/local-setup.md) | Local development guide |
| [`openapi.yaml`](./openapi.yaml) | OpenAPI 3.0 API specification |

---

## 🎯 Key Features

- ✅ **Strict per-match ordering** via `matchId` partition key
- ✅ **High availability** with multi-AZ deployment (RF=3, min.insync=2)
- ✅ **Idempotent processing** via DynamoDB conditional writes
- ✅ **Retry & DLQ** with exponential backoff (1s, 5s, 15s)
- ✅ **Event replay** from DynamoDB (authoritative source)
- ✅ **Horizontal scaling** with ECS autoscaling on consumer lag
- ✅ **Observability** with OpenTelemetry traces, Prometheus metrics, structured logs
- ✅ **Match simulation** for testing and demos
- ✅ **Local development** with Docker Compose (Kafka + LocalStack)

---

## 📈 Performance & Scale

| Metric | Target | Current Capacity |
|--------|--------|------------------|
| **Concurrent matches** | 100-130 | 192 (1.6× headroom) |
| **P99 latency** | < 1 second | Producer → Consumer → DynamoDB |
| **Throughput** | 7,200 events/min | ~120 events/sec peak |
| **Availability** | 99.9% | Multi-AZ with RF=3 |
| **Consumer lag** | < 10,000 messages | Autoscales at 5,000 |
| **Data retention** | Kafka: 7 days | DynamoDB: unlimited |

---

## 🔄 Task Progress

See [`TASKS.md`](./TASKS.md) for detailed task breakdown.

| Task | Description | Status |
|------|-------------|--------|
| TASK-00 | Local Development Environment | ✅ Done |
| TASK-01 | Project Scaffold & Parent Structure | ✅ Done |
| TASK-05 | Producer Application – Core Setup | ✅ Done |
| TASK-06 | Producer Application – Kafka Integration | ✅ Done |
| TASK-07 | Producer Application – API & Simulation | ✅ Done |
| TASK-09 | Consumer Application – Core Setup | ✅ Done |
| TASK-10 | Consumer Application – Kafka Integration | ✅ Done |
| TASK-11 | Consumer Application – DynamoDB Integration | ✅ Done |
| TASK-12 | Consumer Application – Retry & DLQ | ✅ Done |
| TASK-16 | Documentation | 🔄 In Progress |
| TASK-02-04, 08, 13-15 | Infrastructure & Observability | 🔲 Pending |

**Progress:** 9/17 tasks completed (53%)

---

## 🤝 Contributing

1. Follow existing code style and patterns
2. Write unit tests for new features
3. Update documentation for API changes
4. Run `./gradlew build` before committing

---

## 📄 License

[Your License Here]

---

## 👥 Team & Support

- **Architecture**: See [`docs/architecture-diagram.md`](./docs/architecture-diagram.md)
- **Operations**: See [`docs/runbook.md`](./docs/runbook.md)
- **Interview Prep**: See [`docs/interview-qa-complete.md`](./docs/interview-qa-complete.md)
