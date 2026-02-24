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

### DynamoDB Backup & Disaster Recovery Strategy

#### Why Backup & DR is Critical

**Business Impact:**
- **Data Loss Prevention**: Score events are authoritative source (Kafka retention only 7 days)
- **Compliance**: Audit trail for match integrity and dispute resolution
- **Replay Capability**: Historical data required for analytics and bug fixes
- **Revenue Protection**: Lost data = lost viewer trust = lost revenue

**Regulatory Requirements:**
- Data retention: 7 years for audit purposes
- Point-in-time recovery: Restore to any second within 35 days
- Cross-region redundancy: Survive regional failures

---

#### DynamoDB as a Managed Service

**AWS Manages:**
✅ **Infrastructure**: No servers, storage, or hardware to manage  
✅ **Replication**: Automatic multi-AZ replication (3 copies)  
✅ **Durability**: 99.999999999% (11 nines) durability  
✅ **Availability**: 99.99% SLA with multi-AZ deployment  
✅ **Backups**: Automated continuous backups with PITR  
✅ **Encryption**: At-rest and in-transit encryption managed  
✅ **Patching**: Zero-downtime updates and security patches

**You Manage:**
- Backup retention policies
- Restore procedures and testing
- Cross-region replication (if needed)
- Backup monitoring and alerting
- DR runbooks and testing

**Cost Savings vs Self-Managed:**
```
Self-Managed PostgreSQL:
- 3× EC2 instances (multi-AZ): $180/month
- EBS storage (3× 100GB): $30/month
- Backup storage (S3): $25/month
- DBA time (10 hours/month): $1,000/month
Total: ~$1,235/month

DynamoDB Managed:
- On-demand capacity: $9/month
- Backup storage: $2.50/month (PITR included)
- DBA time: 0 hours
Total: ~$11.50/month

Savings: $1,223/month (99% reduction)
```

---

#### Disaster Recovery Strategy

**Recovery Objectives:**

| Metric | Target | Current Implementation |
|--------|--------|------------------------|
| **RTO** (Recovery Time Objective) | 15 minutes | PITR restore + DNS switch |
| **RPO** (Recovery Point Objective) | 0 seconds | Multi-AZ replication (synchronous) |
| **Data Durability** | 99.999999999% | DynamoDB managed (11 nines) |
| **Availability** | 99.99% | Multi-AZ automatic failover |

**DR Scenarios & Response:**

##### Scenario 1: Single AZ Failure

**Impact:** None (automatic failover)

**DynamoDB Response:**
- Automatic failover to healthy AZ (< 1 second)
- No data loss (synchronous replication)
- No manual intervention required

**Monitoring:**
```bash
# Check table status (should remain ACTIVE)
aws dynamodb describe-table \
  --table-name t20-score-events \
  --query 'Table.TableStatus'
```

##### Scenario 2: Accidental Data Deletion

**Impact:** Data loss, requires restore

**Response Time:** 15 minutes

**Recovery Steps:**
```bash
# 1. Stop writes to prevent further damage
aws ecs update-service \
  --cluster t20-score-cluster \
  --service t20-score-consumer-service \
  --desired-count 0

# 2. Identify deletion time
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name ConsumedWriteCapacityUnits \
  --dimensions Name=TableName,Value=t20-score-events \
  --start-time $(date -u -d '1 hour ago' --iso-8601=seconds) \
  --end-time $(date -u --iso-8601=seconds) \
  --period 60 \
  --statistics Sum

# 3. Restore to point before deletion
aws dynamodb restore-table-to-point-in-time \
  --source-table-name t20-score-events \
  --target-table-name t20-score-events-restored \
  --restore-date-time "2025-04-15T14:30:00Z"

# 4. Verify restored data
aws dynamodb describe-table \
  --table-name t20-score-events-restored \
  --query 'Table.ItemCount'

# 5. Switch application to restored table (update env vars)
# 6. Resume consumer service
```

##### Scenario 3: Table Corruption

**Impact:** Data integrity compromised

**Response Time:** 30 minutes

**Recovery Steps:**
```bash
# 1. Create backup of current state (for forensics)
aws dynamodb create-backup \
  --table-name t20-score-events \
  --backup-name t20-score-events-corrupted-$(date +%Y%m%d-%H%M%S)

# 2. Restore from last known good backup
aws dynamodb restore-table-from-backup \
  --target-table-name t20-score-events-clean \
  --backup-arn arn:aws:dynamodb:ap-southeast-2:123456789012:table/t20-score-events/backup/01234567890123-abcdef12

# 3. Validate data integrity
# 4. Switch application to clean table
# 5. Investigate root cause from corrupted backup
```

##### Scenario 4: Regional Failure

**Impact:** Complete region unavailable

**Response Time:** 1 hour (manual failover)

**Prerequisites:**
- Global Tables enabled (multi-region replication)
- Route53 health checks configured
- Cross-region ALB setup

**Recovery Steps:**
```bash
# 1. Verify secondary region health
aws dynamodb describe-table \
  --table-name t20-score-events \
  --region us-east-1

# 2. Update Route53 to point to secondary region
aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch '{
    "Changes": [{
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "api.t20scoring.com",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "Z0987654321XYZ",
          "DNSName": "t20-alb-us-east-1.elb.amazonaws.com",
          "EvaluateTargetHealth": true
        }
      }
    }]
  }'

# 3. Monitor application in secondary region
# 4. Communicate status to stakeholders
```

---

#### Automated Backup Scheduling

**Backup Strategy:**

| Backup Type | Frequency | Retention | Purpose |
|-------------|-----------|-----------|---------|
| **PITR (Continuous)** | Automatic | 35 days | Point-in-time recovery, accidental deletes |
| **Daily Snapshots** | 00:00 UTC | 90 days | Compliance, long-term retention |
| **Weekly Snapshots** | Sunday 00:00 UTC | 1 year | Quarterly audits, historical analysis |
| **Pre-Deployment** | Before each deploy | 7 days | Rollback safety net |
| **Export to S3** | Monthly | 7 years | Archival, compliance, analytics |

**Implementation:**

##### 1. Enable Point-in-Time Recovery (PITR)

```bash
# Enable PITR on all tables (one-time setup)
for table in t20-score-events t20-live-scores t20-replay-state; do
  aws dynamodb update-continuous-backups \
    --table-name $table \
    --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true
  echo "PITR enabled for $table"
done

# Verify PITR status
aws dynamodb describe-continuous-backups \
  --table-name t20-score-events \
  --query 'ContinuousBackupsDescription.PointInTimeRecoveryDescription'
```

**PITR Benefits:**
- ✅ Automatic, no manual intervention
- ✅ Restore to any second within 35 days
- ✅ No performance impact
- ✅ No additional cost (included in DynamoDB pricing)

##### 2. Automated Daily Backups (EventBridge + Lambda)

**Lambda Function:**
```python
# lambda/dynamodb-backup.py
import boto3
from datetime import datetime

dynamodb = boto3.client('dynamodb')

def lambda_handler(event, context):
    tables = ['t20-score-events', 't20-live-scores', 't20-replay-state']
    timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
    
    for table_name in tables:
        backup_name = f"{table_name}-daily-{timestamp}"
        
        response = dynamodb.create_backup(
            TableName=table_name,
            BackupName=backup_name
        )
        
        print(f"Created backup: {backup_name}")
        print(f"Backup ARN: {response['BackupDetails']['BackupArn']}")
    
    return {'statusCode': 200, 'body': 'Backups created successfully'}
```

**EventBridge Rule:**
```bash
# Create daily backup schedule (00:00 UTC)
aws events put-rule \
  --name dynamodb-daily-backup \
  --schedule-expression "cron(0 0 * * ? *)" \
  --description "Daily DynamoDB backup at midnight UTC"

# Add Lambda as target
aws events put-targets \
  --rule dynamodb-daily-backup \
  --targets "Id"="1","Arn"="arn:aws:lambda:ap-southeast-2:123456789012:function:dynamodb-backup"
```

##### 3. Automated Backup Cleanup (Lifecycle Management)

```python
# lambda/dynamodb-backup-cleanup.py
import boto3
from datetime import datetime, timedelta

dynamodb = boto3.client('dynamodb')

def lambda_handler(event, context):
    # Delete daily backups older than 90 days
    cutoff_date = datetime.now() - timedelta(days=90)
    
    response = dynamodb.list_backups(
        TableName='t20-score-events',
        TimeRangeLowerBound=datetime(2020, 1, 1),
        TimeRangeUpperBound=cutoff_date
    )
    
    for backup in response['BackupSummaries']:
        if 'daily' in backup['BackupName']:
            dynamodb.delete_backup(BackupArn=backup['BackupArn'])
            print(f"Deleted old backup: {backup['BackupName']}")
    
    return {'statusCode': 200, 'body': 'Cleanup completed'}
```

##### 4. Monthly Export to S3 (Long-Term Archival)

```bash
# EventBridge rule for monthly export (1st of each month)
aws events put-rule \
  --name dynamodb-monthly-export \
  --schedule-expression "cron(0 2 1 * ? *)" \
  --description "Monthly DynamoDB export to S3"

# Lambda to trigger export
aws lambda create-function \
  --function-name dynamodb-monthly-export \
  --runtime python3.11 \
  --handler index.lambda_handler \
  --role arn:aws:iam::123456789012:role/lambda-dynamodb-export \
  --code file://lambda-export.zip
```

**Export Lambda:**
```python
import boto3
from datetime import datetime

dynamodb = boto3.client('dynamodb')

def lambda_handler(event, context):
    table_arn = 'arn:aws:dynamodb:ap-southeast-2:123456789012:table/t20-score-events'
    s3_bucket = 't20-backups'
    s3_prefix = f"exports/score-events/{datetime.now().strftime('%Y%m')}"
    
    response = dynamodb.export_table_to_point_in_time(
        TableArn=table_arn,
        S3Bucket=s3_bucket,
        S3Prefix=s3_prefix,
        ExportFormat='DYNAMODB_JSON'
    )
    
    print(f"Export started: {response['ExportDescription']['ExportArn']}")
    return {'statusCode': 200, 'exportArn': response['ExportDescription']['ExportArn']}
```

---

#### Operational Requirements

##### 1. Backup Monitoring & Alerting

**CloudWatch Alarms:**

```bash
# Alert if PITR is disabled
aws cloudwatch put-metric-alarm \
  --alarm-name dynamodb-pitr-disabled \
  --alarm-description "Alert if PITR is disabled on critical tables" \
  --metric-name PointInTimeRecoveryStatus \
  --namespace AWS/DynamoDB \
  --statistic Average \
  --period 300 \
  --evaluation-periods 1 \
  --threshold 0 \
  --comparison-operator LessThanThreshold \
  --alarm-actions arn:aws:sns:ap-southeast-2:123456789012:critical-alerts

# Alert if backup fails
aws cloudwatch put-metric-alarm \
  --alarm-name dynamodb-backup-failure \
  --alarm-description "Alert if daily backup fails" \
  --metric-name BackupCreationFailures \
  --namespace Custom/DynamoDB \
  --statistic Sum \
  --period 86400 \
  --evaluation-periods 1 \
  --threshold 0 \
  --comparison-operator GreaterThanThreshold \
  --alarm-actions arn:aws:sns:ap-southeast-2:123456789012:critical-alerts
```

**Daily Backup Verification:**

```bash
# Check if today's backup exists
#!/bin/bash
TODAY=$(date +%Y%m%d)
BACKUP_COUNT=$(aws dynamodb list-backups \
  --table-name t20-score-events \
  --time-range-lower-bound $(date -d 'today 00:00:00' +%s) \
  --query 'BackupSummaries[?contains(BackupName, `'$TODAY'`)] | length(@)')

if [ "$BACKUP_COUNT" -eq 0 ]; then
  echo "ERROR: No backup found for today"
  # Send alert to PagerDuty
  exit 1
else
  echo "SUCCESS: Found $BACKUP_COUNT backup(s) for today"
fi
```

##### 2. Restore Testing (Monthly)

**Test Procedure:**

```bash
# Monthly DR drill (1st Sunday of each month)
#!/bin/bash

# 1. Create test restore
aws dynamodb restore-table-to-point-in-time \
  --source-table-name t20-score-events \
  --target-table-name t20-score-events-dr-test \
  --use-latest-restorable-time

# 2. Wait for restore to complete
aws dynamodb wait table-exists --table-name t20-score-events-dr-test

# 3. Verify item count matches
ORIGINAL_COUNT=$(aws dynamodb describe-table \
  --table-name t20-score-events \
  --query 'Table.ItemCount' --output text)

RESTORED_COUNT=$(aws dynamodb describe-table \
  --table-name t20-score-events-dr-test \
  --query 'Table.ItemCount' --output text)

if [ "$ORIGINAL_COUNT" -eq "$RESTORED_COUNT" ]; then
  echo "✅ DR test passed: Item counts match ($ORIGINAL_COUNT)"
else
  echo "❌ DR test failed: Item count mismatch (Original: $ORIGINAL_COUNT, Restored: $RESTORED_COUNT)"
  exit 1
fi

# 4. Sample data validation
aws dynamodb query \
  --table-name t20-score-events-dr-test \
  --key-condition-expression "matchId = :matchId" \
  --expression-attribute-values '{":matchId":{"S":"IPL-2025-MI-CSK-001"}}' \
  --limit 10

# 5. Cleanup test table
aws dynamodb delete-table --table-name t20-score-events-dr-test

# 6. Document results
echo "DR test completed at $(date)" >> /var/log/dr-tests.log
```

##### 3. Backup Retention Policy

**Retention Schedule:**

```bash
# Automated retention enforcement (weekly cleanup)
#!/bin/bash

# Delete daily backups older than 90 days
aws dynamodb list-backups \
  --table-name t20-score-events \
  --time-range-upper-bound $(date -d '90 days ago' +%s) \
  --query 'BackupSummaries[?contains(BackupName, `daily`)].BackupArn' \
  --output text | \
while read arn; do
  aws dynamodb delete-backup --backup-arn "$arn"
  echo "Deleted backup: $arn"
done

# Keep weekly backups for 1 year
aws dynamodb list-backups \
  --table-name t20-score-events \
  --time-range-upper-bound $(date -d '365 days ago' +%s) \
  --query 'BackupSummaries[?contains(BackupName, `weekly`)].BackupArn' \
  --output text | \
while read arn; do
  aws dynamodb delete-backup --backup-arn "$arn"
  echo "Deleted backup: $arn"
done
```

##### 4. Backup Cost Monitoring

```bash
# Calculate monthly backup costs
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name BackupStorageSize \
  --dimensions Name=TableName,Value=t20-score-events \
  --start-time $(date -d '30 days ago' --iso-8601=seconds) \
  --end-time $(date --iso-8601=seconds) \
  --period 2592000 \
  --statistics Average

# Backup storage cost: $0.10/GB/month
# PITR: Included (no additional cost)
# Export to S3: $0.11/GB (one-time) + S3 storage ($0.023/GB/month)
```

##### 5. Documentation Requirements

**Maintain:**
- ✅ Backup schedule and retention policy
- ✅ Restore procedures (this runbook)
- ✅ DR test results (monthly)
- ✅ RTO/RPO metrics (quarterly review)
- ✅ Backup cost analysis (monthly)
- ✅ Incident post-mortems (when DR invoked)

**Review Cadence:**
- Weekly: Verify backups completed successfully
- Monthly: DR drill and restore testing
- Quarterly: Review RTO/RPO targets
- Annually: Full DR plan review and update

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
