# Kafka Partitioning and Scaling Architecture

Based on the project's documentation ([docs/architecture-diagram.md](file:///Users/bala/code/cursor/CrickeScore/docs/architecture-diagram.md) and [docs/interview-qa.md](file:///Users/bala/code/cursor/CrickeScore/docs/interview-qa.md)), here are the answers to your questions on Kafka partitioning, consumer scaling, and cost optimization for the T20 Live Scoring system.

## 1. Why should the number of Kafka partitions equal the number of consumers?

In Kafka, **the number of partitions is the absolute upper limit on consumer parallelism**. 

> [!IMPORTANT]
> A single partition can only be consumed by **one thread** within a consumer group at any given time.

If you have **192 partitions**, you can have at most **192 active consumer threads**. 
- **1:1 Mapping (192 threads, 192 partitions):** This is the ideal maximum scale. Every thread gets exactly one partition, meaning no threads are idle, and there is no partition contention. All matches process fully in parallel.
- **Too many consumers:** If you spin up 200 threads for 192 partitions, **8 threads will sit completely idle** doing nothing, which wastes compute.
- **Fewer consumers:** If you have 10 threads for 192 partitions, each thread is assigned ~19 partitions. This works perfectly fine but reduces your maximum parallelism.

## 2. How can I know how many partitions are actively used?

Because the system uses [matchId](file:///Users/bala/code/cursor/CrickeScore/t20-score-consumer/src/test/java/com/crickscore/consumer/repository/ScoreEventMapperTest.java#48-54) as the partition key, **traffic is only routed to a partition when a match is actively being played**. 

If there are only 5 live matches happening right now, only **5 out of 192 partitions** will receive new messages. The other 187 partitions will simply be empty.

You can monitor actively used partitions in a few ways:
1. **Kafka Consumer Lag / Offset Metrics:** Through Amazon CloudWatch (or MSK metrics), you can see the rate of incoming messages per partition.
2. **Application-Level Metrics:** Use the OpenTelemetry setup to track how many unique [matchId](file:///Users/bala/code/cursor/CrickeScore/t20-score-consumer/src/test/java/com/crickscore/consumer/repository/ScoreEventMapperTest.java#48-54)s are currently processing events.

> [!WARNING]
> If you run 24 consumer pods (192 threads) 24/7, even when no matches are playing or only a few are playing, you are absolutely wasting compute resources.

## 3. How to make a better design, reduce cost, and use the best approach?

Running 100+ consumer pods (or even the full 24 pods) constantly is unnecessary. Here is the best approach to optimize costs while maintaining strict ordering and low latency:

### ✅ Approach A: ECS Autoscaling based on Consumer Lag (Recommended for this stack)
Instead of a static number of pods, you should configure **Application Autoscaling for ECS**:

1. **Base Capacity:** Run a tiny baseline of containers (e.g., 2 pods = 16 threads) during off-peak hours. These 16 threads will continuously poll all 192 partitions (each polling 12 partitions). Since most partitions are empty off-peak, this is extremely cheap and fast.
2. **Scaling Metric:** Create a CloudWatch custom metric for **Kafka Consumer Group Lag**. 
3. **Scale Out:** When multiple matches start and the 2 pods can't process events fast enough (Lag goes > 0), the ECS Service autoscales up to a maximum of 24 pods. Kafka will automatically rebalance the partitions across the new pods.
4. **Scale In:** When the matches end and lag drops to zero, ECS automatically terminates the extra pods, saving you money.

### ✅ Approach B: AWS Lambda as the Consumer (Serverless)
If you want to reduce compute costs to **$0** when no matches are playing, consider replacing the ECS Fargate consumers with AWS Lambda:
- MSK/Kafka can natively trigger Lambda functions.
- Lambda will **automatically scale** concurrent executions based on the number of partitions with active messages.
- You only pay for the exact milliseconds the Lambda function runs to process a ball. 

> [!TIP]
> **Summary:** Always create enough partitions to handle your absolute peak load (e.g., 192). However, you should **not** run 192 consumer threads all the time. Use **ECS Autoscaling** on Kafka Lag so you only pay for compute when matches are actually happening.

## 4. How can I make sure each pod runs 8 consumer threads?

To ensure each pod runs exactly 8 threads (meaning 24 pods × 8 threads = 192 partitions), you need to configure the Spring Kafka listener container's **concurrency** setting.

In your `t20-score-consumer` application, this is already configured identically:

### Step 1: Set the property in [application.yml](file:///Users/bala/code/cursor/CrickeScore/t20-score-producer/src/main/resources/application.yml)
```yaml
app-properties:
  kafka:
    consumer:
      concurrency: 8   # 8 threads × 24 ECS tasks = 192 partitions consumed in parallel
```

### Step 2: Apply it to the KafkaListenerContainerFactory
In [KafkaConsumerConfig.java](file:///Users/bala/code/cursor/CrickeScore/t20-score-consumer/src/main/java/com/crickscore/consumer/kafka/KafkaConsumerConfig.java), the factory is explicitly configured to use this property:
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> scoreEventListenerContainerFactory(...) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    // ...
    // ── Concurrency: 1 thread per partition ───────────────────────────────
    factory.setConcurrency(appProperties.getKafka().getConsumer().getConcurrency());
    // ...
    return factory;
}
```

Because of this configuration, when a single pod starts up, Spring Boot automatically creates exactly **8 physical KafkaConsumer threads**. Kafka then balances the 192 partitions evenly across all available threads. If all 24 pods are running, each of the 192 threads will be assigned exactly 1 partition.

## 5. How do I configure the number of threads in ECS? Is it required?

**No, you do not configure the number of threads directly in ECS.** 

The number of threads is strictly controlled by your application code (the Spring Boot `concurrency: 8` setting shown in Section 4). 

However, in ECS, you **must configure sufficient CPU and Memory** to support those 8 threads. 

### What you actually configure in ECS (Task Definition):
In your ECS `TaskDefinition`, you allocate vCPUs and RAM. For 8 concurrent Spring Kafka threads doing network I/O to DynamoDB, you don't strictly need 8 vCPUs because the threads spend most of their time waiting for network responses (I/O bound, not CPU bound).

A typical ECS configuration for this consumer might be:
- **CPU:** `1024` (1 vCPU) or `2048` (2 vCPUs)
- **Memory:** `2048` (2 GB) or `4096` (4 GB)

### Summary of Responsibilities:
1. **Application ([application.yml](file:///Users/bala/code/cursor/CrickeScore/t20-score-producer/src/main/resources/application.yml)):** Dictates exactly **how many threads** spin up (e.g., 8).
2. **ECS (Task Definition):** Dictates **how much CPU/Memory** the container gets to execute those threads without crashing or throttling. 
3. **ECS (Service):** Dictates **how many pods/containers** run (e.g., 24 pods).

So, you only need to ensure your ECS CPU/Memory allocation is high enough to comfortably run the 8 threads you configured in your Spring Boot application.
