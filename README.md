# T20 Live Scoring System

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/) [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-green)](https://spring.io/projects/spring-boot) [![Kafka](https://img.shields.io/badge/Kafka-MSK-orange)](https://aws.amazon.com/msk/) [![CDK](https://img.shields.io/badge/AWS%20CDK-2.130-yellow)](https://aws.amazon.com/cdk/)

Real-time T20 cricket score streaming system built on an event-driven architecture.
Handles **100–130 concurrent matches** with strict ordering, high availability, and full event replay.

---

## Project Structure

```
CrickeScore/
├── t20-score-producer/     ← Spring Boot 3 Kafka producer (port 8081)
├── t20-score-consumer/     ← Spring Boot 3 Kafka consumer + DynamoDB (port 8082)
├── cdk-infra/              ← AWS CDK TypeScript infrastructure
├── docs/                   ← Architecture diagrams, runbook, interview Q&A
├── REQUIREMENTS.md         ← Full functional & non-functional requirements
└── TASKS.md                ← Taskmaster – all tasks with status tracking
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Gradle | 8.6 (via wrapper) |
| Node.js | 18+ |
| AWS CDK CLI | 2.130+ |
| Docker | 24+ |
| AWS CLI | 2+ |

---

## Local Development Setup

### 1. Clone and build

```bash
git clone <repo-url>
cd CrickeScore

# Build both Spring Boot applications
./gradlew build
```

### 2. Start local infrastructure (Kafka + DynamoDB Local)

```bash
# Coming in TASK-15 (docker-compose.yml)
docker-compose up -d
```

### 3. Run Producer

```bash
./gradlew :t20-score-producer:bootRun
# Runs on http://localhost:8081
# Health: http://localhost:8081/actuator/health
```

### 4. Run Consumer

```bash
./gradlew :t20-score-consumer:bootRun
# Runs on http://localhost:8082
# Health: http://localhost:8082/actuator/health
```

---

## AWS Deployment (CDK)

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

### Stack Deployment Order (managed automatically via dependencies)

1. `T20KmsStack` – Encryption keys
2. `T20VpcStack` → `T20SubnetStack` → `T20SecurityGroupsStack` – Networking
3. `T20SecretsManagerStack` – Secrets
4. `T20MskStack` + `T20DynamoDbStack` + `T20S3Stack` + `T20EcrStack` – Data layer
5. `T20IamStack` – Roles & policies
6. `T20EcsClusterStack` → `T20AlbStack` → Producer/Consumer ECS services
7. `T20CloudWatchStack` + `T20WafStack` – Observability & security

---

## Key API Endpoints

### Producer (port 8081)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/scores` | Submit a score event |
| POST | `/api/v1/simulate` | Start match simulation |
| DELETE | `/api/v1/simulate/{matchId}` | Stop match simulation |
| GET | `/api/v1/simulate/status` | List active simulations |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

### Consumer (port 8082)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/replay/{matchId}` | Replay match from DynamoDB |
| GET | `/api/v1/replay/{matchId}/status` | Check replay status |
| GET | `/actuator/health` | Health check |
| GET | `/actuator/prometheus` | Prometheus metrics |

---

## Environment Variables

| Variable | Service | Description |
|----------|---------|-------------|
| `MSK_BOOTSTRAP_SERVERS` | Producer, Consumer | MSK TLS bootstrap endpoint |
| `AWS_REGION` | All | AWS region (default: ap-southeast-2) |
| `SECRETS_NAME` | Producer, Consumer | Secrets Manager secret name |
| `KAFKA_CONCURRENCY` | Consumer | Threads per pod (default: 8) |
| `DYNAMODB_TABLE_EVENTS` | Consumer | DynamoDB event store table name |
| `DYNAMODB_TABLE_LIVE_SCORES` | Consumer | DynamoDB live scores table name |
| `SPRING_PROFILES_ACTIVE` | All | Spring profile (local, dev, prod) |

---

## Task Progress

See [TASKS.md](./TASKS.md) for full task breakdown and current status.

| Task | Status |
|------|--------|
| TASK-01: Project Scaffold | Done |
| TASK-02: CDK Foundation | Pending |
| TASK-03: CDK Compute | Pending |
| TASK-04 through TASK-16 | Pending |
