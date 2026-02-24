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

## DynamoDB Production Support Procedures

### Query Operations (SELECT)

#### Query Events for a Match

```bash
# Get all events for a specific match
aws dynamodb query \
  --table-name t20-score-events \
  --key-condition-expression "matchId = :matchId" \
  --expression-attribute-values '{":matchId":{"S":"IPL-2025-MI-CSK-001"}}' \
  --limit 100

# Get events for specific inning
aws dynamodb query \
  --table-name t20-score-events \
  --key-condition-expression "matchId = :matchId AND begins_with(eventSequence, :inning)" \
  --expression-attribute-values '{
    ":matchId":{"S":"IPL-2025-MI-CSK-001"},
    ":inning":{"S":"1#"}
  }'

# Get events in a specific over
aws dynamodb query \
  --table-name t20-score-events \
  --key-condition-expression "matchId = :matchId AND eventSequence BETWEEN :start AND :end" \
  --expression-attribute-values '{
    ":matchId":{"S":"IPL-2025-MI-CSK-001"},
    ":start":{"S":"1#03#01"},
    ":end":{"S":"1#03#06"}
  }'
```

#### Get Live Score

```bash
# Get current live score for a match
aws dynamodb get-item \
  --table-name t20-live-scores \
  --key '{"matchId":{"S":"IPL-2025-MI-CSK-001"}}' \
  --consistent-read

# Get live scores for multiple matches (batch)
aws dynamodb batch-get-item \
  --request-items '{
    "t20-live-scores": {
      "Keys": [
        {"matchId": {"S": "IPL-2025-MI-CSK-001"}},
        {"matchId": {"S": "IPL-2025-RCB-KKR-001"}},
        {"matchId": {"S": "IPL-2025-DC-SRH-001"}}
      ],
      "ConsistentRead": false
    }
  }'
```

#### Scan Operations (Use Sparingly)

```bash
# ⚠️ WARNING: Scan is expensive - use only for admin tasks

# Scan with filter (find all active matches)
aws dynamodb scan \
  --table-name t20-live-scores \
  --filter-expression "matchStatus = :status" \
  --expression-attribute-values '{":status":{"S":"ACTIVE"}}' \
  --max-items 100

# Scan with projection (only specific attributes)
aws dynamodb scan \
  --table-name t20-score-events \
  --projection-expression "matchId, eventId, runs, wicket" \
  --max-items 50
```

#### Query with Pagination

```bash
# First page
aws dynamodb query \
  --table-name t20-score-events \
  --key-condition-expression "matchId = :matchId" \
  --expression-attribute-values '{":matchId":{"S":"IPL-2025-MI-CSK-001"}}' \
  --limit 50 > page1.json

# Extract LastEvaluatedKey from page1.json, then:
aws dynamodb query \
  --table-name t20-score-events \
  --key-condition-expression "matchId = :matchId" \
  --expression-attribute-values '{":matchId":{"S":"IPL-2025-MI-CSK-001"}}' \
  --exclusive-start-key '{"matchId":{"S":"IPL-2025-MI-CSK-001"},"eventSequence":{"S":"1#10#06"}}' \
  --limit 50
```

---

### Delete Operations

#### Delete Single Item

```bash
# Delete a specific event (use with caution!)
aws dynamodb delete-item \
  --table-name t20-score-events \
  --key '{
    "matchId": {"S": "IPL-2025-MI-CSK-001"},
    "eventSequence": {"S": "1#03#04"}
  }'

# Delete with condition (only if attribute matches)
aws dynamodb delete-item \
  --table-name t20-score-events \
  --key '{
    "matchId": {"S": "IPL-2025-MI-CSK-001"},
    "eventSequence": {"S": "1#03#04"}
  }' \
  --condition-expression "wicket = :false" \
  --expression-attribute-values '{":false":{"BOOL":false}}'
```

#### Delete All Events for a Match

```bash
# ⚠️ DANGEROUS: This deletes all events for a match

# Step 1: Query all items to get keys
aws dynamodb query \
  --table-name t20-score-events \
  --key-condition-expression "matchId = :matchId" \
  --expression-attribute-values '{":matchId":{"S":"IPL-2025-MI-CSK-001"}}' \
  --projection-expression "matchId, eventSequence" \
  --output json > keys.json

# Step 2: Use batch-write-item to delete (max 25 per batch)
aws dynamodb batch-write-item \
  --request-items '{
    "t20-score-events": [
      {"DeleteRequest": {"Key": {"matchId": {"S": "IPL-2025-MI-CSK-001"}, "eventSequence": {"S": "1#00#01"}}}},
      {"DeleteRequest": {"Key": {"matchId": {"S": "IPL-2025-MI-CSK-001"}, "eventSequence": {"S": "1#00#02"}}}},
      ...
    ]
  }'
```

#### Delete with TTL (Recommended for Bulk Deletes)

```bash
# Enable TTL on table (one-time setup)
aws dynamodb update-time-to-live \
  --table-name t20-score-events \
  --time-to-live-specification "Enabled=true,AttributeName=ttl"

# Set TTL on items (they'll auto-delete after expiration)
aws dynamodb update-item \
  --table-name t20-score-events \
  --key '{"matchId":{"S":"IPL-2025-MI-CSK-001"},"eventSequence":{"S":"1#03#04"}}' \
  --update-expression "SET ttl = :ttl" \
  --expression-attribute-values '{":ttl":{"N":"'$(date -d '+7 days' +%s)'"}}'
```

---

### Backup & Restore

#### On-Demand Backups

```bash
# Create on-demand backup
aws dynamodb create-backup \
  --table-name t20-score-events \
  --backup-name t20-score-events-backup-$(date +%Y%m%d-%H%M%S)

# List all backups
aws dynamodb list-backups \
  --table-name t20-score-events

# Describe backup details
aws dynamodb describe-backup \
  --backup-arn arn:aws:dynamodb:ap-southeast-2:123456789012:table/t20-score-events/backup/01234567890123-abcdef12

# Delete old backup
aws dynamodb delete-backup \
  --backup-arn arn:aws:dynamodb:ap-southeast-2:123456789012:table/t20-score-events/backup/01234567890123-abcdef12
```

#### Point-in-Time Recovery (PITR)

```bash
# Enable PITR (one-time setup)
aws dynamodb update-continuous-backups \
  --table-name t20-score-events \
  --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true

# Check PITR status
aws dynamodb describe-continuous-backups \
  --table-name t20-score-events

# Restore to specific point in time
aws dynamodb restore-table-to-point-in-time \
  --source-table-name t20-score-events \
  --target-table-name t20-score-events-restored \
  --restore-date-time $(date -u -d '2 hours ago' --iso-8601=seconds)

# Restore to latest restorable time
aws dynamodb restore-table-to-point-in-time \
  --source-table-name t20-score-events \
  --target-table-name t20-score-events-restored \
  --use-latest-restorable-time
```

#### Restore from Backup

```bash
# Restore table from backup
aws dynamodb restore-table-from-backup \
  --target-table-name t20-score-events-restored \
  --backup-arn arn:aws:dynamodb:ap-southeast-2:123456789012:table/t20-score-events/backup/01234567890123-abcdef12

# Monitor restore progress
aws dynamodb describe-table \
  --table-name t20-score-events-restored \
  --query 'Table.TableStatus'

# Once ACTIVE, verify data
aws dynamodb describe-table \
  --table-name t20-score-events-restored \
  --query 'Table.ItemCount'
```

#### Export to S3 (for archival/analytics)

```bash
# Export table to S3
aws dynamodb export-table-to-point-in-time \
  --table-arn arn:aws:dynamodb:ap-southeast-2:123456789012:table/t20-score-events \
  --s3-bucket t20-backups \
  --s3-prefix exports/score-events/$(date +%Y%m%d) \
  --export-format DYNAMODB_JSON

# Check export status
aws dynamodb describe-export \
  --export-arn arn:aws:dynamodb:ap-southeast-2:123456789012:table/t20-score-events/export/01234567890123-abcdef12

# Import from S3 (requires new table)
aws dynamodb import-table \
  --s3-bucket-source S3Bucket=t20-backups,S3KeyPrefix=exports/score-events/20250415 \
  --input-format DYNAMODB_JSON \
  --table-creation-parameters '{
    "TableName": "t20-score-events-imported",
    "KeySchema": [
      {"AttributeName": "matchId", "KeyType": "HASH"},
      {"AttributeName": "eventSequence", "KeyType": "RANGE"}
    ],
    "AttributeDefinitions": [
      {"AttributeName": "matchId", "AttributeType": "S"},
      {"AttributeName": "eventSequence", "AttributeType": "S"}
    ],
    "BillingMode": "PAY_PER_REQUEST"
  }'
```

---

### Partition Management & Monitoring

#### Check Partition Metrics

```bash
# View consumed capacity per partition (CloudWatch)
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name ConsumedReadCapacityUnits \
  --dimensions Name=TableName,Value=t20-score-events \
  --start-time $(date -u -d '1 hour ago' --iso-8601=seconds) \
  --end-time $(date -u --iso-8601=seconds) \
  --period 300 \
  --statistics Sum,Average,Maximum

# Check for throttled requests (indicates hot partition)
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name UserErrors \
  --dimensions Name=TableName,Value=t20-score-events \
  --start-time $(date -u -d '1 hour ago' --iso-8601=seconds) \
  --end-time $(date -u --iso-8601=seconds) \
  --period 300 \
  --statistics Sum
```

#### Identify Hot Partitions

```bash
# Enable CloudWatch Contributor Insights (one-time)
aws dynamodb update-contributor-insights \
  --table-name t20-score-events \
  --contributor-insights-action ENABLE

# View top partition keys by read/write activity
aws dynamodb describe-contributor-insights \
  --table-name t20-score-events

# Get detailed insights report
aws cloudwatch get-insight-rule-report \
  --rule-name DynamoDBContributorInsights-t20-score-events \
  --start-time $(date -u -d '1 hour ago' --iso-8601=seconds) \
  --end-time $(date -u --iso-8601=seconds)
```

#### Partition Key Distribution Analysis

```bash
# Sample partition keys to check distribution
aws dynamodb scan \
  --table-name t20-score-events \
  --projection-expression "matchId" \
  --max-items 1000 \
  --output json | \
  jq -r '.Items[].matchId.S' | \
  sort | uniq -c | sort -rn | head -20

# Expected: Even distribution across matchIds
# Red flag: One matchId appears 10× more than others
```

#### Partition Capacity Limits

```
Per Partition Limits:
- Max 10 GB storage
- Max 3,000 RCU (strongly consistent)
- Max 1,000 WCU

If approaching limits:
1. Redesign partition key (add randomness)
2. Use write sharding (append random suffix)
3. Split data across multiple tables
```

#### Handle Hot Partition

```bash
# Temporary fix: Increase table capacity (on-demand mode auto-handles)
aws dynamodb update-table \
  --table-name t20-score-events \
  --billing-mode PAY_PER_REQUEST

# Long-term fix: Redesign partition key
# Example: Add shard suffix to distribute load
# Old: matchId = "IPL-2025-MI-CSK-001"
# New: matchId = "IPL-2025-MI-CSK-001#0" (0-9 for 10× distribution)
```

#### Monitor Table Size & Item Count

```bash
# Get table statistics
aws dynamodb describe-table \
  --table-name t20-score-events \
  --query 'Table.{
    ItemCount:ItemCount,
    SizeBytes:TableSizeBytes,
    Status:TableStatus,
    BillingMode:BillingModeSummary.BillingMode
  }'

# Estimate cost based on size
# On-demand: $0.25/GB/month
# Provisioned: $0.25/GB/month (same storage cost)
```

#### Partition Key Best Practices

```bash
# ✅ GOOD: High cardinality, even distribution
PK = matchId (120 unique values, ~300 items each)

# ❌ BAD: Low cardinality
PK = inning (only 2 values → hot partition)

# ❌ BAD: Uneven distribution
PK = teamName (popular teams get 10× more traffic)

# ❌ BAD: Time-based without sharding
PK = date (all today's writes go to 1 partition)

# ✅ FIX: Add shard suffix
PK = date#shard (e.g., "2025-04-15#3")
```

---

## Contact & Escalation

| Issue Type | Contact | SLA |
|------------|---------|-----|
| **P0 - System Down** | On-call engineer (PagerDuty) | 15 minutes |
| **P1 - Degraded Performance** | Team Slack #t20-alerts | 1 hour |
| **P2 - Non-critical** | JIRA ticket | 1 business day |
| **AWS Support (MSK/DynamoDB)** | AWS Enterprise Support | 15 minutes (P0) |
