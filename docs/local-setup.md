# T20 Live Scoring – Local Development Setup

This guide walks you through spinning up the complete local development environment — Kafka, Schema Registry, Confluent Control Center, and all AWS services via LocalStack — on your Mac developer machine.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Stack Overview](#stack-overview)
3. [Quick Start (TL;DR)](#quick-start-tldr)
4. [Detailed Setup](#detailed-setup)
5. [Running the Applications](#running-the-applications)
6. [Verifying the Stack](#verifying-the-stack)
7. [Makefile Reference](#makefile-reference)
8. [Configuration Reference](#configuration-reference)
9. [Troubleshooting](#troubleshooting)
10. [Cleaning Up](#cleaning-up)

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Docker Desktop | ≥ 4.x | [docs.docker.com](https://docs.docker.com/desktop/install/mac-install/) |
| Java | 24 | `brew install --cask temurin@24` |
| AWS CLI v2 | ≥ 2.x | `brew install awscli` |
| Make | any | pre-installed on macOS |

> **Docker resources**: Confluent Control Center is memory-hungry. Allocate at least **6 GB RAM** to Docker Desktop in *Settings → Resources → Memory*. If you're RAM-constrained, use `make up-minimal` (broker + LocalStack only, no Control Center).

### One-time AWS CLI local profile

LocalStack accepts any credentials — set them once so the CLI works without `--no-sign-request`:

```bash
aws configure set aws_access_key_id     test       --profile localstack
aws configure set aws_secret_access_key test       --profile localstack
aws configure set region                ap-southeast-2 --profile localstack
```

Or just export them per-session (the Makefile handles this automatically):

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=ap-southeast-2
```

---

## Stack Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Docker Compose (t20-local network)              │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ broker (confluentinc/cp-kafka:7.7.1) — KRaft, port 9092     │   │
│  │  • INTERNAL listener: broker:29092  (container-to-container)  │   │
│  │  • EXTERNAL listener: localhost:9092 (host access)           │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              ▲                                       │
│             depends_on (healthy)                                     │
│                              │                                       │
│  ┌────────────────────────┐  │  ┌───────────────────────────────┐   │
│  │ schema-registry        │──┘  │ control-center (UI)           │   │
│  │ port 8081              │     │ port 9021                     │   │
│  └────────────────────────┘     └───────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ localstack (community 3.8) — port 4566                       │   │
│  │  Services: dynamodb, s3, sns, secretsmanager                  │   │
│  │  Init scripts: localstack/init/0{1-4}-*.sh                   │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘

Host machine:
  Spring Boot producer  → connects to localhost:9092 (Kafka)
                        → connects to http://localhost:4566 (LocalStack)
```

---

## Quick Start (TL;DR)

```bash
# 1. Start the full stack
make up

# 2. Wait ~60 seconds for all services to become healthy
make health

# 3. Run the producer
make producer

# 4. Send a test event manually (in another terminal)
make kafka-produce

# 5. Verify the message arrived
make kafka-consume
```

Open the Confluent Control Center at **http://localhost:9021** to browse topics and consumer groups visually.

---

## Detailed Setup

### Step 1 – Clone and navigate

```bash
git clone <repo-url>
cd CrickeScore
```

### Step 2 – Start the local stack

```bash
make up
```

This runs `docker compose up -d` which:
1. Starts the **Kafka broker** (KRaft, single node) — waits for healthy
2. Starts **Schema Registry** — waits for broker healthy
3. Starts **Control Center** — waits for schema-registry healthy
4. Starts **LocalStack** — simultaneously with Kafka
5. LocalStack executes `localstack/init/` scripts on first startup:
   - `01-dynamodb.sh` → creates `t20-score-events`, `t20-live-scores`, `t20-replay-state`
   - `02-s3.sh`       → creates artifact and flow-log buckets
   - `03-sns.sh`      → creates `t20-dlt-alerts`, `t20-ops-alarms` SNS topics
   - `04-secrets.sh`  → creates `t20/producer/config`, `t20/consumer/config` secrets

### Step 3 – Verify all services are healthy

```bash
make health
```

Expected output:
```
  Kafka broker      : UP
  Schema Registry   : UP
  Control Center    : UP   (may take up to 60s)
  LocalStack        : UP
```

### Step 4 – Verify DynamoDB tables exist

```bash
make dynamo-list
```

Expected output includes: `t20-score-events`, `t20-live-scores`, `t20-replay-state`

---

## Running the Applications

### Producer (Spring Boot)

```bash
# Via Makefile:
make producer

# Or directly via Gradle:
./gradlew :t20-score-producer:bootRun --args='--spring.profiles.active=local'
```

The producer starts on **port 8081**. Health check: `curl http://localhost:8081/actuator/health`

### Consumer (Spring Boot) — when implemented (TASK-09+)

```bash
./gradlew :t20-score-consumer:bootRun --args='--spring.profiles.active=local'
```

The consumer starts on **port 8082** (default, configured in consumer's application-local.yml).

---

## Verifying the Stack

### Check Kafka topics

```bash
make topics
```

After running the producer with `local` profile, you'll see the auto-created topics:
- `t20-match-scores`
- `t20-match-scores-retry-1`
- `t20-match-scores-retry-2`
- `t20-match-scores-retry-3`
- `t20-match-scores-dlt`

### Send a test event manually

```bash
make kafka-produce
```

### Read events from the topic

```bash
make kafka-consume
# Ctrl+C to stop
```

### Browse via Control Center UI

Open **http://localhost:9021** in your browser:
- *Topics* → Select `t20-match-scores` → *Messages* tab to inspect events
- *Consumer Groups* → Monitor consumer lag
- *Schema Registry* → Browse schemas (for future Avro migration)

### Verify DynamoDB items (after consumer runs)

```bash
make dynamo-scan-events    # show score-event store
make dynamo-scan-scores    # show live scores
```

### Check Secrets Manager

```bash
make secrets

# Fetch the producer secret directly:
aws --endpoint-url=http://localhost:4566 --region=ap-southeast-2 \
    --no-sign-request \
    secretsmanager get-secret-value --secret-id t20/producer/config
```

---

## Makefile Reference

| Target | Description |
|--------|-------------|
| `make up` | Start full stack (broker + schema-registry + control-center + localstack) |
| `make up-minimal` | Start only broker + localstack (no Control Center, faster startup) |
| `make down` | Stop all containers (volumes preserved) |
| `make reset` | Full teardown including Docker volumes (clean slate) |
| `make logs` | Tail all container logs |
| `make logs-kafka` | Tail only Kafka broker logs |
| `make logs-ls` | Tail only LocalStack logs |
| `make health` | Check readiness of all services |
| `make topics` | List all Kafka topics |
| `make topic-desc` | Describe the main `t20-match-scores` topic |
| `make kafka-produce` | Send a sample ScoreEvent to the main topic |
| `make kafka-consume` | Consume messages from the main topic (Ctrl+C to stop) |
| `make dynamo-list` | List all DynamoDB tables |
| `make dynamo-scan-events` | Scan first 10 items from `t20-score-events` |
| `make dynamo-scan-scores` | Scan all live scores |
| `make secrets` | List Secrets Manager secrets |
| `make producer` | Run the producer app on local profile |

---

## Configuration Reference

### Spring Boot local profile (`application-local.yml`)

| Property | Value | Notes |
|----------|-------|-------|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Confluent single broker |
| `spring.kafka.producer.acks` | `all` | Same reliability as prod |
| `spring.kafka.producer.properties.compression.type` | `lz4` | Faster than zstd for local volumes |
| `spring.kafka.admin.auto-create` | `true` | Spring creates topics on startup |
| `aws.region` | `ap-southeast-2` | Matches prod region |
| `aws.secretsmanager.endpoint-override` | `http://localhost:4566` | Points to LocalStack |

### AWS SDK v2 local credentials

No real credentials are needed. Export these environment variables before running the app locally:

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=ap-southeast-2
```

Or add them to your shell profile (`.zshrc`).

### LocalStack service endpoints

All LocalStack services share a single endpoint: `http://localhost:4566`

| AWS Service | LocalStack ARN pattern |
|-------------|----------------------|
| DynamoDB | `arn:aws:dynamodb:ap-southeast-2:000000000000:table/<name>` |
| S3 | `arn:aws:s3::::<bucket>` |
| SNS | `arn:aws:sns:ap-southeast-2:000000000000:<name>` |
| Secrets Manager | `arn:aws:secretsmanager:ap-southeast-2:000000000000:secret:<name>` |

The account ID for LocalStack Community is always `000000000000`.

---

## Troubleshooting

### Kafka broker doesn't start / `CLUSTER_ID already registered`

The KRaft cluster ID is baked into the Docker volume. If you see this error after a hard container kill:

```bash
make reset   # removes volumes — clean slate
make up
```

### Control Center takes too long / stays unhealthy

Control Center can take 2–3 minutes on first boot. Check its logs:

```bash
make logs-kafka | grep -i "control-center"
# or
docker logs t20-control-center --tail 50
```

If you don't need the UI, use `make up-minimal` and access topics via CLI (`make topics`, `make kafka-consume`).

### LocalStack init scripts didn't run

Scripts in `localstack/init/` only execute on **first startup** (when the volume is empty). If you made a change to init scripts after the first run, reset and try again:

```bash
make reset
make up
```

### Producer can't connect to Kafka

1. Confirm Kafka is healthy: `make health`
2. Confirm you're on the `local` profile: check the app logs for `The following profiles are active: local`
3. Check that `spring.kafka.bootstrap-servers=localhost:9092` in `application-local.yml`

### AWS SDK can't connect to LocalStack

1. Confirm LocalStack is healthy: `curl http://localhost:4566/_localstack/health`
2. Ensure `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` are exported in your shell
3. Confirm `aws.secretsmanager.endpoint-override=http://localhost:4566` in your profile config

### Port conflicts

| Port | Service | Fix |
|------|---------|-----|
| 9092 | Kafka broker | Stop local Kafka: `brew services stop kafka` |
| 8081 | Schema Registry | Check for other HTTP services on 8081 |
| 9021 | Control Center | `lsof -i :9021` then kill the conflicting process |
| 4566 | LocalStack | Check for another LocalStack instance: `docker ps \| grep localstack` |

---

## Cleaning Up

```bash
# Stop containers (keep data):
make down

# Nuclear option — removes all containers, networks, and volumes:
make reset
```

> **Note:** `make reset` removes all Kafka topic data and DynamoDB table data. Run `make up` again to recreate everything from scratch via the init scripts.
