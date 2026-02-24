# T20 Live Scoring – Task Master

## Legend
| Status | Symbol |
|--------|--------|
| Pending | 🔲 |
| In Progress | 🔄 |
| Done | ✅ |
| Blocked | 🚫 |

---

## 📋 TASK OVERVIEW

| Task ID | Title | Status | Priority | Dependencies |
|---------|-------|--------|----------|--------------|
| TASK-00 | Local Development Environment (LocalStack + Kafka) | ✅ | P0 | None |
| TASK-01 | Project Scaffold & Parent Structure | ✅ | P0 | None |
| TASK-02 | AWS CDK Infrastructure – Foundation | 🔲 | P0 | TASK-01 |
| TASK-03 | AWS CDK Infrastructure – Compute & Messaging | 🔲 | P0 | TASK-02 |
| TASK-04 | AWS CDK Infrastructure – Observability & Security | 🔲 | P1 | TASK-03 |
| TASK-05 | Producer Application – Core Setup | ✅ | P0 | TASK-01 |
| TASK-06 | Producer Application – Kafka Integration | ✅ | P0 | TASK-05 |
| TASK-07 | Producer Application – API & Simulation | 🔲 | P1 | TASK-06 |
| TASK-08 | Producer Application – Observability | 🔲 | P1 | TASK-07 |
| TASK-09 | Consumer Application – Core Setup | ✅ | P0 | TASK-01 |
| TASK-10 | Consumer Application – Kafka Integration | ✅ | P0 | TASK-09 |
| TASK-11 | Consumer Application – DynamoDB Integration | ✅ | P0 | TASK-10 |
| TASK-12 | Consumer Application – Retry & DLQ | ✅ | P0 | TASK-10 |
| TASK-13 | Consumer Application – Replay API | 🔲 | P1 | TASK-11 |
| TASK-14 | Consumer Application – Observability | 🔲 | P1 | TASK-13 |
| TASK-15 | Integration Testing | 🔲 | P1 | TASK-08, TASK-14 |
| TASK-16 | Documentation | 🔲 | P2 | TASK-15 |

---

## TASK-00: Local Development Environment (LocalStack + Kafka)
**Status:** ✅ Done  
**Priority:** P0 – Needed for local testing of all application tasks  
**Estimated Effort:** Medium  
**Description:** Set up a fully self-contained local development environment using Docker Compose so developers can run and test the producer/consumer without AWS access. Covers a local Kafka broker (Confluent Platform KRaft, single broker), LocalStack Community (DynamoDB, S3, SNS, Secrets Manager), and Confluent Control Center UI.

> **Decisions made:** Confluent Platform lite (KRaft), LocalStack Community, single broker, health-check-based `depends_on`.

### Subtasks

| Sub ID | Description | Status | Options / Notes |
|--------|-------------|--------|-----------------|
| TASK-00.1 | Choose & configure local Kafka | ✅ | Confluent Platform 7.7.1 (KRaft, single broker) — `confluentinc/cp-kafka:7.7.1` |
| TASK-00.2 | Choose & configure local AWS services | ✅ | LocalStack Community 3.8 — DynamoDB, S3, SNS, Secrets Manager |
| TASK-00.3 | `docker-compose.yml` – Kafka broker(s) | ✅ | Single broker, KRaft mode, dual listeners (internal + external), health check |
| TASK-00.4 | `docker-compose.yml` – LocalStack service | ✅ | `localstack/localstack:3.8`, init scripts mounted to `ready.d`, persistence enabled |
| TASK-00.5 | `docker-compose.yml` – Kafka UI | ✅ | Confluent Control Center 7.7.1 on port 9021; Schema Registry on port 8081 |
| TASK-00.6 | `localstack/init/01-dynamodb.sh` – Table init script | ✅ | Creates `t20-score-events` (PK+SK+GSI), `t20-live-scores`, `t20-replay-state` |
| TASK-00.7 | `localstack/init/02-secrets.sh` – Secrets Manager init | ✅ | Creates `t20/producer/config` and `t20/consumer/config` secrets |
| TASK-00.8 | `application-local.yml` updates | ✅ | Kafka → localhost:9092; LocalStack endpoint override for Secrets Manager |
| TASK-00.9 | Developer quickstart `Makefile` | ✅ | 18 targets: `up`, `up-minimal`, `down`, `reset`, `logs`, `health`, `topics`, `kafka-produce`, `kafka-consume`, `dynamo-list`, `dynamo-scan-*`, `secrets`, `producer` |
| TASK-00.10 | Verify & document in `docs/local-setup.md` | ✅ | Full guide: prerequisites, quick start, step-by-step, Makefile reference, configuration tables, troubleshooting, cleanup |

### Acceptance Criteria
- `make up` starts all services in < 60 seconds with no manual configuration.
- `./gradlew bootRun` with `local` profile starts producer and it can produce to local Kafka.
- DynamoDB tables exist and accept writes from the consumer on local profile.
- Confluent Control Center shows the `t20-match-scores` topic and consumer group lag.
- `docs/local-setup.md` covers all steps from a fresh checkout.

---

## TASK-01: Project Scaffold & Parent Structure
**Status:** ✅ Done  
**Priority:** P0 – Must complete first  
**Estimated Effort:** Small  
**Description:** Set up the root monorepo directory structure with both Spring Boot applications and CDK infra folder.

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-01.1 | Create root `CrickeScore/` directory layout (producer, consumer, cdk-infra, docs) | ✅ |
| TASK-01.2 | Create `t20-score-producer/` Gradle project with Spring Boot 3 + Java 24 | ✅ |
| TASK-01.3 | Create `t20-score-consumer/` Gradle project with Spring Boot 3 + Java 24 | ✅ |
| TASK-01.4 | Create shared Gradle wrapper (8.14-rc-1) and root `settings.gradle` for multi-project build | ✅ |
| TASK-01.5 | Add `.gitignore`, `.editorconfig`, and root `README.md` | ✅ |
| TASK-01.6 | Initialize `cdk-infra/` as a TypeScript CDK project with all 16 lib stacks scaffolded | ✅ |
| TASK-01.7 | Initialize `docs/` directory with placeholder files for architecture, runbook, interview-qa | ✅ |

### Acceptance Criteria
- `./gradlew build` succeeds for both producer and consumer from root.
- `cdk synth` runs successfully in `cdk-infra/`.
- All directories exist as per the structure in REQUIREMENTS.md.

---

## TASK-02: AWS CDK Infrastructure – Foundation
**Status:** 🔲 Pending  
**Priority:** P0  
**Description:** Create foundational CDK lib stacks: VPC, Subnets, Security Groups, KMS, and Secrets Manager.  
**Depends on:** TASK-01

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-02.1 | `lib/kms-stack.ts` – KMS CMK for DynamoDB encryption and Secrets Manager | 🔲 |
| TASK-02.2 | `lib/vpc-stack.ts` – VPC with CIDR `10.0.0.0/16`, DNS support, flow logs to S3 | 🔲 |
| TASK-02.3 | `lib/subnet-stack.ts` – Public subnets (3 AZs), Private subnets (3 AZs), Isolated subnets (3 AZs) | 🔲 |
| TASK-02.4 | `lib/security-groups-stack.ts` – SGs for ALB, ECS (producer), ECS (consumer), MSK, DynamoDB endpoints | 🔲 |
| TASK-02.5 | `lib/secrets-manager-stack.ts` – Secrets for MSK credentials, DB connection strings, API keys | 🔲 |
| TASK-02.6 | `bin/app.ts` – Wire all stacks together with proper cross-stack references and CDK environment config | 🔲 |

### Acceptance Criteria
- `cdk diff` shows correct VPC, 9 subnets across 3 AZs, 5+ security groups, 3+ secrets.
- KMS key ARN is exported and importable in other stacks.
- No hardcoded account IDs or regions (use CDK environment tokens).

---

## TASK-03: AWS CDK Infrastructure – Compute & Messaging
**Status:** 🔲 Pending  
**Priority:** P0  
**Description:** Create MSK (Kafka), DynamoDB, S3, ECR, ECS Cluster, ECS Services, and ALB CDK stacks.  
**Depends on:** TASK-02

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-03.1 | `lib/msk-stack.ts` – MSK Kafka cluster: 3 brokers (1/AZ), `kafka.m5.xlarge`, RF=3, 192 partitions config, TLS, SASL/IAM, `min.insync.replicas=2` | 🔲 |
| TASK-03.2 | `lib/dynamodb-stack.ts` – Table `t20-score-events` (PK: matchId, SK: eventSequence), Table `t20-live-scores` (PK: matchId), GSI, TTL, KMS encryption, point-in-time recovery | 🔲 |
| TASK-03.3 | `lib/s3-stack.ts` – S3 bucket for VPC flow logs, artifact storage, server-side encryption (KMS), versioning, lifecycle rules | 🔲 |
| TASK-03.4 | `lib/ecr-stack.ts` – ECR repositories for `t20-score-producer` and `t20-score-consumer` with image scanning and lifecycle policy | 🔲 |
| TASK-03.5 | `lib/ecs-cluster-stack.ts` – ECS cluster with Container Insights, Fargate capacity providers | 🔲 |
| TASK-03.6 | `lib/ecs-producer-service-stack.ts` – Fargate task (1 vCPU, 2 GB), service with ALB integration, auto-scaling by CPU, task role with MSK + Secrets Manager permissions | 🔲 |
| TASK-03.7 | `lib/ecs-consumer-service-stack.ts` – Fargate task (2 vCPU, 4 GB), 24 tasks, 8 threads/task, auto-scaling by Kafka consumer lag (CloudWatch custom metric), task role with MSK + DynamoDB + Secrets Manager permissions | 🔲 |
| TASK-03.8 | `lib/alb-stack.ts` – Application Load Balancer (internet-facing for producer API), target group, HTTPS listener (ACM cert), health check path `/actuator/health` | 🔲 |

### Acceptance Criteria
- MSK cluster has 3 brokers, TLS enabled, SASL/IAM auth configured.
- DynamoDB tables have KMS encryption, GSI, and TTL enabled.
- ECS consumer service can scale from 24 to 48 tasks based on lag metric.
- ALB routes to producer ECS service on port 8080.

---

## TASK-04: AWS CDK Infrastructure – Observability & Security
**Status:** 🔲 Pending  
**Priority:** P1  
**Description:** Create CloudWatch dashboards/alarms, IAM roles, and WAF stacks.  
**Depends on:** TASK-03

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-04.1 | `lib/iam-stack.ts` – IAM roles: ProducerTaskRole (MSK write, Secrets Manager read), ConsumerTaskRole (MSK read, DynamoDB read/write, Secrets Manager read), least-privilege policies | 🔲 |
| TASK-04.2 | `lib/cloudwatch-stack.ts` – CloudWatch dashboards: consumer lag, DLT rate, P99 latency, broker health, ECS CPU/memory | 🔲 |
| TASK-04.3 | `lib/cloudwatch-stack.ts` – CloudWatch Alarms: consumer lag > 10k, DLT rate > 0, broker offline, API latency P99 > 1s, ECS task count below threshold | 🔲 |
| TASK-04.4 | `lib/cloudwatch-stack.ts` – Log groups for producer and consumer services with 30-day retention and KMS encryption | 🔲 |
| TASK-04.5 | `lib/waf-stack.ts` – AWS WAF WebACL attached to ALB with rate limiting (1000 req/5min per IP), SQL injection, and XSS rules | 🔲 |

### Acceptance Criteria
- IAM roles have no `*` actions; all permissions are scoped to specific resources.
- CloudWatch dashboard shows all 5 key metrics panels.
- At least 5 CloudWatch alarms are created and linked to an SNS topic.
- WAF blocks requests > 1000/5min per IP.

---

## TASK-05: Producer Application – Core Setup
**Status:** ✅ Done  

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-05.1 | `build.gradle` – Dependencies: spring-boot-starter-web, spring-kafka, micrometer-registry-prometheus, micrometer-tracing-bridge-otel, logstash-logback-encoder, lombok 1.18.38, jackson-databind, jackson-datatype-jsr310 | ✅ |
| TASK-05.2 | `application.yml` – Base config: server port 8081, graceful shutdown, actuator (health/prometheus/info/metrics/loggers), SLO histogram buckets, `app.kafka.topic.*` properties | ✅ |
| TASK-05.3 | `application-local.yml` – Local dev: local Kafka, plain-text, idempotent producer settings, auto-create topics | ✅ |
| TASK-05.4 | `application-prod.yml` – MSK bootstrap, TLS, SASL/IAM (IAMLoginModule), acks=all, retries=MAX_VALUE, zstd compression, OTLP export | ✅ |
| TASK-05.5 | `logback-spring.xml` – Coloured console for local; LogstashEncoder JSON (ShortenedThrowableConverter, MDC, traceId) for dev/prod | ✅ |
| TASK-05.6 | `ScoreEvent.java` – Immutable Java record with all fields, Bean Validation, `eventSequence()` DynamoDB SK key, `withDefaults()` factory; `ScoreEventTest.java` – 16 unit tests (sequence, factory, equality, JSON round-trip) | ✅ |

### Acceptance Criteria
- `./gradlew bootRun` starts without errors on local profile.
- `/actuator/health` returns `{"status":"UP"}`.
- Logs appear in JSON format.

---

## TASK-06: Producer Application – Kafka Integration
**Status:** ✅ Done  
**Priority:** P0  
**Description:** Implement Kafka producer configuration, service, and all reliability settings.  
**Depends on:** TASK-05

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-06.1 | `KafkaProducerConfig.java` – `ProducerFactory` and `KafkaTemplate` beans: `enable.idempotence=true`, `acks=all`, `retries=Integer.MAX_VALUE`, `max.in.flight.requests.per.connection=5`, `compression.type=zstd`, `linger.ms=5`, `batch.size=65536` | ✅ |
| TASK-06.2 | `KafkaTopicConfig.java` – `NewTopic` beans for `t20-match-scores` (192 partitions, RF=1 local/3 prod), `t20-match-scores-retry-1/2/3` (192 partitions), `t20-match-scores-dlt` (192 partitions); topic configs: `retention.ms`, `min.insync.replicas`; only active on `local`/`test` profiles | ✅ |
| TASK-06.3 | `ScoreEventProducerService.java` – `send(ScoreEvent event, String traceId)` method: serialize to JSON, set `matchId` as partition key, inject `X-Trace-Id`/`X-Match-Id`/`X-Event-Id` headers, return `CompletableFuture<SendResult>`; `TopicResolver.java` helper | ✅ |
| TASK-06.4 | `ProducerCallbackHandler.java` – Success callback: log offset/partition/topic + increment `score.events.sent.total`. Failure callback: log error + increment `score.events.failed.total` | ✅ |
| TASK-06.5 | `TlsSaslConfig.java` – `@Profile("prod")` only: SASL_SSL + AWS_MSK_IAM via IAMLoginModule; `SecretsManagerClient` bean for optional credential resolution; `mskSaslProperties` map | ✅ |
| TASK-06.6 | Unit tests: `ScoreEventProducerServiceTest.java` – Mock `KafkaTemplate`, assert correct topic, key, all three headers, payload round-trip, success/failure callbacks, serialisation exception, timer registration | ✅ |

### Acceptance Criteria
- Producer sends `ScoreEvent` to `t20-match-scores` with `matchId` as key.
- `traceId` header is present on every produced message.
- `acks=all` and `enable.idempotence=true` are confirmed in config.
- Unit tests pass: `./gradlew test`.

---

## TASK-07: Producer Application – API & Simulation
**Status:** 🔲 Pending  
**Priority:** P1  
**Description:** Implement REST API for score ingestion and a match simulator for testing.  
**Depends on:** TASK-06

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-07.1 | `ScoreEventController.java` – `POST /api/v1/scores` endpoint: validate request body, call producer service, return `202 Accepted` with `eventId` | 🔲 |
| TASK-07.2 | `ScoreEventRequest.java` – Request DTO with Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Max`)  | 🔲 |
| TASK-07.3 | `GlobalExceptionHandler.java` – `@ControllerAdvice` for validation errors (400), Kafka send failures (503), generic errors (500) | 🔲 |
| TASK-07.4 | `MatchSimulatorService.java` – Configurable simulator: generates realistic `ScoreEvent` sequences for N matches, configurable ball-by-ball delay (default 20 seconds), triggers on `POST /api/v1/simulate` | 🔲 |
| TASK-07.5 | `SimulatorController.java` – `POST /api/v1/simulate` (start simulation), `DELETE /api/v1/simulate/{matchId}` (stop match), `GET /api/v1/simulate/status` (list active simulations) | 🔲 |
| TASK-07.6 | Integration tests: `ScoreEventControllerIntegrationTest.java` – Embedded Kafka, POST a score event, assert it lands in Kafka with correct key and headers | 🔲 |

### Acceptance Criteria
- `POST /api/v1/scores` with valid payload returns 202.
- `POST /api/v1/scores` with invalid payload returns 400 with field errors.
- `POST /api/v1/simulate` starts generating events for specified matches.
- Integration test passes with embedded Kafka.

---

## TASK-08: Producer Application – Observability
**Status:** 🔲 Pending  
**Priority:** P1  
**Description:** Add Micrometer metrics, OpenTelemetry tracing, and actuator endpoints to producer.  
**Depends on:** TASK-07

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-08.1 | `MetricsConfig.java` – Custom Micrometer counters: `score.events.sent.total`, `score.events.failed.total`, `score.events.dlt.total`; timer: `score.event.send.latency` | 🔲 |
| TASK-08.2 | OpenTelemetry auto-instrumentation: `OTEL_EXPORTER_OTLP_ENDPOINT` env var, resource attributes (`service.name`, `service.version`, `deployment.environment`) | 🔲 |
| TASK-08.3 | Actuator config: expose `health`, `prometheus`, `info`, `metrics` endpoints; health indicators for Kafka connectivity | 🔲 |
| TASK-08.4 | `Dockerfile` – Multi-stage build: `gradle:8-jdk17` builder → `eclipse-temurin:17-jre-alpine` runtime; non-root user; JVM heap flags via `JAVA_OPTS` | 🔲 |

### Acceptance Criteria
- `GET /actuator/prometheus` returns Micrometer metrics in Prometheus format.
- Traces appear with `traceId` correlating Kafka headers and HTTP requests.
- Docker image builds successfully: `docker build -t t20-score-producer .`.

---

## TASK-09: Consumer Application – Core Setup
**Status:** 🔲 Pending  
**Priority:** P0  
**Description:** Bootstrap the `t20-score-consumer` Spring Boot 3 application with core dependencies and configuration.  
**Depends on:** TASK-01

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-09.1 | `build.gradle` – Dependencies: spring-boot-starter-web, spring-kafka, spring-boot-starter-data-dynamodb (or AWS SDK v2), micrometer-registry-prometheus, opentelemetry-spring-boot-starter, logstash-logback-encoder, lombok, jackson-databind | 🔲 |
| TASK-09.2 | `application.yml` – Kafka consumer group: `t20-score-consumer-group`, `concurrency=8`, `auto.offset.reset=earliest`, `enable.auto.commit=false`, `isolation.level=read_committed` | 🔲 |
| TASK-09.3 | `application-local.yml` – Local dev: local Kafka, local DynamoDB endpoint (DynamoDB Local), DEBUG logging | 🔲 |
| TASK-09.4 | `application-prod.yml` – Production: MSK bootstrap, TLS/SASL, Secrets Manager, DynamoDB regional endpoint | 🔲 |
| TASK-09.5 | `logback-spring.xml` – Same JSON logging structure as producer | 🔲 |
| TASK-09.6 | `ScoreEvent.java` – Shared model (same as producer); consider extracting to a shared `t20-score-common` module in future | 🔲 |

### Acceptance Criteria
- `./gradlew bootRun` starts without errors on local profile.
- `/actuator/health` returns `{"status":"UP"}`.
- Consumer config shows `enable.auto.commit=false` confirmed.

---

## TASK-10: Consumer Application – Kafka Integration
**Status:** ✅ Done  
**Priority:** P0  
**Description:** Implement Kafka consumer factory, listener container factory, and main score event listener.  
**Depends on:** TASK-09

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-10.1 | `KafkaConsumerConfig.java` – `ConsumerFactory` and `ConcurrentKafkaListenerContainerFactory`: `concurrency=8`, `AckMode.RECORD`, `CooperativeStickyAssignor`, `group.instance.id` for static membership, `max.poll.records=50`, `fetch.min.bytes=1024` | 🔲 |
| TASK-10.2 | `ScoreEventListener.java` – `@KafkaListener(topics="t20-match-scores", groupId="t20-score-consumer-group")` method: deserialize `ScoreEvent`, extract `traceId` from headers, call `ScoreEventService.process()`, manual ACK after success | 🔲 |
| TASK-10.3 | `ScoreEventService.java` – Orchestrates: idempotency check → DynamoDB write (event store) → DynamoDB update (live score) → emit notification | 🔲 |
| TASK-10.4 | `IdempotencyService.java` – Check `eventId` uniqueness before processing (DynamoDB conditional write or Redis SETNX); return `ALREADY_PROCESSED` if duplicate | 🔲 |
| TASK-10.5 | `TlsSaslConfig.java` – Same TLS/SASL pattern as producer | 🔲 |
| TASK-10.6 | Unit tests: `ScoreEventListenerTest.java` – Mock `ScoreEventService`, assert `process()` called once for new event, zero times for duplicate | 🔲 |

### Acceptance Criteria
- Consumer receives events from `t20-match-scores` with `AckMode.RECORD`.
- Duplicate `eventId` is detected and skipped (idempotency).
- `traceId` from Kafka header is linked to the processing span.
- Unit tests pass.

---

## TASK-11: Consumer Application – DynamoDB Integration
**Status:** ✅ Done  
**Priority:** P0  
**Description:** Implement DynamoDB repositories for event store writes and live score materialized view updates.  
**Depends on:** TASK-10

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-11.1 | `DynamoDbConfig.java` – `DynamoDbClient` bean with region, endpoint override for local, credential provider chain | ✅ |
| TASK-11.2 | `ScoreEventRepository.java` – `putItem()` for `t20-score-events` table using composite key `matchId` + `inning#over#ball`; conditional expression to prevent overwrites (idempotency) | ✅ |
| TASK-11.3 | `LiveScoreRepository.java` – `updateItem()` for `t20-live-scores` table: atomic update of `totalRuns`, `wickets`, `currentOver`, `currentBall`, `lastUpdated` using `SET` expressions | ✅ |
| TASK-11.4 | `ScoreEventMapper.java` – Map `ScoreEvent` → DynamoDB `AttributeValue` maps (put/update expressions) | ✅ |
| TASK-11.5 | Unit tests: `ScoreEventRepositoryTest.java` – DynamoDB Local or Testcontainers (localstack); assert item written with correct PK/SK; assert conditional expression rejects duplicate | ✅ |
| TASK-11.6 | Unit tests: `LiveScoreRepositoryTest.java` – Write 10 sequential events, assert `totalRuns` in live score matches sum; verify UpdateItem structure, wicket accumulation | ✅ |

### Acceptance Criteria
- `ScoreEvent` persisted to `t20-score-events` with correct PK/SK.
- `t20-live-scores` updated atomically after each event.
- Duplicate event write fails with `ConditionalCheckFailedException` (not an application error).
- Tests pass against DynamoDB Local or LocalStack.

---

## TASK-12: Consumer Application – Retry & DLQ
**Status:** ✅ Done  
**Priority:** P0  
**Description:** Implement retry topics, DLQ routing, and DLT alert mechanism.  
**Depends on:** TASK-10

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-12.1 | `@RetryableTopic` on `ScoreEventListener`: 3 retries, exponential backoff (1s, 5s, 15s), DLT suffix `-dlt` | ✅ |
| TASK-12.2 | Exception classification: `exclude` non-retryable (`JsonProcessingException`, `ConstraintViolationException`); all `RuntimeException` retried by default | ✅ |
| TASK-12.3 | `DltListener.java` – `@KafkaListener` on `t20-match-scores-dlt`: log, emit `score.events.dlt.total`, call `DeadLetterNotificationService` | ✅ |
| TASK-12.4 | `DeadLetterNotificationService` interface + `NoOpDeadLetterNotificationService` (local/test stub; SNS impl in TASK-14) | ✅ |
| TASK-12.5 | Unit tests: `DltListenerTest.java` – 4 tests covering notify called, ACK always sent, notify-before-ACK ordering, missing headers | ✅ |

### Acceptance Criteria
- Retryable exceptions retry 3 times with backoff before hitting DLT.
- Non-retryable exceptions go directly to DLT (no retry).
- DLT listener logs the event and sends SNS notification.
- DLT topic uses same `matchId` partition key → same partition.

---

## TASK-13: Consumer Application – Replay API
**Status:** 🔲 Pending  
**Priority:** P1  
**Description:** Implement a REST API to replay all events for a given match from DynamoDB.  
**Depends on:** TASK-11

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-13.1 | `ReplayController.java` – `POST /api/v1/replay/{matchId}`: trigger replay; `GET /api/v1/replay/{matchId}/status`: check replay status | 🔲 |
| TASK-13.2 | `ReplayService.java` – Query `t20-score-events` table by `matchId` (all items sorted by SK `inning#over#ball`); re-publish each event to `t20-match-scores` topic with original `matchId` key | 🔲 |
| TASK-13.3 | `ReplayStateRepository.java` – Track replay status (STARTED, IN_PROGRESS, COMPLETED, FAILED) in DynamoDB `t20-replay-state` table | 🔲 |
| TASK-13.4 | Rate limiting on replay: max 1 concurrent replay per `matchId`; reject with 409 if already in progress | 🔲 |
| TASK-13.5 | Integration tests: `ReplayServiceTest.java` – Seed 300 events for a matchId in DynamoDB Local, trigger replay, assert all 300 re-published to Kafka in order | 🔲 |

### Acceptance Criteria
- `POST /api/v1/replay/{matchId}` returns `202 Accepted` and triggers async replay.
- Events are replayed from DynamoDB in strict `inning#over#ball` order.
- Second replay request on same matchId while first is running returns 409.
- All 300 events replayed in correct order (verified in integration test).

---

## TASK-14: Consumer Application – Observability
**Status:** 🔲 Pending  
**Priority:** P1  
**Description:** Add Micrometer metrics, OpenTelemetry tracing, and Docker setup to consumer.  
**Depends on:** TASK-13

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-14.1 | `MetricsConfig.java` – Custom Micrometer counters: `score.events.consumed.total`, `score.events.processed.total`, `score.events.duplicate.total`, `score.events.dlt.total`; timers: `score.event.processing.latency`, `score.dynamodb.write.latency` | 🔲 |
| TASK-14.2 | OpenTelemetry config: propagate `traceId` from Kafka header → processing span → DynamoDB span; resource attributes same as producer | 🔲 |
| TASK-14.3 | Actuator: expose `health`, `prometheus`, `info`, `metrics`; custom health indicator checking Kafka consumer assignment and DynamoDB connectivity | 🔲 |
| TASK-14.4 | `Dockerfile` – Multi-stage build same pattern as producer; JVM flags for container-aware memory (`-XX:+UseContainerSupport`), `-XX:MaxRAMPercentage=75.0` | 🔲 |

### Acceptance Criteria
- `GET /actuator/prometheus` exposes all consumer-specific metrics.
- Processing trace links HTTP → Kafka consume → DynamoDB write in one trace.
- Docker image builds successfully: `docker build -t t20-score-consumer .`.

---

## TASK-15: Integration Testing
**Status:** 🔲 Pending  
**Priority:** P1  
**Description:** End-to-end integration tests covering key scenarios: ordering, idempotency, retry, DLQ, and replay.  
**Depends on:** TASK-08, TASK-14

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-15.1 | `docker-compose.yml` – Local stack: Kafka (3 brokers via Redpanda or Kafka), LocalStack (DynamoDB, S3, SNS, Secrets Manager), producer service, consumer service | 🔲 |
| TASK-15.2 | Ordering test: send 300 events for 1 match out of order → verify DynamoDB `t20-score-events` shows all events; live score reflects correct final state | 🔲 |
| TASK-15.3 | Idempotency test: publish same `eventId` twice → verify only 1 record in DynamoDB, consumer metrics show 1 duplicate detected | 🔲 |
| TASK-15.4 | Retry test: inject retryable failure via mock, verify events processed after retry-2 topic | 🔲 |
| TASK-15.5 | DLQ test: inject non-retryable failure → verify event in DLT topic, SNS notification sent | 🔲 |
| TASK-15.6 | Replay test: trigger replay API → verify all events re-consumed and state rebuilt correctly | 🔲 |
| TASK-15.7 | Load test (basic): simulate 100 concurrent matches × 300 events = 30,000 events; verify P99 < 1s and no data loss | 🔲 |

### Acceptance Criteria
- All 6 test scenarios pass against local Docker Compose stack.
- Load test: 30,000 events processed with 0 data loss and P99 < 1s on local hardware.
- `docker-compose up` brings up full local stack ready for testing.

---

## TASK-16: Documentation
**Status:** 🔲 Pending  
**Priority:** P2  
**Description:** Complete all project documentation.  
**Depends on:** TASK-15

### Subtasks

| Sub ID | Description | Status |
|--------|-------------|--------|
| TASK-16.1 | `docs/architecture-diagram.md` – System architecture diagram (mermaid or PlantUML): producer → MSK → consumer → DynamoDB; ALB → ECS; CloudWatch monitoring flow | 🔲 |
| TASK-16.2 | `docs/runbook.md` – Operational runbook: SLOs, alarm response procedures, how to replay a match, how to read DLT messages, scaling procedure | 🔲 |
| TASK-16.3 | `docs/interview-qa.md` – All 25 Q&A from the requirements, formatted with question, ideal answer, and code example where applicable | 🔲 |
| TASK-16.4 | Root `README.md` – Project overview, local setup guide, how to run docker-compose, how to deploy CDK, environment variable reference | 🔲 |
| TASK-16.5 | API documentation: OpenAPI 3.0 spec (`openapi.yaml`) for producer score endpoint and consumer replay endpoint | 🔲 |

### Acceptance Criteria
- `README.md` is complete with all setup instructions.
- Architecture diagram accurately represents the system.
- Runbook covers all SLO alarms and response steps.

---

## 📊 Progress Summary

**Total Tasks:** 17  
**Total Subtasks:** 102  

| Status | Tasks | Subtasks |
|--------|-------|----------|
| ✅ Done | 6 | 47 |
| 🔄 In Progress | 0 | 0 |
| 🔲 Pending | 11 | 55 |
| 🚫 Blocked | 0 | 0 |

---

## 🔀 Task Dependency Graph

```
TASK-00 (Local Dev Environment — standalone, can run in parallel)

TASK-01 (Scaffold)
    ├── TASK-02 (CDK Foundation)
    │       └── TASK-03 (CDK Compute)
    │               └── TASK-04 (CDK Observability)
    ├── TASK-05 (Producer Setup)
    │       └── TASK-06 (Producer Kafka) ✅
    │               └── TASK-07 (Producer API)
    │                       └── TASK-08 (Producer Observability)
    └── TASK-09 (Consumer Setup)
            └── TASK-10 (Consumer Kafka)
                    ├── TASK-11 (Consumer DynamoDB)
                    │       └── TASK-13 (Replay API)
                    │               └── TASK-14 (Consumer Observability)
                    └── TASK-12 (Retry & DLQ)
                            └── TASK-14 (Consumer Observability)

TASK-08 + TASK-14 → TASK-15 (Integration Testing) → TASK-16 (Documentation)
TASK-00 feeds into TASK-15 (local stack used by integration tests)
```

---

## ⚡ How to Use This File

1. **Pick the next pending task** in dependency order (start with TASK-01).
2. **Tell me** `"Start TASK-01"` or `"Start TASK-01.2"` for a specific subtask.
3. I will **implement only that task/subtask**, then **mark it ✅ Done**.
4. **Verify** the acceptance criteria before moving to the next task.
5. Repeat for each task in order.

> **Rule:** Never start TASK-N if any dependency is still 🔲 Pending or 🔄 In Progress.
