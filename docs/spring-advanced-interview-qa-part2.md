# Spring Advanced Interview Questions - Part 2 (Questions 26-50)

## Troubleshooting & Real-Time Issues

### 26. You encounter a memory leak in your Spring Boot app. How do you diagnose and fix it?
**Answer:**
A memory leak in Java occurs when objects are no longer used by the application but the Garbage Collector cannot remove them because they are still being referenced (e.g., lingering objects in static maps, unclosed resources, or excessive `ThreadLocal` variables).
**Diagnosis:**
1. Enable Heap Dumps on OOM (`-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/path`).
2. Alternatively, trigger a manual heap dump using `jmap -dump:format=b,file=heap.bin <pid>` or Actuator's `/actuator/heapdump` endpoint.
3. Analyze the dump using Eclipse MAT (Memory Analyzer Tool) or VisualVM. Look for the "Dominator Tree" to find the largest objects retaining memory. Look for custom domain classes or large collections.
**Fix:** Explicitly clear caches, close `InputStream` resources using try-with-resources, and ensure background thread tasks are properly terminated.

### 27. The application CPU is pinned at 100%. What are your steps to identify the culprit?
**Answer:**
High CPU usually points to an infinite loop, excessive Garbage Collection (thrashing), or complex regex/hash calculations.
**Steps:**
1. Identify the exact thread consuming CPU using `$ top -H -p <pid>`. Note the PID of the highest thread and convert it to Hexadecimal.
2. Generate a thread dump using `jstack <pid> > threads.txt` (or `/actuator/threaddump`).
3. Open `threads.txt` and search for the Hexadecimal thread ID. You will see the exact Java stack trace, pointing to the exact line of code causing the bottleneck.
If it's GC thrashing, the heap is completely full, and the JVM is burning 100% CPU desperately trying to free up memory (and failing).

### 28. What causes an `OutOfMemoryError: Metaspace` in Spring applications?
**Answer:**
Metaspace is the native memory region where the JVM stores class metadata (the definitions of classes and methods).
In Spring Boot, it is rarely due to your own application code. It usually occurs during hot-reloading (e.g., Spring Boot DevTools reloading classes repeatedly without cleaning up old ones), or heavily dynamic systems using CGLIB/ByteBuddy to generate an infinite amount of dynamic proxy classes at runtime.
**Fix:** Investigate dynamically generated proxies, and optionally increase the boundary using `-XX:MaxMetaspaceSize`.

### 29. Thread Pool Exhaustion in Tomcat vs HikariCP
**Answer:**
- **Tomcat Exhaustion:** Occurs when your HTTP request threads back up. Spring Boot's default Tomcat server has a `max-threads` of 200. If 200 external users request a slow API simultaneously, all 200 threads block. The 201st user gets a 503 Service Unavailable timeout. Fix: Increase threads, move to async WebFlux, or fix the slow downstream dependencies.
- **HikariCP Exhaustion (`Connection is not available`):** Occurs when your default 10 database connections are all checked out by active queries, and the 11th thread waits past the `connectionTimeout` (default 30s) and fails. Fix: Ensure transactions (`@Transactional`) are kept extremely short. Never make external REST API calls within a database transaction!

### 30. How do you gracefully shutdown a Spring Boot application?
**Answer:**
When deploying to Kubernetes or killing a process, immediate termination drops all in-flight HTTP requests and actively processing database transactions.
**Implementation:**
In `application.yml`, set `server.shutdown: graceful` and `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
When the server receives a SIGTERM signal, Tomcat stops accepting *new* HTTP requests at the network layer but allows currently processing requests up to 30 seconds to finish their logic and return responses.

---

## Performance Optimization

### 31. RestTemplate vs WebClient: When to use which for performance?
**Answer:**
- **RestTemplate** is synchronous and blocking. A thread is uniquely assigned to a network request, and blocks entirely, doing nothing while waiting for the remote server's response. Under high concurrency, this exhausts thread pools.
- **WebClient** (from Spring WebFlux) is asynchronous and non-blocking, built on Netty. A single thread sends the request, immediately returns to the pool to serve other users, and handles the response later via a callback when the network I/O finishes.
**Performance:** WebClient is drastically more performant and requires significantly less memory/threads under heavy load. `RestTemplate` is currently in maintenance mode; newly built services should use `WebClient` or `RestClient` (introduced in Spring 6.1).

### 32. What is a Cache Stampede (Thundering Herd) and how do you prevent it in Spring?
**Answer:**
A cache stampede occurs when a highly requested cached item (like a viral news article on a homepage) suddenly expires. Because thousands of concurrent requests check the cache simultaneously, they all see a "miss" and immediately query the heavy backend database simultaneously, crushing the database.
**Prevention in Spring:**
Using the `@Cacheable(sync = true)` attribute. This tells Spring Cache to block all threads *except the first one*. The first thread executes the heavy DB query and populates the cache. The remaining threads wait, then read the newly populated cache.

### 33. How do you optimize Spring Boot startup time?
**Answer:**
Fast startup is crucial for Serverless (AWS Lambda) and Kubernetes Auto-Scaling.
1. **Spring Native (GraalVM):** Compiles your application into an AOT (Ahead-of-Time) native executable, dropping startup times from seconds to mere milliseconds.
2. **Lazy Initialization:** Setting `spring.main.lazy-initialization=true` prevents Spring from instantiating all beans at startup. They are only created on their first HTTP request. (Reduces startup time, but first requests take a penalty).
3. **CDS (Class Data Sharing):** Introduced broadly in Java 21, it caches parsed class metadata on disk to speed up subsequent JVM launches.

### 34. Tuning the HikariCP Connection Pool
**Answer:**
HikariCP is the default database pool in Spring Boot, engineered for extreme performance.
**Crucial Parameters:**
- `maximumPoolSize`: The maximum number of actual open connections to the DB. Don't make this 1,000! A pool size of 10-20 is often optimal even for high-load systems due to context-switching overhead on the database CPU.
- `connectionTimeout`: How long a thread will wait (default 30s) before throwing an exception if the pool is empty.
- `minimumIdle`: Try to keep this equal to `maximumPoolSize` to prevent sudden connection-churn latency spikes during burst traffic.

### 35. Optimizing JSON Serialization / Deserialization with Jackson
**Answer:**
Jackson is heavily reflection-based and can become a bottleneck when processing massive JSON payloads.
- **Afterburner / Blackbird Modules:** Register these Jackson modules in Spring Boot. They generate and compile bytecode for getter/setter access instead of using slow reflection.
- **Only parse what you need:** Use `@JsonIgnoreProperties(ignoreUnknown = true)` on DTOs and only declare the 3 fields you actually need from a 100-field API response.
- **Streams:** For gigabyte-sized JSON files, skip standard mapping. Use Jackson's `JsonParser` stream API to read and process token-by-token sequentially without loading the entire string into memory.

### 36. How to correctly handle Large File Uploads/Downloads without crashing the heap?
**Answer:**
A common mistake is reading an entire file into a `byte[]` in a Controller memory. If a file is 1GB, and two users upload at once, your heap is destroyed with OOM.
**Upload:** Accept a `MultipartFile` and immediately stream its `InputStream` directly to the disk or AWS S3 bucket using an I/O buffer block, bypassing total memory retention.
**Download:** Return a `StreamingResponseBody`, `ResourceRegion`, or use `HttpServletResponse.getOutputStream()`. Spring will stream the file chunks back to the client directly from the disk.

### 37. Database Deadlocks vs Application Deadlocks
**Answer:**
- **Database Deadlock:** Transaction A locks row 1 and wants row 2. Transaction B locks row 2 and wants row 1. Both wait forever. The DBMS detects this, throws a `DeadlockLoserDataAccessException` to Spring, and forces one transaction to roll back.
- **Application Deadlock:** Thread A locks Java Monitor 1 and wants Monitor 2. Thread B locks Monitor 2 and wants Monitor 1. Both wait forever. The JVM *does not* kill these threads. They hang permanently, creating zombie applications. Detect via thread dumps.

### 38. Best Practices for Logging in High-Throughput systems
**Answer:**
System I/O (writing to console or disk files) is notoriously slow and blocking.
Under heavy load, synchronously writing standard logs will grind your application to a halt as threads queue up for the disk controller.
**Fix:** Switch Logback or Log4j2 to use **AsyncAppenders / LMAX Disruptor**. This delegates log serialization to a low-priority background thread, allowing the main HTTP worker threads to return to the user instantly.

### 39. Garbage Collection Tuning for Microservices (G1GC vs ZGC)
**Answer:**
- **G1GC:** The default in modern Java (Java 9+). Excellent balance of throughput and predictable pause times. Best for heaps up to 16GB.
- **ZGC (Z Garbage Collector):** Available reliably in Java 17/21. It performs all expensive work concurrently with application threads. Pause times are sub-millisecond, regardless of whether your heap is 100MB or 16TB. If your Spring Boot app suffers from latency spikes due to stop-the-world GC pauses on large payloads, switch to ZGC (`-XX:+UseZGC`).

### 40. gRPC vs REST for internal microservice communication
**Answer:**
While Spring Boot defaults to REST (JSON over HTTP/1.1), it is text-heavy, verbose, and poorly suited for extremely high-throughput internal backend traffic.
**gRPC (Protobuf over HTTP/2):**
Uses strict binary contracts. Payloads are drastically smaller to serialize/deserialize and require significantly less network bandwidth. HTTP/2 allows multiplexing (sending dozens of requests simultaneously over a single TCP connection). Only use gRPC internally behind the firewall; expose standard REST endpoints to the public front-end.

---

## System Design Implementation in Spring

### 41. Design an API Rate Limiting System in Spring Boot
**Answer:**
To protect against DDoS attacks or enforce subscription tiers, Rate Limiting caps requests (e.g., 100 requests / minute).
**Implementation:**
Use **Redis** combined with a Lua script (to execute atomic lock-and-increment operations) utilizing the **Token Bucket Algorithm**.
Typically, this logic is shifted left into the **Spring Cloud Gateway** using the built-in `RequestRateLimiterGatewayFilterFactory`, preventing spam requests from ever touching your backend microservices.

### 42. Design a High-Concurrency URL Shortener
**Answer:**
The crucial part is generating a unique, short, collision-free key instantly.
**Implementation:**
- A centralized database generates a sequential ID.
- Convert the Base-10 ID into a Base-62 string (a-z, A-Z, 0-9).
- A 6-character string yields 56 billion unique URLs.
- For high availability scalability, implement **Zookeeper** to issue massive pre-allocated ranges to individual Spring Boot instances (e.g., Instance 1 gets ID range 1-1,000,000 to distribute entirely in local memory without hitting the DB).

### 43. Designing a Global Notification System (Email / SMS)
**Answer:**
Notification APIs (Sendgrid/Twilio) often suffer downtime or extreme rate limits.
**Implementation:**
- Spring Boot exposes an API to accept notification payloads and instantly returns an HTTP 202 Accepted.
- The payload is serialized and dumped into a **Kafka Topic**.
- A separate Spring Boot Worker-Group consumes the topic, attempting to contact Sendgrid.
- If Sendgrid errors out (5xx), the worker pushes the payload to a separate Retry Queue with exponential delay logic.
- A final DLQ (Dead Letter Queue) captures hopelessly failing notifications for manual inspection.

### 44. Real-time Leaderboard System (Gaming)
**Answer:**
Updating a relational database hundreds of times a second per user and querying the top 10 requires massive sorting power and locks.
**Implementation:**
Integrate **Redis Sorted Sets (ZSET)**.
In Spring Boot, use `StringRedisTemplate.opsForZSet()`. Add members with their score (`ZADD`). Redis automatically sorts them efficiently. Fetching the top 10 is an instant O(log(N)) operation using `ZREVRANGE`. Back up final scores to a relational database periodically via a scheduled cron job to ensure persistence.

### 45. Scaling WebSockets horizontally across multiple Spring Boot instances
**Answer:**
WebSockets establish a persistent TCP connection to a single specific server. If User A connects to Instance 1, and User B connects to Instance 2, Instance 1 cannot easily broadcast a chat message to User B because they are held in separate JVM memory spaces.
**Implementation:**
Integrate **Redis Pub/Sub** or **RabbitMQ**.
When User A sends a message to Instance 1, Instance 1 publishes the message to a central Redis Topic. ALL Spring Boot instances are subscribed to that topic. Instance 2 receives the event from Redis and pushes it down the WebSocket to User B.

### 46. What is Event Sourcing?
**Answer:**
Instead of storing the "current state" of an object in a row (e.g., Shopping Cart total is $50), Event Sourcing mandates appending specific, immutable events to a ledger (e.g., Item Added $20, Item Added $30).
**Implementation:**
Using tools like **Axon Framework** with Spring Boot, every action generates an Event. The current state represents a fold/reduction of all events played back from the beginning of time. This guarantees perfect auditability, time-travel debugging, and naturally resolves write-contention in heavy distributed systems.

### 47. Handling massive localized static traffic (CDN Integration)
**Answer:**
Spring Boot should rarely serve static assets (images, massive PDFs) directly in production.
**Implementation:**
Upload user assets directly to Amazon S3. Put CloudFront (CDN) in front of S3.
Return the CDN URLs back to the client via the Spring API.
For dynamic API traffic (common lookup dictionaries), configure standard `@Cacheable` APIs on Spring Boot, but inject standard `Cache-Control: max-age=3600` headers so the CDN/Load Balancer traps and caches the actual HTTP JSON responses geographically close to the user.

### 48. Designing a resilient Web Scraping / Backend Job system
**Answer:**
Long-running jobs (e.g., 40 mins) executing within a simple Spring Boot `@Async` method are vulnerable. If the server is restarted or crashes, the job context is entirely lost.
**Implementation:**
Use a persistent job scheduling framework like **Quartz** or cloud-native solutions like **Temporal.io**. It checkpoints execution state to a database. If the server crashes, another instance automatically sweeps up the orphaned job and resumes execution precisely from the last checkpoint.

### 49. Why and when would you use NoSQL (MongoDB/Cassandra) with Spring Data?
**Answer:**
Relational databases (PostgreSQL/MySQL) excel at strong consistency, normalized schemas, and joins.
**MongoDB (Document):** Use when data shape is highly unstructured or polymorphic, or when you consistently query and return large, complex hierarchical JSON documents in their entirety without needing multiple SQL joins.
**Cassandra (Wide-Column):** Use when write scalability must be infinite and highly available across multiple geographic datacenters (e.g., IoT continuous sensor data ingestion, massive time-series event logging).

### 50. Implementing "Remember Me" securely across distributed Spring applications
**Answer:**
Storing a static Remember-Me token leaves the system vulnerable to token theft. If stolen, a hacker has permanent access.
**Implementation:**
Use the **Persistent Token Approach** heavily vetted in Spring Security.
Generate a random `series_id` and a random `token_secret` in a central DB table. Send both to the user in a secure cookie. When they return, validate both. Upon success: *rotate the token_secret immediately*, update the DB, and send the new secret to the user.
If a hacker steals the cookie and uses it, the token_secret rotates. When the legitimate user eventually tries to use their stolen cookie, the secret fails. Spring Security registers this anomaly as a token theft via `series_id` collision, and instantly deletes entirely all sessions for that user, enforcing an immediate re-authorization login.
