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

**Recommendation: Do NOT write to Elasticsearch directly from the Kafka `score-consumer`.** 
Writing to two databases simultaneously (DynamoDB and Elasticsearch) introduces the "Dual Write Problem" (if DynamoDB succeeds but Elasticsearch fails, your search is out of sync). 

Instead, use one of the following asynchronous replication strategies:

### Option A: DynamoDB Streams + AWS Lambda (Recommended)
1. The `score-consumer` consumes the Kafka message and writes it *only* to DynamoDB.
2. DynamoDB Streams captures the insert/update event.
3. An AWS Lambda function is triggered by the stream.
4. The Lambda function transforms the DynamoDB record into an Elasticsearch document and indexes it into OpenSearch.
**Pros:** Guarantees DynamoDB is the absolute source of truth. Highly scalable and decoupled.

### Option B: Kafka Sink Connector (Alternative)
1. Run a Kafka Connect OpenSearch Sink Connector directly attached to MSK.
2. The connector reads the exact same topic the `score-consumer` reads and indexes data directly into OpenSearch.
**Pros:** Zero-code syncing. **Cons:** Requires running Kafka Connect infrastructure.

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
*   `POST /api/dashboard/analytics/matches` → Aggregations (e.g., match wins by venue, average scores by format).

---

## 5. Technology Stack for the Subproject
*   **Framework:** Spring Boot 3.4.2
*   **Web:** `spring-boot-starter-web`
*   **Security:** `spring-boot-starter-security`, `java-jwt` (or `jjwt` for token generation/validation)
*   **Search:** `spring-boot-starter-data-elasticsearch`
*   **Caching:** `spring-boot-starter-data-redis`, `spring-boot-starter-cache`
*   **Observability:** Micrometer + OpenTelemetry (to trace requests from API -> Cache -> DB/Search)
