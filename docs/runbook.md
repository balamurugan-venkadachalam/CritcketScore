# T20 Live Scoring – Operational Runbook

## Service Level Objectives (SLOs)

| Metric | Target | Measurement | Alert Threshold |
|--------|--------|-------------|-----------------|
| **P99 end-to-end latency** | < 1 second | Producer → Kafka → Consumer → DynamoDB write | > 1.5s for 5 minutes |
| **System availability** | 99.9% | Uptime (≤ 8.7 hours/year) | < 99.5% over 24h window |
| **Consumer lag (per partition)** | < 10,000 messages | Kafka consumer group lag | > 10,000 for 3 minutes |
| **DLT message rate** | 0 messages/hour | Dead Letter Topic produce rate | > 0 (alert immediately) |
| **API error rate** | < 0.1% | 5xx responses / total requests | > 1% over 5 minutes |
| **Message loss rate** | 0% | Producer send failures | > 0 (alert immediately) |

---

## CloudWatch Alarms & Response Procedures

### ALM-01: Consumer Lag > 10,000 Messages

**Severity:** HIGH  
**Trigger:** Consumer lag exceeds 10,000 messages for 3 consecutive minutes  
**Impact:** Score updates delayed; viewers see stale data

#### Immediate Actions
1. **Check consumer health**
   ```bash
   aws ecs describe-services \
     --cluster t20-score-cluster \
     --services t20-score-consumer-service
   ```
   Look for: running task count, CPU/memory utilization, recent deployments

2. **Scale out consumer service**
   ```bash
   aws ecs update-service \
     --cluster t20-score-cluster \
     --service t20-score-consumer-service \
     --desired-count 48
   ```
   ⚠️ **Max safe count:** 48 tasks (192 partitions ÷ 4 partitions/task = 48)

3. **Check CloudWatch Logs for errors**
   ```bash
   aws logs filter-log-events \
     --log-group-name /ecs/t20-score-consumer \
     --filter-pattern "ERROR" \
     --start-time $(date -u -d '10 minutes ago' +%s)000
   ```

4. **Verify MSK broker health**
   - Navigate to MSK Console → Cluster Metrics
   - Check: CPU utilization, disk usage, network throughput
   - If broker CPU > 80%, consider upgrading broker instance type

#### Root Cause Investigation
- **Slow DynamoDB writes:** Check DynamoDB throttling metrics; increase provisioned capacity or switch to on-demand
- **GC pauses:** Review JVM heap metrics; increase task memory if heap > 75%
- **Network issues:** Check VPC flow logs for packet loss
- **Code regression:** Compare lag before/after recent deployment; rollback if needed

---

### ALM-02: DLT Rate > 0

**Severity:** CRITICAL  
**Trigger:** Any message lands in Dead Letter Topic  
**Impact:** Data loss risk; manual intervention required

#### Immediate Actions
1. **Query DLT topic for messages**
   ```bash
   kafka-console-consumer \
     --bootstrap-server $MSK_BOOTSTRAP \
     --topic t20-match-scores-dlt \
     --from-beginning \
     --property print.headers=true \
     --property print.key=true
   ```

2. **Identify error type from headers**
   - `X-Error-Type`: Exception class name
   - `X-Error-Message`: Exception message
   - `X-Retry-Count`: Number of retry attempts (should be 3)
   - `X-Match-Id`: Affected match

3. **Categorize failure**
   - **Retryable (transient):** DynamoDB throttling, network timeout
     - Action: Restart consumer pods to retry
   - **Non-retryable (permanent):** JSON parsing error, schema mismatch, constraint violation
     - Action: Fix code bug, deploy new version, then replay

4. **Fix and replay**
   ```bash
   # After deploying fix
   curl -X POST https://<alb-dns>/api/v1/replay/<matchId>
   ```

#### Prevention
- Add integration tests for all error scenarios
- Enable DynamoDB auto-scaling to prevent throttling
- Validate schema changes in staging before production

---

### ALM-03: MSK Broker Offline

**Severity:** HIGH  
**Trigger:** MSK broker becomes unavailable  
**Impact:** Reduced throughput; potential data unavailability if 2+ brokers fail

#### Immediate Actions
1. **Check MSK cluster health**
   ```bash
   aws kafka describe-cluster --cluster-arn <arn>
   ```
   Look for: broker state, active broker count

2. **Verify replication factor**
   - RF=3 with min.insync.replicas=2 means system tolerates 1 broker failure
   - If 1 broker down: **No action needed** – system continues normally
   - If 2+ brokers down: **CRITICAL** – escalate to AWS Support immediately

3. **Check partition leadership**
   ```bash
   kafka-topics --bootstrap-server $MSK_BOOTSTRAP \
     --describe --topic t20-match-scores
   ```
   Ensure all partitions have a leader

4. **Monitor producer/consumer impact**
   - Producer: Check `record-error-rate` metric
   - Consumer: Check lag increase rate

#### Recovery
- AWS MSK auto-replaces failed brokers within 15 minutes
- If manual intervention needed, contact AWS Support with cluster ARN

---

### ALM-04: P99 API Latency > 1 Second

**Severity:** MEDIUM  
**Trigger:** P99 latency exceeds 1s for 5 consecutive minutes  
**Impact:** Slow score ingestion; user experience degradation

#### Immediate Actions
1. **Check ALB access logs**
   ```bash
   aws s3 cp s3://<alb-logs-bucket>/AWSLogs/<account>/elasticloadbalancing/ . --recursive
   grep "request_processing_time" *.log | awk '{if ($NF > 1.0) print}'
   ```

2. **Check producer ECS metrics**
   - CPU utilization (target: < 70%)
   - Memory utilization (target: < 80%)
   - Active connections to MSK

3. **Check Kafka producer metrics**
   ```bash
   curl http://<producer-ip>:8081/actuator/prometheus | grep kafka_producer
   ```
   Key metrics:
   - `kafka.producer.buffer.available.bytes` (should be > 50% of total)
   - `kafka.producer.record.send.rate`
   - `kafka.producer.request.latency.avg`

4. **Check MSK broker metrics**
   - Request latency (should be < 50ms)
   - Network throughput (should be < 80% of limit)

#### Remediation
- **High CPU:** Scale out producer service
- **Buffer exhaustion:** Increase `buffer.memory` config or reduce `linger.ms`
- **MSK overload:** Upgrade broker instance type or add more brokers

---

### ALM-05: ECS Task Count Below Threshold

**Severity:** HIGH  
**Trigger:** Running task count < desired count for 5 minutes  
**Impact:** Reduced capacity; potential lag buildup

#### Immediate Actions
1. **Check task failure reasons**
   ```bash
   aws ecs describe-tasks \
     --cluster t20-score-cluster \
     --tasks $(aws ecs list-tasks --cluster t20-score-cluster --service-name t20-score-consumer-service --query 'taskArns' --output text)
   ```

2. **Common failure causes**
   - **Image pull error:** Check ECR permissions
   - **Health check failure:** Review `/actuator/health` endpoint logs
   - **OOM kill:** Increase task memory limit
   - **Secrets Manager access denied:** Verify IAM role permissions

3. **Force new deployment**
   ```bash
   aws ecs update-service \
     --cluster t20-score-cluster \
     --service t20-score-consumer-service \
     --force-new-deployment
   ```

---

## Operational Procedures

### How to Replay a Match

**Use case:** Recover from data corruption, reprocess after bug fix, backfill missing events

```bash
# 1. Trigger replay from DynamoDB (authoritative source)
curl -X POST https://<alb-dns>/api/v1/replay/IPL-2025-MI-CSK-001

# Response: {"status": "STARTED", "matchId": "IPL-2025-MI-CSK-001", "eventCount": 300}

# 2. Monitor replay progress
curl https://<alb-dns>/api/v1/replay/IPL-2025-MI-CSK-001/status

# Response: {"status": "IN_PROGRESS", "processed": 150, "total": 300}

# 3. Verify completion
curl https://<alb-dns>/api/v1/replay/IPL-2025-MI-CSK-001/status

# Response: {"status": "COMPLETED", "processed": 300, "total": 300}
```

**⚠️ Concurrency limit:** Only 1 replay per `matchId` at a time (409 Conflict if already running)

---

### How to Read DLT Messages

```bash
# 1. Connect to MSK cluster
export MSK_BOOTSTRAP="<msk-bootstrap-servers>"

# 2. List DLT messages with headers
kafka-console-consumer \
  --bootstrap-server $MSK_BOOTSTRAP \
  --topic t20-match-scores-dlt \
  --from-beginning \
  --property print.headers=true \
  --property print.key=true \
  --property print.timestamp=true

# 3. Filter by specific match
kafka-console-consumer \
  --bootstrap-server $MSK_BOOTSTRAP \
  --topic t20-match-scores-dlt \
  --from-beginning \
  --property print.headers=true | grep "X-Match-Id:IPL-2025-MI-CSK-001"

# 4. Export to file for analysis
kafka-console-consumer \
  --bootstrap-server $MSK_BOOTSTRAP \
  --topic t20-match-scores-dlt \
  --from-beginning \
  --max-messages 100 > dlt-messages.json
```

---

### Scaling Procedures

#### Scale Consumer Service (Horizontal)

```bash
# Current: 24 tasks × 8 threads = 192 consumer threads (1:1 with partitions)
# Max safe: 48 tasks × 4 threads = 192 consumer threads (still 1:1)

# Scale to 48 tasks (for high load periods)
aws ecs update-service \
  --cluster t20-score-cluster \
  --service t20-score-consumer-service \
  --desired-count 48

# Scale back to 24 tasks (normal load)
aws ecs update-service \
  --cluster t20-score-cluster \
  --service t20-score-consumer-service \
  --desired-count 24
```

**⚠️ Warning:** Do NOT exceed 192 total threads (192 partitions). Extra threads will sit idle.

#### Scale Producer Service

```bash
# Scale to 4 tasks (high ingestion load)
aws ecs update-service \
  --cluster t20-score-cluster \
  --service t20-score-producer-service \
  --desired-count 4

# Producers are stateless; can scale freely based on CPU/memory
```

#### Increase Kafka Partitions (for 300-500 concurrent matches)

**⚠️ CRITICAL:** Partition count changes require blue/green topic migration

```bash
# 1. Create new topic with 512 partitions
kafka-topics --bootstrap-server $MSK_BOOTSTRAP \
  --create --topic t20-match-scores-v2 \
  --partitions 512 \
  --replication-factor 3 \
  --config min.insync.replicas=2 \
  --config retention.ms=604800000

# 2. Deploy producer to write to BOTH topics (dual-write)
# 3. Deploy consumer to read from new topic
# 4. Verify no lag on new topic for 24 hours
# 5. Stop producer writes to old topic
# 6. Delete old topic after retention period
```

---

### Rolling Deployment Procedure

```bash
# 1. Build and push new Docker image
docker build -t <account>.dkr.ecr.ap-southeast-2.amazonaws.com/t20-score-consumer:v1.2.0 .
docker push <account>.dkr.ecr.ap-southeast-2.amazonaws.com/t20-score-consumer:v1.2.0

# 2. Update ECS task definition with new image tag
aws ecs register-task-definition --cli-input-json file://task-def.json

# 3. Trigger rolling update
aws ecs update-service \
  --cluster t20-score-cluster \
  --service t20-score-consumer-service \
  --task-definition t20-score-consumer:42 \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=100"

# 4. Monitor deployment
aws ecs wait services-stable \
  --cluster t20-score-cluster \
  --services t20-score-consumer-service

# 5. Verify no lag increase during deployment
```

**Deployment strategy:**
- `minimumHealthyPercent=100` → no capacity reduction during rollout
- `maximumPercent=200` → double capacity temporarily (new + old tasks)
- `CooperativeStickyAssignor` → only reassigned partitions pause briefly

---

### Emergency Rollback

```bash
# 1. Identify previous stable task definition
aws ecs describe-services \
  --cluster t20-score-cluster \
  --services t20-score-consumer-service \
  --query 'services[0].deployments'

# 2. Rollback to previous version
aws ecs update-service \
  --cluster t20-score-cluster \
  --service t20-score-consumer-service \
  --task-definition t20-score-consumer:41 \
  --force-new-deployment

# 3. Monitor rollback completion
aws ecs wait services-stable \
  --cluster t20-score-cluster \
  --services t20-score-consumer-service
```

---

## Troubleshooting Guide

### Consumer Lag Not Decreasing Despite Scaling

**Symptoms:** Lag remains high even after adding more consumer tasks

**Possible causes:**
1. **DynamoDB throttling**
   - Check: CloudWatch → DynamoDB → `UserErrors` metric
   - Fix: Enable auto-scaling or switch to on-demand mode

2. **Thread count exceeds partition count**
   - Check: Total threads = task count × 8
   - Fix: Ensure threads ≤ 192 (partition count)

3. **Slow network to DynamoDB**
   - Check: VPC flow logs, NAT gateway metrics
   - Fix: Use VPC endpoints for DynamoDB

4. **GC pauses**
   - Check: JVM GC logs in CloudWatch
   - Fix: Increase heap size or tune GC settings

### Messages Stuck in Retry Topics

**Symptoms:** Retry topics have messages but they never reach DLT or succeed

**Possible causes:**
1. **Retry listener not configured**
   - Verify: `@RetryableTopic` annotation present on listener
   - Fix: Add retry configuration to consumer

2. **Backoff too long**
   - Check: Retry backoff settings (1s, 5s, 15s)
   - Fix: Adjust backoff if needed for faster retries

### Duplicate Events in DynamoDB

**Symptoms:** Same `eventId` appears multiple times in event store

**Possible causes:**
1. **Idempotency check failing**
   - Check: DynamoDB conditional write expression
   - Fix: Ensure `eventId` uniqueness constraint is enforced

2. **Offset committed before write completes**
   - Check: `AckMode.RECORD` is set
   - Fix: Change to `AckMode.RECORD` and `enable-auto-commit: false`

---

## Monitoring Dashboard URLs

| Dashboard | URL | Purpose |
|-----------|-----|---------|
| **CloudWatch Consumer Lag** | CloudWatch Console → Dashboards → T20-Consumer-Lag | Real-time lag per partition |
| **Grafana Overview** | https://grafana.<domain>/d/t20-overview | System-wide health |
| **Kafka Manager** | https://kafka-manager.<domain> | Topic/partition management |
| **X-Ray Traces** | X-Ray Console → Service Map | End-to-end request tracing |
| **ALB Metrics** | CloudWatch Console → ALB → Target Groups | Producer API health |

---

## Contact & Escalation

| Issue Type | Contact | SLA |
|------------|---------|-----|
| **P0 - System Down** | On-call engineer (PagerDuty) | 15 minutes |
| **P1 - Degraded Performance** | Team Slack #t20-alerts | 1 hour |
| **P2 - Non-critical** | JIRA ticket | 1 business day |
| **AWS Support (MSK/DynamoDB)** | AWS Enterprise Support | 15 minutes (P0) |
