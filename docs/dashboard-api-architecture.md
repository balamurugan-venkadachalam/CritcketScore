# Score Dashboard API Architecture

## Overview
The `score-dashboard` will be a new Spring Boot subproject responsible for serving frontend clients (web/mobile). It provides secure API endpoints for searching matches, fetching live scoreboards, and retrieving player statistics. It relies heavily on **Amazon OpenSearch (Elasticsearch)** for fast, fuzzy, and aggregated queries, and **Redis** for caching high-traffic data.

---

## 1. High-Level Architecture

```text
┌──────────────┐      ┌─────────────────────────┐      ┌─────────────────────────┐
│              │      │                         │      │                         │
│  Frontend    ├─────►│  score-dashboard (API)  ├─────►│  Amazon ElastiCache     │
│ (React/Vue)  │      │  (Spring Boot + Sec)    │      │  (Redis - Caching)      │
│              │      │                         │      │                         │
└──────────────┘      └─────────┬───────────────┘      └─────────────────────────┘
                                │
                                │ (Queries)
                                ▼
                      ┌─────────────────────────┐
                      │                         │      ┌─────────────────────────┐
                      │  Amazon OpenSearch      │◄─────┤  AWS Lambda (Indexer)   │
                      │  (Elasticsearch)        │      │                         │
                      │                         │      └─────────▲───────────────┘
                      └─────────────────────────┘                │
                                                                 │ (Streams)
┌──────────────┐      ┌─────────────────────────┐      ┌─────────┴───────────────┐
│              │      │                         │      │                         │
│ Kafka (MSK)  ├─────►│  score-consumer         ├─────►│  Amazon DynamoDB        │
│              │      │                         │      │  (Source of Truth)      │
└──────────────┘      └─────────────────────────┘      └─────────────────────────┘
```

---

## 2. How Data is Loaded into Elasticsearch

When adding a secondary data store like Elasticsearch alongside a primary database like DynamoDB, you must choose a sync strategy. Here are the three main architectural options, along with their pros, cons, and a final conclusion.

### Option A: Change Data Capture (CDC) via DynamoDB Streams + AWS Lambda
1. The `score-consumer` consumes the Kafka message and writes it *only* to DynamoDB.
2. DynamoDB Streams captures the insert/modify/remove event.
3. An AWS Lambda function is triggered automatically by the stream.
4. The Lambda function transforms the DynamoDB record into an Elasticsearch document and indexes it into OpenSearch.

**Pros:**
*   **Single Source of Truth:** DynamoDB is the absolute authority. The search index is guaranteed to eventually match the DB state.
*   **Decoupled & Resilient:** If OpenSearch goes down, the Lambda fails and retries via the stream iterator without impacting the main Kafka consumer workflow.
*   **No Code Changes to Consumer:** The `score-consumer` remains purely concerned with DynamoDB.

**Cons:**
*   **Slight Latency:** There is a minor delay (usually < 1 second) between the DB write and the search index update.
*   **AWS Lock-in / Cost:** Requires configuring and paying for DynamoDB Streams and Lambda invocations.

### Option B: Kafka Sink Connector (Kafka Connect)
1. You run a Kafka Connect cluster equipped with the OpenSearch Sink Connector.
2. The connector subscribes to the exact same MSK topic that the `score-consumer` is reading from.
3. As messages arrive, the connector sink pushes them directly into OpenSearch in parallel to the consumer writing to DynamoDB.

**Pros:**
*   **Zero Code:** No custom Lambda or consumer code is required for the sync; it's purely configuration-driven.
*   **Speed:** Elasticsearch and DynamoDB are updated in parallel.

**Cons:**
*   **Operational Overhead:** You must provision, monitor, and maintain a Kafka Connect cluster (or use MSK Connect which incurs infrastructure costs).
*   **Divergence Risk:** If the Kafka message structure doesn't perfectly match the desired Elasticsearch document structure, you still need to write custom Single Message Transforms (SMTs), negating the "zero code" benefit.

### Option C: "Dual Writes" from the Spring Boot Consumer
1. The `score-consumer` receives a Kafka message.
2. The Java code calls `dynamoDbTemplate.save()`.
3. Immediately after, the same Java code calls `elasticsearchOperations.save()`.

**Pros:**
*   **Simplicity at First Glance:** Conceptually easy to understand and all logic is in one codebase.

**Cons:**
*   **The Dual Write Problem:** This is a known distributed systems anti-pattern. If the DynamoDB write succeeds but the OpenSearch API call times out or fails, your systems are out of sync. You must implement complex saga patterns, retry queues (DLQs), or two-phase commits to handle partial failures.
*   **Coupling:** The consumer is now tied to the availability of *both* databases to make progress.

### Final Conclusion & Recommendation
**Option A (DynamoDB Streams + Lambda)** is the definitive choice for this architecture. 

It completely avoids the Dual Write problem (Option C) and removes the heavy infrastructure tax of running Kafka Connect (Option B). Even though it introduces a few hundred milliseconds of eventual consistency, this is perfectly acceptable for a dashboard search feature. Most importantly, it keeps the `score-consumer` extremely fast and focused solely on processing Kafka messages into the source-of-truth database.

---

## 3. Dashboard API Components

### A. Spring Security & JWT
*   **Authentication Endpoint:** `POST /api/auth/login` - Accepts credentials, validates against the DB, and returns a signed JWT.
*   **Security Filter:** A `JwtAuthenticationFilter` intercepts all requests to `/api/dashboard/**`, validates the JWT signature, and populates the `SecurityContext`.
*   **Stateless:** The application uses `SessionCreationPolicy.STATELESS` since JWTs carry all necessary user claims.

### B. Caching Layer (Spring Cache + Redis)
To handle massive spikes in traffic (e.g., during the final overs of a match), the dashboard uses aggressive caching.
*   `@EnableCaching` enabled on the main class.
*   Redis is used as the caching provider (`spring-boot-starter-data-redis`).
*   **Cached Endpoints:**
    *   `@Cacheable(value = "live-scores", key = "#matchId", sync = true)` with a short TTL (e.g., 5 seconds).
    *   `@Cacheable(value = "player-stats", key = "#playerId")` for historical data (long TTL).

### C. Elasticsearch Integration
*   Uses `spring-data-elasticsearch` mapped to the OpenSearch cluster.
*   Powers the advanced search endpoints utilizing exact matches, fuzzy search, and aggregations.

---

## 4. Proposed API Endpoints

### Authentication
*   `POST /api/auth/login` → Get JWT Token

### Matches & Live Scores
*   `GET /api/dashboard/matches/live` → Get currently active matches (Cached: 5s)
*   `GET /api/dashboard/matches/{matchId}` → Get full match scorecard (Sourced from DynamoDB or Redis Cache)

### Search & Discovery (Powered by Elasticsearch)
*   `GET /api/dashboard/search?q={query}` 
    *   Searches across Teams, Players, Match Titles, and Venues. Returns multi-index hits.
*   `GET /api/dashboard/search/suggestions?q={prefix}` 
    *   Auto-complete suggestions for the search bar (uses Elasticsearch Completion Suggester).

### Statistics & Aggregations (Powered by Elasticsearch)
*   `GET /api/dashboard/stats/players/{playerId}` → Player career stats.
*   `GET /api/dashboard/stats/players/{playerId}?timeframe=last_year` → Player stats filtered by time.
*   `POST /api/dashboard/analytics/matches` → Aggregations (e.g., match wins by venue, average scores by format).

---

## 5. Querying Time-Bound Player Performance (e.g., "Last 1 Year")

To get a player's performance (runs, strike rate, wickets, catches) specifically for the last 1 year, you shouldn't rely on the pre-calculated `careerStats` in the `Player Index`. Instead, you use the **Match Index** where the raw performance data lives inside the nested `players` array.

### The Elasticsearch Approach
You run an aggregation query against the `matches` index with a top-level date filter, and then calculate sums inside the nested `players` block.

**Query Logic:**
1.  **Filter (Outer Level):** Match Date >= `now-1y/d` (last 1 year).
2.  **Filter (Nested Level):** `players.playerId` == `TARGET_PLAYER_ID`.
3.  **Aggregations (Nested Level):** `sum` of runs, `sum` of balls faced, `sum` of wickets, `sum` of catches.
4.  **Pipeline Aggregation:** Calculate Strike Rate = `(sum_runs / sum_balls) * 100`.

### Example OpenSearch JSON Query
```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        {
          "range": {
            "startDate": {
              "gte": "now-1y/d",
              "lte": "now/d"
            }
          }
        },
        {
          "nested": {
            "path": "players",
            "query": {
              "term": {
                "players.playerId": "V_KOHLI_18"
              }
            }
          }
        }
      ]
    }
  },
  "aggs": {
    "player_stats": {
      "nested": {
        "path": "players"
      },
      "aggs": {
        "filtered_player": {
          "filter": {
            "term": {
              "players.playerId": "V_KOHLI_18"
            }
          },
          "aggs": {
            "total_runs": { "sum": { "field": "players.runs" } },
            "total_balls": { "sum": { "field": "players.ballsFaced" } },
            "total_wickets": { "sum": { "field": "players.wickets" } },
            "total_catches": { "sum": { "field": "players.catches" } },
            "matches_played": { "value_count": { "field": "players.playerId" } },
            "strike_rate": {
              "bucket_script": {
                "buckets_path": {
                  "runs": "total_runs",
                  "balls": "total_balls"
                },
                "script": "(params.balls > 0) ? (params.runs / params.balls) * 100 : 0"
              }
            }
          }
        }
      }
    }
  }
}
```

### Spring Data Elasticsearch Implementation
In Spring Boot, you build this using `NativeSearchQuery` and `AggregationBuilders`:

```java
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;

public PlayerStats getPlayerStatsLastYear(String playerId) {
    // 1. Filter by last year & nested playerId
    BoolQueryBuilder query = QueryBuilders.boolQuery()
        .must(QueryBuilders.rangeQuery("startDate").gte("now-1y/d").lte("now/d"))
        .must(QueryBuilders.nestedQuery("players", 
              QueryBuilders.termQuery("players.playerId", playerId), ScoreMode.None));

    // 2. Build Nested Aggregation and Pipeline Math
    NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
        .withQuery(query)
        .addAggregation(AggregationBuilders.nested("player_stats", "players")
            .subAggregation(AggregationBuilders.filter("filtered_player", 
                QueryBuilders.termQuery("players.playerId", playerId))
                .subAggregation(AggregationBuilders.sum("total_runs").field("players.runs"))
                .subAggregation(AggregationBuilders.sum("total_balls").field("players.ballsFaced"))
                .subAggregation(AggregationBuilders.sum("total_wickets").field("players.wickets"))
                .subAggregation(AggregationBuilders.sum("total_catches").field("players.catches"))
                // Execute math (Runs/Balls * 100) inside ES to save network I/O
                .subAggregation(PipelineAggregatorBuilders.bucketScript("strike_rate", 
                    Map.of("runs", "total_runs", "balls", "total_balls"),
                    new Script("(params.balls > 0) ? (params.runs / params.balls) * 100 : 0"))
                )
            )
        )
        .build();

    // 3. Execute and parse results...
}
```
**Why this is powerful:** We don't need a background job updating "Last 1 Year" stats every day. Elasticsearch computes it on the fly in milliseconds directly from the match history.

---

## 6. Technology Stack for the Subproject
*   **Framework:** Spring Boot 3.4.2
*   **Web:** `spring-boot-starter-web`
*   **Security:** `spring-boot-starter-security`, `java-jwt` (or `jjwt` for token generation/validation)
*   **Search:** `spring-boot-starter-data-elasticsearch`
*   **Caching:** `spring-boot-starter-data-redis`, `spring-boot-starter-cache`
*   **Observability:** Micrometer + OpenTelemetry (to trace requests from API -> Cache -> DB/Search)
