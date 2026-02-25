# Kafka Producer & Consumer Best Practices

Comprehensive guide for production-ready Kafka implementation in the T20 Live Scoring system.

---

## Table of Contents

1. [Consumer Best Practices](#consumer-best-practices)
2. [Producer Best Practices](#producer-best-practices)
3. [Configuration Reference](#configuration-reference)
4. [Monitoring & Observability](#monitoring--observability)
5. [Common Pitfalls](#common-pitfalls)

---

## Consumer Best Practices

### 1. Idempotency (Prevent Duplicates)

**Problem:** Kafka guarantees at-least-once delivery → duplicates possible during rebalances, retries, or network issues.

**Solution:** Idempotent writes using conditional expressions

```java
@Service
public class ScoreEventConsumer {
    
    @Autowired
    private DynamoDbClient dynamoDb;
    
    @KafkaListener(topics = "score-events", groupId = "score-consumer-group")
    public void consume(ScoreEvent event) {
        try {
            // Use eventId as idempotency key
            dynamoDb.putItem(PutItemRequest.builder()
                .tableName("t20-score-events")
                .item(toAttributeMap(event))
                .conditionExpression("attribute_not_exists(eventId)")  // ✅ Only insert if new
                .build());
                
            log.info("Event processed: {}", event.getEventId());
            
        } catch (ConditionalCheckFailedException e) {
            // Already processed, skip silently
            log.warn("Duplicate event ignored: {}", event.getEventId());
        }
    }
}
```

**Alternative: Database-level deduplication**
```java
// Use unique constraint on eventId column (PostgreSQL)
// Or use DynamoDB conditional writes as shown above
```

---

### 2. Offset Management (Exactly-Once Semantics)

**Best Practice:** Commit offset **after** successful processing

```java
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(AckMode.RECORD);  // ✅ Commit per record
        
        return factory;
    }
}

@Service
public class ScoreEventConsumer {
    
    @KafkaListener(topics = "score-events")
    public void consume(ScoreEvent event, Acknowledgment ack) {
        try {
            // 1. Process message
            dynamoDb.putItem(event);
            
            // 2. Update live score
            liveScoreService.update(event);
            
            // 3. Commit offset ONLY after successful processing
            ack.acknowledge();  // ✅ Manual commit
            
        } catch (Exception e) {
            // Don't acknowledge → message will be redelivered
            log.error("Processing failed, will retry: {}", event.getEventId(), e);
            throw e;  // Trigger retry mechanism
        }
    }
}
```

**Configuration:**
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # ✅ Manual control
      ack-mode: RECORD           # ✅ Commit per record (not batch)
      isolation-level: read_committed  # ✅ Only read committed messages
```

---

### 3. Poison Pill Handling

**Problem:** Malformed messages block partition progress

**Solution:** Two-layer defense (catch + exclude)

```java
@Service
public class ScoreEventConsumer {
    
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        exclude = {JsonProcessingException.class, ValidationException.class},  // ✅ Don't retry permanent errors
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = "score-events")
    public void consume(String message) {
        try {
            // ✅ Layer 1: Catch inside listener
            ScoreEvent event = objectMapper.readValue(message, ScoreEvent.class);
            
            // ✅ Validate business rules
            validateEvent(event);
            
            // Process event
            processEvent(event);
            
        } catch (JsonProcessingException e) {
            // Poison pill detected - log and skip
            log.error("Malformed JSON, skipping: {}", message, e);
            // Automatically acknowledged, partition advances
            
        } catch (ValidationException e) {
            // Invalid data - log and skip
            log.error("Validation failed, skipping: {}", message, e);
        }
    }
    
    private void validateEvent(ScoreEvent event) throws ValidationException {
        if (event.getMatchId() == null || event.getEventId() == null) {
            throw new ValidationException("Missing required fields");
        }
        if (event.getRuns() < 0 || event.getRuns() > 6) {
            throw new ValidationException("Invalid runs value: " + event.getRuns());
        }
    }
}
```

**Why this works:**
- ✅ Exception caught before reaching Kafka framework
- ✅ Message acknowledged automatically
- ✅ Partition advances (no blocking)
- ✅ Layer 2 (exclude) prevents retry if exception escapes

---

### 4. Graceful Shutdown (No Data Loss)

**Problem:** Container restart loses in-flight messages

**Solution:** Drain messages before shutdown

```java
@Component
public class GracefulShutdown {
    
    @Autowired
    private KafkaListenerEndpointRegistry registry;
    
    @PreDestroy
    public void onShutdown() {
        log.info("Graceful shutdown initiated - draining messages");
        
        // 1. Stop accepting new messages
        registry.getListenerContainers().forEach(container -> {
            log.info("Stopping container: {}", container.getListenerId());
            container.stop();
        });
        
        // 2. Wait for in-flight messages to complete (max 30s)
        long startTime = System.currentTimeMillis();
        registry.getListenerContainers().forEach(container -> {
            try {
                container.stop(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("Container stopped after {}ms: {}", elapsed, container.getListenerId());
                });
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        });
        
        log.info("All messages drained, shutdown complete");
    }
}
```

**ECS Task Definition:**
```json
{
  "containerDefinitions": [{
    "name": "score-consumer",
    "stopTimeout": 60,
    "healthCheck": {
      "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
      "interval": 30,
      "timeout": 5,
      "retries": 3,
      "startPeriod": 60
    }
  }]
}
```

---

### 5. Backpressure Handling (Prevent OOM)

**Problem:** Consumer fetches faster than processor can handle → memory overflow

**Solution:** Limit concurrent processing and batch sizes

```java
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConcurrency(4);  // ✅ 4 threads per partition
        factory.getContainerProperties().setPollTimeout(3000);
        factory.getContainerProperties().setIdleBetweenPolls(100);  // ✅ Throttle polling
        
        return factory;
    }
}
```

**Configuration:**
```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 100      # ✅ Limit records per poll
      fetch-max-wait: 500        # ✅ Don't wait too long for batch
      fetch-min-size: 1024       # ✅ Minimum bytes to fetch
      max-partition-fetch-bytes: 1048576  # ✅ 1MB max per partition
```

---

### 6. Circuit Breaker (Downstream Failures)

**Problem:** DynamoDB throttling causes cascading failures

**Solution:** Circuit breaker with fallback to DLT

```java
@Service
public class ScoreEventService {
    
    private final CircuitBreaker circuitBreaker;
    private final KafkaTemplate<String, ScoreEvent> kafkaTemplate;
    
    public ScoreEventService() {
        this.circuitBreaker = CircuitBreaker.of("dynamodb", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)           // Open if 50% fail
            .waitDurationInOpenState(Duration.ofSeconds(30))  // Wait 30s before retry
            .slidingWindowSize(10)              // Track last 10 calls
            .permittedNumberOfCallsInHalfOpenState(5)  // Test with 5 calls
            .build());
            
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker state changed: {}", event.getStateTransition()));
    }
    
    @KafkaListener(topics = "score-events")
    public void consume(ScoreEvent event) {
        Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            dynamoDb.putItem(event);
            return null;
        }))
        .onSuccess(result -> log.debug("Event processed: {}", event.getEventId()))
        .onFailure(throwable -> {
            if (throwable instanceof CallNotPermittedException) {
                // Circuit open, send to DLT immediately
                log.error("Circuit open, sending to DLT: {}", event.getEventId());
                kafkaTemplate.send("score-events-dlt", event);
            } else {
                // Transient error, will retry
                log.error("Processing failed: {}", event.getEventId(), throwable);
                throw new RuntimeException(throwable);
            }
        });
    }
}
```

**Dependencies:**
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot2</artifactId>
    <version>2.0.2</version>
</dependency>
```

---

### 7. Dead Letter Topic (DLT) Strategy

**Best Practice:** Structured DLT with enriched metadata

```java
@Service
public class ScoreEventConsumer {
    
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 15000),
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        include = {DynamoDbException.class, TimeoutException.class}  // Only retry transient errors
    )
    @KafkaListener(topics = "score-events")
    public void consume(ScoreEvent event) {
        processEvent(event);
    }
    
    @DltHandler
    public void handleDlt(ScoreEvent event, 
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String error,
                          @Header(KafkaHeaders.EXCEPTION_STACKTRACE) String stacktrace,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.OFFSET) long offset) {
        
        // ✅ Enrich with metadata
        DltRecord dltRecord = DltRecord.builder()
            .originalEvent(event)
            .errorMessage(error)
            .stackTrace(stacktrace)
            .originalTopic(topic)
            .originalOffset(offset)
            .timestamp(Instant.now())
            .retryCount(3)
            .build();
        
        // ✅ Store in DynamoDB for investigation
        dynamoDb.putItem(PutItemRequest.builder()
            .tableName("t20-dlt-records")
            .item(toAttributeMap(dltRecord))
            .build());
        
        // ✅ Alert on-call engineer
        sns.publish(PublishRequest.builder()
            .topicArn("arn:aws:sns:ap-southeast-2:123456789012:critical-alerts")
            .subject("DLT Message Received")
            .message(String.format("Event %s failed after 3 retries: %s", 
                event.getEventId(), error))
            .build());
        
        log.error("Event sent to DLT: {}", event.getEventId());
    }
}
```

---

### 8. Rebalance Handling (Partition Reassignment)

**Problem:** Rebalance can cause duplicate processing or data loss

**Solution:** Implement rebalance listener

```java
@Component
public class RebalanceListener implements ConsumerRebalanceListener {
    
    private final Map<TopicPartition, Long> currentOffsets = new ConcurrentHashMap<>();
    
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        log.info("Partitions revoked: {}", partitions);
        
        // ✅ Flush any pending writes
        flushPendingWrites();
        
        // ✅ Commit current offsets
        commitOffsets(partitions);
        
        // ✅ Release resources
        releaseResources(partitions);
    }
    
    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        log.info("Partitions assigned: {}", partitions);
        
        // ✅ Initialize state for new partitions
        partitions.forEach(partition -> {
            currentOffsets.put(partition, 0L);
        });
        
        // ✅ Seek to last committed offset if needed
        // consumer.seek(partition, lastCommittedOffset);
    }
    
    private void flushPendingWrites() {
        // Flush any batched writes to DynamoDB
        log.info("Flushing pending writes");
    }
    
    private void commitOffsets(Collection<TopicPartition> partitions) {
        // Commit offsets for revoked partitions
        log.info("Committing offsets for: {}", partitions);
    }
    
    private void releaseResources(Collection<TopicPartition> partitions) {
        // Clean up partition-specific resources
        partitions.forEach(currentOffsets::remove);
    }
}
```

---

### 9. Schema Validation (Prevent Bad Data)

**Best Practice:** Validate early, fail fast

```java
@Service
public class ScoreEventConsumer {
    
    private final Validator validator;
    
    @KafkaListener(topics = "score-events")
    public void consume(String message) {
        try {
            // ✅ Step 1: Parse JSON
            ScoreEvent event = objectMapper.readValue(message, ScoreEvent.class);
            
            // ✅ Step 2: Validate with Bean Validation
            Set<ConstraintViolation<ScoreEvent>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                log.error("Validation failed: {}", violations);
                return;  // Skip invalid event
            }
            
            // ✅ Step 3: Business rule validation
            validateBusinessRules(event);
            
            // ✅ Step 4: Process valid event
            processEvent(event);
            
        } catch (JsonProcessingException e) {
            log.error("Malformed JSON: {}", message, e);
            // Skip poison pill
        } catch (ValidationException e) {
            log.error("Business validation failed: {}", message, e);
            // Skip invalid data
        }
    }
    
    private void validateBusinessRules(ScoreEvent event) throws ValidationException {
        // Validate runs
        if (event.getRuns() < 0 || event.getRuns() > 6) {
            throw new ValidationException("Invalid runs: " + event.getRuns());
        }
        
        // Validate over/ball
        String[] parts = event.getEventSequence().split("#");
        int over = Integer.parseInt(parts[1]);
        int ball = Integer.parseInt(parts[2]);
        
        if (over < 0 || over > 20) {
            throw new ValidationException("Invalid over: " + over);
        }
        if (ball < 1 || ball > 6) {
            throw new ValidationException("Invalid ball: " + ball);
        }
    }
}

// ScoreEvent with Bean Validation
public class ScoreEvent {
    @NotNull
    private String matchId;
    
    @NotNull
    private String eventId;
    
    @NotNull
    @Pattern(regexp = "\\d+#\\d+#\\d+")
    private String eventSequence;
    
    @Min(0) @Max(6)
    private int runs;
    
    // getters/setters
}
```

---

### 10. Resource Cleanup (Prevent Leaks)

**Best Practice:** Use try-with-resources and proper cleanup

```java
@Service
public class ScoreEventConsumer {
    
    private final Timer latencyTimer;
    
    @KafkaListener(topics = "score-events")
    public void consume(ScoreEvent event) {
        // ✅ Auto-close timer context
        try (Timer.Context timer = latencyTimer.time()) {
            
            processEvent(event);
            
        } catch (Exception e) {
            log.error("Processing failed", e);
            throw e;
        }
        // Timer automatically stopped, even on exception
    }
    
    @PreDestroy
    public void cleanup() {
        // ✅ Clean up resources on shutdown
        log.info("Cleaning up consumer resources");
        // Close connections, flush caches, etc.
    }
}
```

---

## Producer Best Practices

### 1. Idempotent Producer (Prevent Duplicates)

**Problem:** Network retries can cause duplicate messages

**Solution:** Enable idempotent producer

```java
@Configuration
public class KafkaProducerConfig {
    
    @Bean
    public ProducerFactory<String, ScoreEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // ✅ Enable idempotence (exactly-once semantics)
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");  // Required for idempotence
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);  // Retry indefinitely
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);  // Max 5 for idempotence
        
        return new DefaultKafkaProducerFactory<>(config);
    }
}
```

**Configuration:**
```yaml
spring:
  kafka:
    producer:
      acks: all                    # ✅ Wait for all replicas
      retries: 2147483647          # ✅ Retry indefinitely
      enable-idempotence: true     # ✅ Exactly-once semantics
      max-in-flight-requests-per-connection: 5
```

---

### 2. Async Send with Callback (Error Handling)

**Best Practice:** Always handle send failures

```java
@Service
public class ScoreEventProducer {
    
    @Autowired
    private KafkaTemplate<String, ScoreEvent> kafkaTemplate;
    
    @Autowired
    private MetricRegistry metrics;
    
    public void sendEvent(ScoreEvent event) {
        Timer.Context timer = metrics.timer("kafka.producer.latency").time();
        
        // ✅ Async send with callback
        kafkaTemplate.send("score-events", event.getMatchId(), event)
            .addCallback(
                // Success callback
                result -> {
                    timer.stop();
                    RecordMetadata metadata = result.getRecordMetadata();
                    log.info("Event sent: topic={}, partition={}, offset={}, key={}", 
                        metadata.topic(), 
                        metadata.partition(), 
                        metadata.offset(),
                        event.getEventId());
                    
                    metrics.counter("kafka.producer.success").inc();
                },
                
                // Failure callback
                ex -> {
                    timer.stop();
                    log.error("Failed to send event: {}", event.getEventId(), ex);
                    
                    metrics.counter("kafka.producer.failure").inc();
                    
                    // ✅ Store in fallback queue or database
                    storeFallback(event);
                    
                    // ✅ Alert on-call
                    alertFailure(event, ex);
                }
            );
    }
    
    private void storeFallback(ScoreEvent event) {
        // Store in DynamoDB or SQS for manual retry
        dynamoDb.putItem("t20-failed-events", event);
    }
    
    private void alertFailure(ScoreEvent event, Throwable ex) {
        sns.publish("critical-alerts", 
            String.format("Failed to send event %s: %s", event.getEventId(), ex.getMessage()));
    }
}
```

---

### 3. Partitioning Strategy (Even Distribution)

**Best Practice:** Use meaningful partition keys

```java
@Service
public class ScoreEventProducer {
    
    public void sendEvent(ScoreEvent event) {
        // ✅ Use matchId as partition key for ordering
        // All events for same match go to same partition (ordered)
        String partitionKey = event.getMatchId();
        
        kafkaTemplate.send("score-events", partitionKey, event);
    }
    
    // Alternative: Custom partitioner for better distribution
    public static class MatchPartitioner implements Partitioner {
        
        @Override
        public int partition(String topic, Object key, byte[] keyBytes, 
                           Object value, byte[] valueBytes, Cluster cluster) {
            
            int partitionCount = cluster.partitionCountForTopic(topic);
            
            // ✅ Hash matchId to distribute evenly
            String matchId = (String) key;
            int hash = Math.abs(matchId.hashCode());
            
            return hash % partitionCount;
        }
    }
}
```

**Configuration:**
```yaml
spring:
  kafka:
    producer:
      properties:
        partitioner.class: com.t20.kafka.MatchPartitioner  # Custom partitioner
```

---

### 4. Batching & Compression (Performance)

**Best Practice:** Batch messages and compress for throughput

```java
@Configuration
public class KafkaProducerConfig {
    
    @Bean
    public ProducerFactory<String, ScoreEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // ✅ Batching configuration
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);  // 16KB batch
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);      // Wait 10ms for batch
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);  // 32MB buffer
        
        // ✅ Compression (reduces network I/O)
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");  // or "lz4", "gzip"
        
        return new DefaultKafkaProducerFactory<>(config);
    }
}
```

**Compression Comparison:**
| Type | Speed | Ratio | Use Case |
|------|-------|-------|----------|
| **snappy** | Fast | Good | Real-time (recommended) |
| **lz4** | Fastest | Good | Ultra-low latency |
| **gzip** | Slow | Best | Batch processing |
| **zstd** | Medium | Better | Balanced |

---

### 5. Timeout & Retry Configuration

**Best Practice:** Configure appropriate timeouts

```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 2147483647          # Retry indefinitely
      request-timeout-ms: 30000    # 30s request timeout
      delivery-timeout-ms: 120000  # 2min total delivery timeout
      max-block-ms: 60000          # 1min max block on send()
      
      properties:
        retry.backoff.ms: 100      # Wait 100ms between retries
        reconnect.backoff.ms: 50   # Wait 50ms before reconnect
        reconnect.backoff.max.ms: 1000  # Max 1s reconnect backoff
```

---

### 6. Message Ordering Guarantees

**Best Practice:** Ensure ordering per partition

```java
@Service
public class ScoreEventProducer {
    
    public CompletableFuture<SendResult<String, ScoreEvent>> sendEvent(ScoreEvent event) {
        // ✅ Use matchId as key to guarantee ordering
        // All events for same match go to same partition in order
        
        ProducerRecord<String, ScoreEvent> record = new ProducerRecord<>(
            "score-events",           // topic
            null,                     // partition (auto-selected by key)
            event.getMatchId(),       // key (determines partition)
            event                     // value
        );
        
        // ✅ Add headers for tracing
        record.headers()
            .add("eventId", event.getEventId().getBytes())
            .add("timestamp", String.valueOf(System.currentTimeMillis()).getBytes())
            .add("source", "producer-api".getBytes());
        
        return kafkaTemplate.send(record);
    }
}
```

**Configuration:**
```yaml
spring:
  kafka:
    producer:
      # ✅ Max 1 in-flight request for strict ordering
      max-in-flight-requests-per-connection: 1  # Strict ordering (slower)
      # OR
      max-in-flight-requests-per-connection: 5  # Better throughput with idempotence
      enable-idempotence: true                  # Maintains ordering with 5 in-flight
```

---

### 7. Transaction Support (Exactly-Once)

**Best Practice:** Use transactions for multi-topic writes

```java
@Service
public class ScoreEventProducer {
    
    @Autowired
    private KafkaTemplate<String, ScoreEvent> kafkaTemplate;
    
    @Transactional("kafkaTransactionManager")
    public void sendEventWithTransaction(ScoreEvent event) {
        try {
            // ✅ All sends in transaction (atomic)
            kafkaTemplate.send("score-events", event);
            kafkaTemplate.send("score-events-backup", event);
            kafkaTemplate.send("analytics-events", event);
            
            // All succeed or all fail (exactly-once)
            
        } catch (Exception e) {
            log.error("Transaction failed, rolling back", e);
            throw e;  // Rollback transaction
        }
    }
}

@Configuration
public class KafkaProducerConfig {
    
    @Bean
    public ProducerFactory<String, ScoreEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // ✅ Enable transactions
        config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "score-producer-" + UUID.randomUUID());
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTransactionManager<String, ScoreEvent> kafkaTransactionManager() {
        return new KafkaTransactionManager<>(producerFactory());
    }
}
```

---

### 8. Schema Registry Integration (Avro/Protobuf)

**Best Practice:** Use schema registry for type safety

```java
@Configuration
public class KafkaProducerConfig {
    
    @Bean
    public ProducerFactory<String, ScoreEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // ✅ Use Avro serializer with schema registry
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
            KafkaAvroSerializer.class);
        config.put("schema.registry.url", "http://schema-registry:8081");
        
        return new DefaultKafkaProducerFactory<>(config);
    }
}

// Avro schema (score-event.avsc)
{
  "type": "record",
  "name": "ScoreEvent",
  "namespace": "com.t20.events",
  "fields": [
    {"name": "matchId", "type": "string"},
    {"name": "eventId", "type": "string"},
    {"name": "eventSequence", "type": "string"},
    {"name": "runs", "type": "int"},
    {"name": "wicket", "type": "boolean"},
    {"name": "timestamp", "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

---

### 9. Monitoring & Metrics (Producer)

**Best Practice:** Track producer metrics

```java
@Service
public class ScoreEventProducer {
    
    @Autowired
    private MeterRegistry registry;
    
    public void sendEvent(ScoreEvent event) {
        Timer.Sample sample = Timer.start(registry);
        
        kafkaTemplate.send("score-events", event.getMatchId(), event)
            .addCallback(
                result -> {
                    sample.stop(registry.timer("kafka.producer.latency", 
                        "status", "success"));
                    
                    registry.counter("kafka.producer.sent", 
                        "topic", "score-events").increment();
                },
                ex -> {
                    sample.stop(registry.timer("kafka.producer.latency", 
                        "status", "failure"));
                    
                    registry.counter("kafka.producer.failed", 
                        "topic", "score-events",
                        "error", ex.getClass().getSimpleName()).increment();
                }
            );
    }
}
```

**Key Metrics to Track:**
- `kafka.producer.sent` - Messages sent successfully
- `kafka.producer.failed` - Send failures
- `kafka.producer.latency` - Send latency (P50, P99)
- `kafka.producer.buffer.available` - Available buffer memory
- `kafka.producer.batch.size.avg` - Average batch size
- `kafka.producer.compression.rate` - Compression ratio

---

### 10. Circuit Breaker (Producer)

**Best Practice:** Fail fast when Kafka is down

```java
@Service
public class ScoreEventProducer {
    
    private final CircuitBreaker circuitBreaker;
    
    public ScoreEventProducer() {
        this.circuitBreaker = CircuitBreaker.of("kafka-producer", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(10)
            .build());
    }
    
    public void sendEvent(ScoreEvent event) {
        Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            kafkaTemplate.send("score-events", event).get(5, TimeUnit.SECONDS);
            return null;
        }))
        .onFailure(throwable -> {
            if (throwable instanceof CallNotPermittedException) {
                // Circuit open - store in fallback
                log.error("Kafka unavailable, storing in fallback: {}", event.getEventId());
                storeFallback(event);
            } else {
                log.error("Send failed: {}", event.getEventId(), throwable);
                throw new RuntimeException(throwable);
            }
        });
    }
    
    private void storeFallback(ScoreEvent event) {
        // Store in DynamoDB or SQS for later replay
        dynamoDb.putItem("t20-fallback-events", event);
    }
}
```

---

## Configuration Reference

### Consumer Configuration (application.yml)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    
    consumer:
      # Identity
      group-id: score-consumer-group
      client-id: score-consumer-${HOSTNAME}
      
      # Serialization
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      
      # Offset Management
      auto-offset-reset: earliest        # Start from beginning if no offset
      enable-auto-commit: false          # Manual commit
      isolation-level: read_committed    # Only read committed messages
      
      # Performance
      max-poll-records: 100              # Max records per poll
      fetch-min-size: 1024               # Min bytes to fetch (1KB)
      fetch-max-wait: 500                # Max wait for fetch (ms)
      max-partition-fetch-bytes: 1048576 # Max per partition (1MB)
      
      # Session Management
      session-timeout-ms: 30000          # 30s session timeout
      heartbeat-interval-ms: 3000        # 3s heartbeat
      
      properties:
        spring.json.trusted.packages: com.t20.events
        max.poll.interval.ms: 300000     # 5min max poll interval
        
    listener:
      ack-mode: record                   # Commit per record
      concurrency: 4                     # 4 threads per partition
      poll-timeout: 3000                 # 3s poll timeout
```

### Producer Configuration (application.yml)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    
    producer:
      # Identity
      client-id: score-producer-${HOSTNAME}
      
      # Serialization
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      
      # Reliability
      acks: all                          # Wait for all replicas
      retries: 2147483647                # Retry indefinitely
      enable-idempotence: true           # Exactly-once semantics
      
      # Performance
      batch-size: 16384                  # 16KB batch size
      linger-ms: 10                      # Wait 10ms for batch
      buffer-memory: 33554432            # 32MB buffer
      compression-type: snappy           # Compression algorithm
      
      # Timeouts
      request-timeout-ms: 30000          # 30s request timeout
      delivery-timeout-ms: 120000        # 2min delivery timeout
      max-block-ms: 60000                # 1min max block
      
      # Ordering
      max-in-flight-requests-per-connection: 5  # Max in-flight (with idempotence)
      
      properties:
        retry.backoff.ms: 100            # 100ms retry backoff
        reconnect.backoff.ms: 50         # 50ms reconnect backoff
```

---

## Monitoring & Observability

### Key Metrics to Monitor

#### Consumer Metrics

```java
@Component
public class KafkaConsumerMetrics {
    
    @Autowired
    private MeterRegistry registry;
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void recordMetrics() {
        // Consumer lag
        registry.gauge("kafka.consumer.lag", consumerLag);
        
        // Processing rate
        registry.counter("kafka.consumer.processed.total");
        registry.counter("kafka.consumer.failed.total");
        
        // Latency
        registry.timer("kafka.consumer.processing.latency");
        
        // Rebalances
        registry.counter("kafka.consumer.rebalances.total");
    }
}
```

**CloudWatch Dashboard:**
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["Kafka/Consumer", "Lag", {"stat": "Maximum"}],
          [".", "ProcessedRate", {"stat": "Sum"}],
          [".", "ErrorRate", {"stat": "Sum"}]
        ],
        "period": 300,
        "stat": "Average",
        "region": "ap-southeast-2",
        "title": "Consumer Health"
      }
    }
  ]
}
```

#### Producer Metrics

```java
@Component
public class KafkaProducerMetrics {
    
    @Scheduled(fixedRate = 60000)
    public void recordMetrics() {
        // Send rate
        registry.counter("kafka.producer.sent.total");
        registry.counter("kafka.producer.failed.total");
        
        // Latency
        registry.timer("kafka.producer.send.latency");
        
        // Buffer usage
        registry.gauge("kafka.producer.buffer.available", bufferAvailable);
        
        // Batch metrics
        registry.gauge("kafka.producer.batch.size.avg", avgBatchSize);
        registry.gauge("kafka.producer.compression.rate", compressionRate);
    }
}
```

### Alerting Rules

```yaml
# CloudWatch Alarms
alarms:
  - name: consumer-lag-high
    metric: kafka.consumer.lag
    threshold: 10000
    evaluation_periods: 2
    action: page-oncall
    
  - name: consumer-error-rate-high
    metric: kafka.consumer.failed.total
    threshold: 100
    period: 300
    action: slack-alert
    
  - name: producer-send-failures
    metric: kafka.producer.failed.total
    threshold: 10
    period: 60
    action: page-oncall
```

---

## Common Pitfalls

### ❌ Pitfall 1: Auto-commit with processing errors

```java
// ❌ BAD: Auto-commit can lose messages
@KafkaListener(topics = "score-events")
public void consume(ScoreEvent event) {
    processEvent(event);  // If this fails, offset already committed
}
```

```java
// ✅ GOOD: Manual commit after success
@KafkaListener(topics = "score-events")
public void consume(ScoreEvent event, Acknowledgment ack) {
    processEvent(event);
    ack.acknowledge();  // Commit only after success
}
```

---

### ❌ Pitfall 2: Blocking operations in listener

```java
// ❌ BAD: Blocking call blocks partition
@KafkaListener(topics = "score-events")
public void consume(ScoreEvent event) {
    externalApi.call(event);  // Blocks for 5 seconds
}
```

```java
// ✅ GOOD: Async processing
@KafkaListener(topics = "score-events")
public void consume(ScoreEvent event) {
    CompletableFuture.runAsync(() -> {
        externalApi.call(event);
    });
}
```

---

### ❌ Pitfall 3: No idempotency

```java
// ❌ BAD: Duplicates possible
@KafkaListener(topics = "score-events")
public void consume(ScoreEvent event) {
    dynamoDb.putItem(event);  // Duplicate on retry
}
```

```java
// ✅ GOOD: Conditional write
@KafkaListener(topics = "score-events")
public void consume(ScoreEvent event) {
    dynamoDb.putItem(PutItemRequest.builder()
        .conditionExpression("attribute_not_exists(eventId)")
        .build());
}
```

---

### ❌ Pitfall 4: Ignoring send failures

```java
// ❌ BAD: Fire and forget
kafkaTemplate.send("score-events", event);
```

```java
// ✅ GOOD: Handle failures
kafkaTemplate.send("score-events", event)
    .addCallback(
        result -> log.info("Sent: {}", result),
        ex -> storeFallback(event)
    );
```

---

### ❌ Pitfall 5: Wrong partition key

```java
// ❌ BAD: Random partitioning breaks ordering
kafkaTemplate.send("score-events", UUID.randomUUID().toString(), event);
```

```java
// ✅ GOOD: Meaningful key for ordering
kafkaTemplate.send("score-events", event.getMatchId(), event);
```

---

## Summary Checklist

### Consumer Checklist

- ✅ Idempotent processing (conditional writes)
- ✅ Manual offset commit (after success)
- ✅ Poison pill handling (catch + exclude)
- ✅ Graceful shutdown (drain messages)
- ✅ Backpressure handling (limit concurrency)
- ✅ Circuit breaker (downstream failures)
- ✅ DLT strategy (structured error handling)
- ✅ Rebalance listener (state management)
- ✅ Schema validation (fail fast)
- ✅ Resource cleanup (prevent leaks)
- ✅ Monitoring (lag, latency, errors)

### Producer Checklist

- ✅ Idempotent producer (exactly-once)
- ✅ Async send with callback (error handling)
- ✅ Meaningful partition key (ordering)
- ✅ Batching & compression (performance)
- ✅ Timeout configuration (reliability)
- ✅ Message ordering (per partition)
- ✅ Transaction support (multi-topic)
- ✅ Schema registry (type safety)
- ✅ Monitoring (send rate, latency)
- ✅ Circuit breaker (fail fast)

---

## References

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/html/)
- [Resilience4j Circuit Breaker](https://resilience4j.readme.io/docs/circuitbreaker)
- [DynamoDB Conditional Writes](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/WorkingWithItems.html#WorkingWithItems.ConditionalUpdate)
