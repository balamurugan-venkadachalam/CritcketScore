# T20 Live Scoring – Architecture Diagram

> **Status:** Placeholder – will be completed in TASK-16.1

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
    Producer -->|Kafka produce\nkey=matchId| MSK
    MSK -->|Kafka consume\ngroup=t20-score-consumer-group| Consumer
    Consumer -->|PutItem / UpdateItem| DDB
    Consumer -->|Retry / DLQ| MSK
    Producer & Consumer --> CW
    Producer & Consumer --> OTEL
```

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Message key | `matchId` | Guarantees per-match ordering on same partition |
| Partitions | 192 | ~1.5× peak concurrent matches (120), power-of-2 friendliness |
| Consumer threads | 192 (24 pods × 8) | 1:1 mapping with partitions |
| Replication | RF=3, min.insync=2 | Survives 1 AZ failure |
| Storage | DynamoDB PAY_PER_REQUEST | No capacity planning, scales with burst traffic |
