# DynamoDB Interview Q&A – 25 Questions

Comprehensive interview questions covering DynamoDB table design, PostgreSQL comparison, optimization strategies, and best practices.

---

## Quick Reference

| # | Question | Key Answer |
|---|----------|------------|
| 1 | When choose DynamoDB over PostgreSQL? | Key-value access, predictable latency, serverless, elastic scale |
| 2 | Key data modeling differences? | Denormalized vs normalized, no JOINs, composite keys |
| 3 | DynamoDB vs PostgreSQL pros/cons? | DynamoDB: scale/ops; PostgreSQL: flexibility/SQL |
| 4 | Transaction support comparison? | PostgreSQL: full ACID; DynamoDB: limited (100 items) |
| 5 | Cost comparison? | DynamoDB cheaper for sporadic; PostgreSQL for sustained |
| 6 | How design partition key? | High cardinality, even distribution, matches access pattern |
| 7 | When use sort key? | Range queries, ordering, multiple items per PK |
| 8 | Composite key pattern? | Encode multiple attributes in PK/SK for efficient queries |
| 9 | Avoid hot partitions? | High cardinality PK, add randomness, use GSI |
| 10 | Single-table vs multi-table? | Single for related entities; multi for independence |
| 11 | Multiple access patterns? | Main table + GSI for alternative queries |
| 12 | GSI vs LSI? | GSI: different PK; LSI: same PK, different SK |
| 13 | GSI projection types? | KEYS_ONLY (lookup), INCLUDE (partial), ALL (full) |
| 14 | Handle GSI throttling? | Match capacity, on-demand mode, sparse index |
| 15 | Sparse index pattern? | Only index items with specific attribute present |
| 16 | On-demand vs provisioned? | On-demand for sporadic; provisioned for sustained |
| 17 | Optimize costs? | Reduce item size, batch ops, eventually consistent reads |
| 18 | Eventually vs strongly consistent? | Eventually: 0.5 RCU, stale; Strongly: 1 RCU, latest |
| 19 | Efficient pagination? | Use LastEvaluatedKey cursor, not offset |
| 20 | Batch operations? | BatchGetItem (100), BatchWriteItem (25) for bulk |
| 21 | Time-series data? | Composite SK with timestamp, time-bucketed tables, TTL |
| 22 | Handle large items (>400KB)? | Store in S3 with reference, compress, or split |
| 23 | Optimistic locking? | Version number + conditional write |
| 24 | DynamoDB Streams for CDC? | Real-time change capture for downstream processing |
| 25 | Global tables? | Multi-region active-active with automatic replication |

---

## Part 1: DynamoDB vs PostgreSQL (Questions 1-5)

### 1. When to choose DynamoDB over PostgreSQL?

**Choose DynamoDB when:**
- Predictable key-value access patterns (get by ID, query by partition key)
- Need single-digit ms latency at any scale
- Serverless, zero-ops database preferred
- Elastic scaling without downtime required
- Multi-region active-active replication needed

**Choose PostgreSQL when:**
- Complex queries with JOINs across tables
- Ad-hoc analytics and reporting
- Strong ACID transactions across multiple rows
- Rich querying (aggregations, window functions, CTEs)
- Existing SQL expertise in team

**T20 System Example:**
```
✅ DynamoDB chosen:
- Access: Get events by matchId (key-value)
- Scale: 100-130 matches, unpredictable spikes
- Latency: P99 < 10ms for live scores
- Ops: Zero capacity planning

❌ PostgreSQL issues:
- Must provision for peak (expensive)
- Complex sharding for horizontal scale
- Operational overhead
```

---

### 2. Key differences in data modeling?

| Aspect | PostgreSQL | DynamoDB |
|--------|-----------|----------|
| **Schema** | Fixed, ALTER TABLE | Schemaless, flexible |
| **Normalization** | 3NF, JOINs | Denormalized, embed data |
| **Primary Key** | Auto-increment/UUID | Partition key + sort key |
| **Queries** | SQL (WHERE, JOIN, GROUP BY) | Key lookups, Query, Scan |
| **Indexes** | B-tree, GIN, GiST | GSI, LSI |

**Example:**

PostgreSQL (normalized):
```sql
SELECT e.*, m.team_a FROM score_events e
JOIN matches m ON e.match_id = m.match_id
WHERE e.match_id = 'IPL-2025-MI-CSK-001';
```

DynamoDB (denormalized):
```json
{
  "matchId": "IPL-2025-MI-CSK-001",
  "eventSequence": "1#03#04",
  "runs": 4,
  "teamA": "MI",
  "teamB": "CSK"
}
```

---

### 3. Pros and cons comparison

**DynamoDB Pros:**
- ✅ Predictable single-digit ms latency
- ✅ Infinite scale (millions of RPS)
- ✅ Zero ops (no servers, patches, backups)
- ✅ Pay-per-request option
- ✅ Multi-region with global tables
- ✅ 99.99% SLA

**DynamoDB Cons:**
- ❌ No JOINs or complex queries
- ❌ Upfront access pattern design required
- ❌ Limited transactions (100 items max)
- ❌ 400KB item size limit
- ❌ Learning curve

**PostgreSQL Pros:**
- ✅ SQL flexibility (JOINs, aggregations)
- ✅ Full ACID transactions
- ✅ Rich ecosystem and tooling
- ✅ Familiar to developers
- ✅ Full-text search built-in

**PostgreSQL Cons:**
- ❌ Scaling complexity (sharding manual)
- ❌ Operational overhead
- ❌ Latency variability at scale
- ❌ Connection limits
- ❌ Must provision capacity

---

### 4. Transaction support comparison

| Feature | PostgreSQL | DynamoDB |
|---------|-----------|----------|
| **ACID** | Full across rows/tables | Within item; limited across items |
| **Scope** | Unlimited rows | Max 100 items, 4MB |
| **Isolation** | Serializable | Serializable |
| **Cross-region** | Manual setup | Single-region only |

**DynamoDB Transaction Example:**
```java
TransactWriteItemsRequest.builder()
    .transactItems(
        // Update item 1
        TransactWriteItem.builder()
            .update(Update.builder()
                .key(Map.of("userId", attr("user1")))
                .updateExpression("SET balance = balance - :amt")
                .conditionExpression("balance >= :amt")
                .build())
            .build(),
        // Update item 2
        TransactWriteItem.builder()
            .update(Update.builder()
                .key(Map.of("userId", attr("user2")))
                .updateExpression("SET balance = balance + :amt")
                .build())
            .build()
    )
    .build();
```

**Limitations:** 100 items max, same region, 4MB total size

---

### 5. Cost comparison at scale

**Scenario:** T20 System (36K writes/day, 1M reads/day)

**DynamoDB On-Demand:**
```
Writes: 36K × $1.25/million = $0.045/day
Reads: 1M × $0.25/million = $0.25/day
Storage: 10GB × $0.25/GB = $2.50/month
Total: ~$9/month
```

**PostgreSQL RDS (db.t3.medium):**
```
Instance: $60/month
Storage: 100GB = $11.50/month
Backup: $9.50/month
Total: ~$81/month
```

**Winner:** DynamoDB (9× cheaper for this workload)

**When PostgreSQL wins:** Sustained >10K RPS, complex analytics

---

## Part 2: Table Design (Questions 6-10)

### 6. How to design partition key (PK)?

**Requirements:**
1. High cardinality (many unique values)
2. Even distribution (avoid hot partitions)
3. Matches primary access pattern

**T20 System:**
```
PK: matchId
SK: eventSequence (inning#over#ball)

✅ High cardinality: 120 matches
✅ Even distribution: ~300 events each
✅ Access pattern: "Get events for match X"
```

**Bad PK choices:**
- ❌ `inning`: Only 2 values → hot partition
- ❌ `eventId`: Can't query related events together
- ❌ Constant: All data in 1 partition

---

### 7. When to use sort key (SK)?

Use SK for:
1. Range queries (BETWEEN, >, <)
2. Ordering (automatic sort)
3. Multiple items per PK
4. Hierarchical data

**T20 System SK:**
```
eventSequence = "inning#over#ball"

✅ Lexicographic ordering: "1#03#04" < "1#03#05"
✅ Range queries: Get all balls in over 3
✅ Prefix queries: begins_with("1#") for inning 1
```

**Query Examples:**
```java
// Get inning 1 events
keyConditionExpression("matchId = :m AND begins_with(eventSequence, :i)")

// Get over 3 events
keyConditionExpression("matchId = :m AND eventSequence BETWEEN :s AND :e")
```

---

### 8. What is a composite key pattern?

Encode multiple attributes in PK/SK for efficient queries.

**Example: Multi-Entity Table**
```json
// Match metadata
{"PK": "MATCH#IPL-001", "SK": "METADATA", "teamA": "MI"}

// Events
{"PK": "MATCH#IPL-001", "SK": "EVENT#1#03#04", "runs": 4}

// Live score
{"PK": "MATCH#IPL-001", "SK": "LIVE_SCORE", "totalRuns": 145}

// Player stats
{"PK": "MATCH#IPL-001", "SK": "PLAYER#Rohit#BATTING", "runs": 45}
```

**Benefits:**
- Single query gets all match data
- All related items in same partition
- Flexible (add entity types without schema changes)

---

### 9. How to avoid hot partitions?

**Solutions:**

**1. Add randomness (write sharding):**
```java
// Distribute across 10 partitions
PK = "2025-04-15#" + (hash(eventId) % 10)
```

**2. Use high-cardinality attribute:**
```java
// Good: matchId (120 unique)
// Bad: inning (only 2 values)
```

**3. Use GSI for uneven access:**
```java
// Main: PK = matchId (even)
// GSI: PK = teamName (uneven, but isolated)
```

**T20 System:**
```
✅ PK = matchId (120 partitions, even load)
❌ If PK = date (all writes to 1 partition → throttle)
```

---

### 10. Single-table vs multi-table design?

| Approach | When | Pros | Cons |
|----------|------|------|------|
| **Single-table** | Related entities | Lower cost, fewer calls | Complex design |
| **Multi-table** | Independent entities | Simple, clear | Higher cost, more calls |

**T20 System: Multi-Table ✅**
```
t20-score-events: Event store (write-heavy)
t20-live-scores: Materialized view (read-heavy)
t20-replay-state: Operational (TTL)

Why: Different access patterns, retention, scaling needs
```

---

## Part 3: GSI & Access Patterns (Questions 11-15)

### 11. How to design for multiple access patterns?

**Step 1: List patterns**
```
1. Get events for match (by matchId)
2. Get events for inning (by matchId + inning)
3. Get matches for team (by teamName)
4. Get matches by date (by date)
```

**Step 2: Design table + GSI**
```
Main: PK=matchId, SK=eventSequence → Patterns 1, 2
GSI1: PK=teamName, SK=matchDate → Pattern 3
GSI2: PK=matchDate, SK=matchId → Pattern 4
```

---

### 12. When to use GSI vs LSI?

| Feature | GSI | LSI |
|---------|-----|-----|
| **PK** | Different from main | Same as main |
| **SK** | Different | Different |
| **Capacity** | Independent | Shares with main |
| **Creation** | Anytime | At table creation only |
| **Consistency** | Eventually only | Strongly consistent option |

**Use GSI:** Query by different PK (most common)  
**Use LSI:** Same PK, different SK + need strong consistency (rare)

---

### 13. GSI projection types – which to choose?

| Type | Includes | Use Case | Cost |
|------|----------|----------|------|
| **KEYS_ONLY** | Keys only | Lookup, then GetItem | Lowest |
| **INCLUDE** | Keys + specified attrs | Frequently accessed attrs | Medium |
| **ALL** | All attributes | Avoid GetItem | Highest |

**T20 System:**
```java
// Team Index: INCLUDE projection
ProjectionType: INCLUDE
NonKeyAttributes: ["teamA", "teamB", "venue", "matchDate"]

// Avoids GetItem for 90% of queries
// Storage: +20% vs KEYS_ONLY
// Read cost: -50% (no GetItem)
```

---

### 14. How to handle GSI throttling?

**Cause:** GSI capacity < main table write rate

**Solutions:**

1. **Match capacity:**
```
Main: 1000 WCU
GSI: 1000 WCU (same)
```

2. **On-demand mode:**
```
Auto-scales GSI with main table
```

3. **Sparse index:**
```java
// Only index "ACTIVE" items (10% of table)
// GSI gets 10% of writes
```

**T20 System:** On-demand mode (unpredictable traffic)

---

### 15. Sparse index pattern – what and why?

Only index items with specific attribute present.

**Example:**
```json
// Indexed (has "status")
{"matchId": "IPL-001", "status": "ACTIVE"}

// NOT indexed (no "status")
{"matchId": "IPL-002"}
```

**Benefits:**
- ✅ Reduce GSI size (lower storage cost)
- ✅ Reduce GSI writes (lower write cost)
- ✅ Efficient filtering

**Use case:** Active vs archived, premium vs free users

---

## Part 4: Performance (Questions 16-20)

### 16. On-demand vs provisioned capacity?

| Aspect | On-Demand | Provisioned |
|--------|-----------|-------------|
| **Pricing** | $1.25/M writes | $0.47/WCU/month |
| **Scaling** | Instant | 1-2 min delay |
| **Planning** | None | Must estimate |
| **Break-even** | <360K writes/month | >360K writes/month |

**T20 System:** On-demand (sporadic load, 40× cheaper)

---

### 17. How to optimize read/write costs?

**Write optimization:**
```java
// 1. Reduce item size (S3 for large data)
// 2. Batch writes (25 items per call)
// 3. UpdateItem vs PutItem (only changed attrs)
```

**Read optimization:**
```java
// 1. Eventually consistent (50% cost savings)
// 2. Projection expressions (read only needed attrs)
// 3. BatchGetItem (100 items per call)
// 4. Cache with DAX or Redis
```

---

### 18. Eventually vs strongly consistent reads?

| Type | Cost | Latency | Consistency |
|------|------|---------|-------------|
| **Eventually** | 0.5 RCU | Lower | May be stale (up to 1s) |
| **Strongly** | 1 RCU | Higher | Always latest |

**T20 System:**
```java
// Live scores: Eventually consistent (50% savings)
// Replay: Strongly consistent (accuracy required)
```

---

### 19. How to implement pagination efficiently?

**❌ Bad (offset-based):**
```java
// Fetch 110 items, skip 100, use 10
// Cost: 110 RCU, Latency: O(offset)
```

**✅ Good (cursor-based):**
```java
QueryRequest.builder()
    .limit(10)
    .exclusiveStartKey(cursor)  // From previous page
    .build();

// Return nextCursor = response.lastEvaluatedKey()
// Cost: 10 RCU, Latency: O(1)
```

---

### 20. Batch operations – when and how?

**BatchGetItem:** Fetch up to 100 items
**BatchWriteItem:** Write up to 25 items

```java
// Fetch 50 live scores in 1 call
BatchGetItemRequest.builder()
    .requestItems(Map.of("t20-live-scores",
        KeysAndAttributes.builder()
            .keys(matchIds.stream()
                .map(id -> Map.of("matchId", attr(id)))
                .toList())
            .build()))
    .build();
```

**Limitations:** No transactions, no conditional writes, no UpdateItem

---

## Part 5: Advanced Patterns (Questions 21-25)

### 21. How to implement time-series data?

**Pattern 1: Composite SK**
```
PK: matchId
SK: timestamp#eventType
```

**Pattern 2: Time-bucketed tables**
```
t20-events-2025-04
t20-events-2025-05
```

**Pattern 3: Composite PK**
```
PK: matchId#YYYY-MM
SK: timestamp
```

**T20 System:** Composite SK (implicit time ordering)

**TTL for auto-deletion:**
```java
@DynamoDbAttribute("ttl")
public Long getTtl() {
    return Instant.now().plus(90, ChronoUnit.DAYS).getEpochSecond();
}
```

---

### 22. How to handle large items (>400KB)?

**Pattern 1: S3 reference (recommended)**
```java
// Store in S3
s3.putObject("highlights/" + matchId, video);

// Reference in DynamoDB
{"matchId": "IPL-001", "videoS3": "s3://bucket/highlights/IPL-001"}
```

**Pattern 2: Compression**
```java
byte[] compressed = gzip(largeJson); // 600KB → 150KB
```

**Pattern 3: Split into chunks**
```java
// Split 1MB into 4× 250KB chunks
{"matchId": "IPL-001", "chunkId": 0, "data": "..."}
{"matchId": "IPL-001", "chunkId": 1, "data": "..."}
```

---

### 23. Optimistic locking with version numbers?

Prevent lost updates using version numbers.

```java
// Item: {matchId, totalRuns: 100, version: 5}

// Update with version check
UpdateItemRequest.builder()
    .updateExpression("SET totalRuns = :new, version = :newVer")
    .conditionExpression("version = :expected")  // Check version
    .expressionAttributeValues(Map.of(
        ":new", attr(104),
        ":newVer", attr(6),
        ":expected", attr(5)
    ))
    .build();

// If version changed → ConditionalCheckFailedException → retry
```

---

### 24. DynamoDB Streams for CDC?

Capture real-time changes for downstream processing.

**Use cases:**
- Replicate to ElasticSearch for search
- Trigger Lambda on item changes
- Maintain materialized views
- Audit logging

```java
// Enable streams
StreamSpecification.builder()
    .streamEnabled(true)
    .streamViewType(StreamViewType.NEW_AND_OLD_IMAGES)
    .build();

// Lambda processes stream records
public void handleRecord(DynamodbStreamRecord record) {
    if (record.eventName().equals("INSERT")) {
        Map<String, AttributeValue> newImage = record.dynamodb().newImage();
        // Process new item
    }
}
```

**T20 System:** Stream events to ElasticSearch for full-text search

---

### 25. Global tables for multi-region?

Multi-region active-active replication.

**Benefits:**
- ✅ Read/write in any region (low latency)
- ✅ Automatic conflict resolution (last-writer-wins)
- ✅ 99.999% availability

**Setup:**
```java
// Create global table
CreateGlobalTableRequest.builder()
    .globalTableName("t20-live-scores")
    .replicationGroup(
        Replica.builder().regionName("ap-southeast-2").build(),
        Replica.builder().regionName("us-east-1").build(),
        Replica.builder().regionName("eu-west-1").build()
    )
    .build();
```

**Conflict resolution:** Last write wins (based on timestamp)

**T20 System:** Not needed (single region sufficient)

---

## Summary

**Key Takeaways:**

1. **Design for access patterns** - DynamoDB requires upfront planning
2. **Denormalize data** - Embed related data, avoid JOINs
3. **Choose right PK/SK** - High cardinality, even distribution
4. **Use GSI wisely** - Alternative access patterns, watch throttling
5. **Optimize costs** - Eventually consistent, batch ops, projection expressions
6. **Handle scale** - Sparse indexes, on-demand mode, avoid hot partitions
7. **Advanced patterns** - Time-series, large items, optimistic locking, streams

**T20 System Design Decisions:**
- Multi-table design (different access patterns)
- matchId as PK (even distribution)
- Composite SK for ordering (inning#over#ball)
- On-demand mode (unpredictable traffic)
- Eventually consistent reads (cost savings)
- TTL for old data (auto-cleanup)
