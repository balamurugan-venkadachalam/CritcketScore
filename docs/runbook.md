# T20 Live Scoring – Operational Runbook

> **Status:** Placeholder – will be completed in TASK-16.2

## SLOs

| Metric | Target |
|--------|--------|
| P99 end-to-end latency (producer → consumer → DynamoDB) | < 1 second |
| System availability | 99.9% (≤ 8.7 hours/year) |
| Max consumer lag (per partition) | < 10,000 messages |
| DLT rate | 0 (alert on any) |

## Alarm Response Procedures

### ALM-01: Consumer Lag > 10,000
1. Check consumer pod health in ECS console.
2. Scale out consumer service: `aws ecs update-service --desired-count 48 ...`
3. Investigate slow consumers via CloudWatch Logs → filter `ERROR`.
4. If lag continues to grow, check MSK broker metrics (disk, network).

### ALM-02: DLT Rate > 0
1. Query DLT topic for messages: `kafka-console-consumer --topic t20-match-scores-dlt`.
2. Identify error type from message headers (`X-Error-Type`).
3. Fix root cause (retryable: restart consumer; non-retryable: fix code + redeploy).
4. Replay DLT events after fix: `POST /api/v1/replay/{matchId}`.

### ALM-03: MSK Broker Offline
1. Check MSK console → cluster health.
2. RF=3 means 1 broker failure should not impact availability.
3. If 2+ brokers down, escalate to AWS Support.

### ALM-04: P99 API Latency > 1s
1. Check ALB access logs for slow requests.
2. Check producer ECS CPU/memory metrics.
3. Check Kafka producer buffer metrics (`buffer-available-bytes`).

## How to Replay a Match

```bash
# Trigger replay for a specific match from DynamoDB
curl -X POST https://<alb-dns>/api/v1/replay/IPL-2025-MI-CSK-001

# Check replay status
curl https://<alb-dns>/api/v1/replay/IPL-2025-MI-CSK-001/status
```

## Scaling Procedure

```bash
# Scale consumer out (max 48 = 192 threads still ≤ 192 partitions, but 2 partitions/thread)
aws ecs update-service \
  --cluster t20-score-cluster \
  --service t20-score-consumer-service \
  --desired-count 48

# For 300-500 matches: increase MSK partitions to 512 (requires blue/green topic migration)
```
