# Java Interview Questions & Answers - Part 4 (Q151–Q200)

## System Design (Java-focused), Scalability, Resilience, Performance

### 151. How do you design a high-level architecture for a typical backend Java service?

**Answer:**
Common layers:
- API layer (controllers)
- Service layer (business logic)
- Persistence layer (repositories)
- Integration layer (clients)
- Observability (logs/metrics/traces)

Aim for:
- clear boundaries
- testability
- dependency inversion

---

### 152. What is the difference between monolith and microservices?

**Answer:**
- Monolith: simpler deployment, easier local dev, but can become tightly coupled.
- Microservices: independent deploy, scalability, team autonomy, but operational complexity.

---

### 153. What is the Strangler Fig pattern?

**Answer:**
Incrementally replace monolith features by routing traffic to new services until old is retired.

---

### 154. How do you design an API that is backward compatible?

**Answer:**
- additive changes
- avoid breaking response contract
- version when breaking
- keep old fields until deprecation window ends

---

### 155. What are idempotent APIs and why do they matter?

**Answer:**
Idempotent operation can be retried safely.
Use for payments/orders using idempotency keys.

---

### 156. How do you design for pagination at scale?

**Answer:**
- offset pagination is simple but slow for deep pages
- keyset pagination is faster

Keyset example: `WHERE id > lastId ORDER BY id LIMIT size`.

---

### 157. What is eventual consistency? Where do you accept it?

**Answer:**
Data becomes consistent over time.
Accept in:
- search indexes
- caches
- analytics
Avoid in:
- strong financial invariants (balance)

---

### 158. What is a Saga pattern?

**Answer:**
Distributed transaction pattern:
- sequence of local transactions
- compensating actions on failure

Two types:
- choreography
- orchestration

---

### 159. Outbox pattern: what problem does it solve?

**Answer:**
Ensures DB write and event publish are consistent.
Write event to outbox table in same transaction, then publish asynchronously.

---

### 160. How do you design a service to handle spikes (traffic bursts)?

**Answer:**
- rate limiting
- queues (Kafka/SQS)
- autoscaling
- caching
- graceful degradation

---

### 161. How do you choose between synchronous REST and async messaging?

**Answer:**
- REST: request/response, user-facing, low latency
- Messaging: decoupling, buffering, retries, event-driven

---

### 162. What are common caching strategies?

**Answer:**
- cache-aside (lazy)
- write-through
- write-behind

Big problem: invalidation.

---

### 163. How do you prevent cache stampede?

**Answer:**
- request coalescing / single flight
- lock per key
- stale-while-revalidate
- jittered TTL

---

### 164. How do you implement rate limiting?

**Answer:**
Algorithms:
- token bucket
- leaky bucket
- fixed/sliding window

In distributed systems use Redis or gateway.

---

### 165. How do you design authentication/authorization in Java services?

**Answer:**
- AuthN: JWT/OAuth2
- AuthZ: RBAC/ABAC
- validate tokens at gateway/resource server
- minimize token scope

---

### 166. What is mTLS and when would you use it?

**Answer:**
mTLS authenticates both client and server with certificates.
Use for service-to-service zero-trust.

---

### 167. What is API gateway and typical responsibilities?

**Answer:**
- routing
- auth
- rate limiting
- request shaping
- aggregation
- observability

---

### 168. What is service mesh and what problems does it solve?

**Answer:**
Moves cross-cutting concerns (mTLS, retries, traffic shifting) to infrastructure layer (sidecars).

---

### 169. How do you design for resilience (timeouts/retries)?

**Answer:**
- timeouts everywhere
- bounded retries with backoff + jitter
- circuit breakers
- bulkheads
- fallbacks

---

### 170. Why are retries dangerous?

**Answer:**
They can amplify load (retry storm).
Always:
- limit retries
- add jitter
- make operations idempotent

---

### 171. How do you design a high-throughput ingestion system in Java?

**Answer:**
- batching
- async I/O
- backpressure
- bounded queues
- separate pools for CPU vs I/O

---

### 172. How do you choose thread pool sizes?

**Answer:**
- CPU-bound: ~ number of cores
- I/O-bound: depends on blocking factor; measure

Use separate pools per dependency.

---

### 173. What is reactive programming and when does it help?

**Answer:**
Helps when you have many concurrent I/O-bound operations.
Not automatically faster; adds complexity.

---

### 174. What is the difference between blocking and non-blocking I/O?

**Answer:**
Blocking ties up a thread while waiting.
Non-blocking uses event loops/callbacks to scale concurrency.

---

### 175. How do you design a search feature (DB vs Elasticsearch)?

**Answer:**
- DB for exact lookups and small datasets
- Elasticsearch for full-text, fuzzy, aggregations

Use dual-write or outbox-based indexing.

---

### 176. How do you design a notification system?

**Answer:**
- store notification state
- async fanout via queue
- idempotent delivery
- retries + DLQ

---

### 177. How do you design a scheduler system in Java?

**Answer:**
- Quartz for persisted jobs
- distributed lock to avoid duplicate execution
- idempotent jobs

---

### 178. What is consistency in distributed systems (CAP)?

**Answer:**
CAP: you can’t have strong Consistency + Availability during Partition.
Most systems choose AP with eventual consistency for scale.

---

### 179. How do you handle distributed locks?

**Answer:**
Options:
- DB (SELECT FOR UPDATE)
- Redis (Redlock caveats)
- ZooKeeper/etcd

Prefer designing without locks when possible.

---

### 180. How do you model data for performance (DB perspective)?

**Answer:**
- proper indexes
- avoid N+1
- denormalize where needed
- use pagination
- avoid large transactions

---

### 181. How do you handle large payloads in APIs?

**Answer:**
- compression
- partial responses/field selection
- streaming
- pre-signed URLs for file uploads

---

### 182. How do you handle multi-tenant architecture?

**Answer:**
- separate DB/schema
- shared DB with tenant_id
- enforce at every query boundary
- secure partitioning

---

### 183. What is blue-green vs canary deployment?

**Answer:**
- blue-green: switch all traffic
- canary: gradually shift traffic, monitor

---

### 184. How do you design observability for Java services?

**Answer:**
- structured logs (correlation IDs)
- metrics (RED/USE)
- tracing (OpenTelemetry)
- dashboards + alerts

---

### 185. What metrics do you track for performance issues?

**Answer:**
- latency p95/p99
- throughput
- error rate
- CPU/memory/GC
- thread pool queue + saturation
- DB query time

---

### 186. How do you find slow DB queries from Java?

**Answer:**
- enable query logs
- add tracing around repository calls
- use APM (New Relic/Datadog)
- analyze explain plans

---

### 187. How do you design a system for exactly-once processing?

**Answer:**
Often achieved as:
- at-least-once + idempotency
- deduplication keys
- transactional outbox

True exactly-once is hard across boundaries.

---

### 188. What is a DLQ and when to use it?

**Answer:**
Dead Letter Queue stores failed messages after retries.
Use for debugging and manual replay.

---

### 189. How do you avoid thundering herd?

**Answer:**
- jitter
- request coalescing
- caching
- randomized backoff

---

### 190. How do you design file processing pipelines?

**Answer:**
- ingest metadata
- store file in object store
- async processing jobs
- progress tracking
- retries

---

### 191. What is the difference between horizontal and vertical scaling?

**Answer:**
- vertical: bigger machine
- horizontal: more machines

Horizontal is preferred for large scale but adds complexity.

---

### 192. How do you control GC pauses in latency-sensitive services?

**Answer:**
- reduce allocation rate
- choose suitable GC (G1/ZGC)
- tune heap and pause targets
- avoid huge objects
- profile allocations

---

### 193. How do you prevent memory bloat in Java apps?

**Answer:**
- bounded caches
- avoid unbounded queues
- streaming
- monitor heap usage
- avoid excessive object churn

---

### 194. What’s the best practice for handling large lists in memory?

**Answer:**
- pagination
- cursor streaming
- process in batches
- avoid collecting entire dataset

---

### 195. How do you design secure secret management?

**Answer:**
- use vault/secret manager
- rotate secrets
- avoid secrets in logs
- least privilege

---

### 196. How do you do load testing for Java services?

**Answer:**
- define SLAs
- test with realistic data and concurrency
- ramp up gradually
- validate p95/p99
- observe dependencies

Tools: Gatling, JMeter, k6.

---

### 197. What is a good approach to performance regression prevention?

**Answer:**
- performance test in CI for critical flows
- track baseline
- alert on regression
- use profiling in staging

---

### 198. How do you design graceful shutdown?

**Answer:**
- stop accepting new traffic
- finish inflight requests
- close resources
- flush buffers
- set termination timeouts

---

### 199. How do you handle feature flags safely?

**Answer:**
- default off
- kill switch
- audit who changes flags
- avoid flag explosion
- remove old flags

---

### 200. If you could pick only 5 Java performance tools, what would they be?

**Answer:**
- JFR (Java Flight Recorder)
- async-profiler
- heap dump + MAT
- GC logs + analysis
- distributed tracing (OpenTelemetry + collector)
