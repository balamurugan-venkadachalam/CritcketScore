# Spring Advanced Interview Questions - Part 1 (Questions 1-25)

## Advanced Spring Architecture & Best Practices

### 1. How does Spring Boot Auto-Configuration work under the hood?
**Answer:**
Auto-configuration automatically configures your Spring application based on the jar dependencies on your classpath. It is triggered by the `@EnableAutoConfiguration` annotation (included inside `@SpringBootApplication`). 
Under the hood, Spring Boot reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (or `spring.factories` in older versions) to find auto-configuration classes. These classes are evaluated in a specific order and are heavily guarded by `@Conditional` annotations (e.g., `@ConditionalOnClass(DataSource.class)`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`). If the conditions are met, the beans are registered.

### 2. What is the difference between `@Primary` and `@Qualifier`? When should you use which?
**Answer:**
When multiple beans of the same type exist, Spring doesn't know which one to inject, throwing a `NoUniqueBeanDefinitionException`.
- **`@Primary`:** Defines a "default" fallback bean. Use this when you have a primary implementation you want to use 90% of the time, and a secondary one you only use in specific cases.
- **`@Qualifier("beanName")`:** Explicitly requests a specific bean by its name. Use this when you have multiple implementations of equal standing (e.g., `StripePaymentService` vs `PayPalPaymentService`), forcing the developer to explicitly state which one they want at the injection point.

### 3. How do you resolve Circular Dependencies in Spring Boot?
**Answer:**
A circular dependency occurs when Bean A depends on Bean B, and Bean B depends on Bean A. Since Spring Boot 2.6, circular dependencies are prohibited by default and will crash the application on startup.
**Best Practice Solutions:**
1. **Redesign:** The best solution is architectural. Extract the shared logic into a third "Bean C" that both A and B can depend on.
2. **`@Lazy` Injection:** If you must keep the cycle, annotate one of the injected dependencies with `@Lazy`. Spring will inject a proxy instead of the real bean, deferring the initialization until the bean is actually used.
3. **Setter/Field Injection:** Transition away from constructor injection for one of the beans (though this is considered bad practice).

### 4. Why is Constructor Injection preferred over Field Injection (`@Autowired`)?
**Answer:**
Field injection (`@Autowired` directly on fields) hides dependencies, makes unit testing difficult (requires reflection or Spring contexts), and allows for circular dependencies to slip by unnoticed.
Constructor injection ensures that:
- Beans are immutable (can be declared `final`).
- Dependencies are explicitly stated, making it impossible to instantiate the class in a test without providing its required dependencies.
- The class fails fast at startup if a dependency is missing.

### 5. Common Pitfall: Why doesn't `@Transactional` or `@Async` work when called from within the same class?
**Answer:**
Spring relies on **AOP (Aspect-Oriented Programming) Proxies** to implement cross-cutting concerns like `@Transactional`, `@Async`, and `@Cacheable`.
When an external class calls a bean's method, it calls the Proxy, which starts the transaction, then delegates to the real object. However, if Method A calls Method B *within the same class*, it uses the `this` reference, completely bypassing the Spring Proxy, meaning no transaction or async thread is created.
**Fix:** Self-inject the Spring bean into itself, use `AopContext.currentProxy()`, or refactor the method into a separate service.

---

## Microservices Patterns & Cloud

### 6. What is the Saga Pattern and how is it implemented?
**Answer:**
In a microservices architecture, you cannot use traditional ACID database transactions (like 2-Phase Commit) across multiple independent databases reliably. The Saga pattern manages distributed transactions through a sequence of local transactions.
- **Choreography:** Each service publishes an event after its local transaction. Other services listen to this event and execute their local transactions. If a step fails, compensation events are published to undo the previous steps.
- **Orchestration:** A central coordinator (the Orchestrator) tells each service what local transaction to execute. If a failure occurs, the orchestrator tells the already-completed services to execute their compensating (rollback) transactions.

### 7. API Gateway vs Load Balancer: When to use which?
**Answer:**
- **Load Balancer (e.g., AWS ALB, Nginx):** Operates at Layer 4 or Layer 7. Simply distributes incoming network traffic across multiple running instances of a service to prevent overload.
- **API Gateway (e.g., Spring Cloud Gateway):** Operates exclusively at Layer 7. Acts as the single entry point for all clients into the microservice ecosystem. It handles cross-cutting concerns like SSL termination, authentication/authorization validation, rate limiting, request routing, and payload aggregation.

### 8. How do you implement Distributed Tracing in Spring Boot?
**Answer:**
In a distributed system, a single user request might travel through 5 different microservices. To debug errors or performance bottlenecks, we use Distributed Tracing.
Using **Micrometer Tracing** (formerly Spring Cloud Sleuth) combined with **Zipkin/Jaeger**:
- The framework generates a `Trace ID` (unique for the entire request journey) and a `Span ID` (unique for each service boundary).
- These IDs are automatically injected into the HTTP headers (e.g., `X-B3-TraceId`) and MDC (Mapped Diagnostic Context) for logging.
- Logs from all microservices can be aggregated into Splunk/ELK and filtered using the single `Trace ID` to see the entire journey.

### 9. Explain the Circuit Breaker Pattern.
**Answer:**
When Service A calls Service B, and Service B becomes unresponsive, Service A will block its threads waiting for a timeout. Under high load, Service A will exhaust all its threads and crash (cascading failure).
A Circuit Breaker (e.g., **Resilience4j**) monitors failures:
- **CLOSED:** Normal state. Requests flow freely.
- **OPEN:** If the failure rate exceeds a threshold, the circuit trips. All requests fail instantly without attempting a network call, giving Service B time to recover. Fallback methods can return cached/default data.
- **HALF-OPEN:** After a timeout, it allows a limited number of test requests through. If they succeed, it closes the circuit; if they fail, it trips back to OPEN.

### 10. Feign Client Best Practices and Common Pitfalls
**Answer:**
Spring Cloud OpenFeign declaratively generates REST clients for you.
**Best Practices:**
- Always configure a specific connection timeout and read timeout; never rely on the defaults.
- Enable Feign logging (`Logger.Level.BASIC` or `FULL`) to see raw requests/responses during debugging.
- Use `ErrorDecoder` to intercept specific HTTP errors from downstream services and translate them into domain-specific exceptions.
**Pitfall:** Not enabling a circuit breaker on Feign clients. If the downstream service hangs, Feign will hang your local threads.

### 11. How do you manage Centralized Configuration for Microservices?
**Answer:**
Managing `application.yml` files manually across 50 microservices is impossible.
We use **Spring Cloud Config Server**. It connects to a centralized Git repository where all configuration files are stored securely. When a microservice starts, it reaches out to the Config Server to download its specific configuration. 
Combined with **Spring Cloud Bus** (using Kafka/RabbitMQ) and `@RefreshScope`, you can commit a change to Git and hit a `/actuator/busrefresh` webhook to dynamically reload configurations across all running instances without a restart.

### 12. How do you handle schema changes when migrating from Monolith to Microservices?
**Answer:**
The **Strangler Fig Pattern** is typically used:
1. Create mapping views or use CDC (Change Data Capture) via tools like **Debezium** to mirror data from the monolithic database to the new microservice's isolated database in real-time.
2. Direct all "Reads" to the new Microservice database.
3. Once stable, direct all "Writes" to the new Microservice database, and sync backward strictly for legacy monolith support.
4. Eventually, deprecate the monolithic tables entirely.

### 13. Service Discovery: How does Netflix Eureka work?
**Answer:**
In cloud environments, IP addresses change constantly due to auto-scaling. Services cannot be hardcoded with static IPs.
- Services register their dynamic IP and Port with a central **Eureka Server** on startup.
- Clients ask Eureka for the location of "ORDER-SERVICE", and Eureka returns a list of healthy, available IP addresses.
- Clients maintain a local cache of this registry so they don't have to query Eureka on every single API call, shielding them from Eureka server outages.

### 14. What are the best practices for inter-service security?
**Answer:**
- **Zero Trust Network:** Don't assume an internal network is safe.
- **mTLS (Mutual TLS):** Encrypt traffic between microservices, requiring both the client and server to authenticate each other using certificates (often handled by Service Meshes like Istio).
- **JWT Propagation:** Have the API Gateway validate the initial JWT, but pass that JWT token down the chain in HTTP Headers so downstream services know the identity of the end user initiating the action.

### 15. Event-Driven Architecture: Kafka vs RabbitMQ in Spring Boot
**Answer:**
- **RabbitMQ:** A traditional message broker (Smart Broker, Dumb Consumer). Excellent for complex routing rules, priority queues, and strict delivery guarantees. Once a message is consumed and acknowledged, it is deleted.
- **Kafka:** A distributed append-only log (Dumb Broker, Smart Consumer). Excellent for massive throughput and event streaming. Messages are retained for days/weeks, allowing consumers to "replay" events from the past. Perfect for Event Sourcing environments.

---

## Advanced Principles & System Design

### 16. What is the CQRS Pattern?
**Answer:**
Command Query Responsibility Segregation (CQRS) separates the models you use to **Read** information (Queries) from the models you use to **Update** information (Commands).
In high-scale Spring systems, write operations are directed to a highly normalized relational database (Command Model), while events are published via Kafka to eventually populate highly denormalized read-optimized databases like Elasticsearch or MongoDB (Query Model), allowing for massive read scalability.

### 17. How do you design an Idempotent API?
**Answer:**
Idempotency means creating an API where making multiple identical requests has the same effect as making a single request (crucial for payment systems handling retries).
- The client generates a unique `Idempotency-Key` (UUID) and sends it in the header.
- In Spring, an interceptor checks Redis/Database for that specific key. 
- If found, the server immediately returns the cached, successful response from the previous attempt.
- If not found, the operation executes, and the result is stored against that key for future repeat requests.

### 18. What is the API Composition Pattern?
**Answer:**
In microservices, data is scattered. A front-end client shouldn't have to make 5 different network calls to fetch User Profile, Orders, Shipping, and Payment data.
The API Composition pattern involves creating an **Aggregator Service** (often via Spring WebFlux to do it concurrently without blocking threads) that takes a single request, fans out to the 4 underlying services simultaneously, merges the JSON payloads together, and returns a single unified DTO to the client.

### 19. Database Sharding vs Read Replicas
**Answer:**
- **Read Replicas:** The data is duplicated. Best when your system is read-heavy. Spring Boot can route all `@Transactional(readOnly = true)` methods to the Replica database connection pool, leaving the Master database purely for writes.
- **Sharding:** The data is partitioned. If your database simply holds too much data to fit on one machine, you split it. Users A-M go to Database 1, Users N-Z go to Database 2. Routing logic (Sharding Key) determines the correct database.

### 20. Designing Webhook Delivery in Spring Boot
**Answer:**
If you need to proactively send events to 3rd party clients:
1. Never send a webhook directly within an API transaction. It blocks the thread and introduces network latency.
2. Publish an intent event to Kafka or a DB queue.
3. Have a background worker process read from the queue and send the HTTP POST request.
4. Implement exponential backoff for retries if the 3rd party receives a 500 error.
5. Use Dead Letter Queues (DLQ) for permanently failed deliveries.

---

## Best Practices & Common Pitfalls

### 21. Common Pitfall: Storing State in Spring Singleton Beans
**Answer:**
By default, all Spring Beans are Singletons. This means only one instance of the class exists in the JVM, and it perfectly serves multiple concurrent requests via separate threads.
**Pitfall:** Declaring a non-final instance variable (e.g., `private String currentUser`) inside a `@Service` bean. Because threads share the exact same object instance, Thread A might write to `currentUser`, and Thread B instantly overwrites it, causing massive data corruption.
**Fix:** Pass state via method parameters or use `ThreadLocal`/`Request scope` beans if absolutely necessary.

### 22. Returning direct Entity classes to the frontend
**Answer:**
Never return JPA `@Entity` classes directly from a `@RestController`.
1. It exposes internal database structures and sensitive fields (like password hashes).
2. Modifying the database schema immediately breaks the API contract.
3. Can trigger infinite recursion on bidirectional relationships during Jackson JSON serialization.
**Best Practice:** Always map Entities to immutable Data Transfer Objects (DTOs) using tools like MapStruct before returning to the Controller layer.

### 23. Exploding your DB with N+1 Queries disguised as clear code
**Answer:**
Relying on simple `get()` methods on nested collections causes N+1 queries. Even worse is iterating over a `List<User>` and calling a remote Microservice or external API per loop iteration. N+1 network calls will crash any system under load.
**Fix:** Always use bulk endpoints, `JOIN FETCH`, or `@EntityGraph`. Fetch all data ahead of time in a single batch query, map it by ID in memory, and then loop over it.

### 24. Hardcoding Thread.sleep() or missing timeouts in RestTemplate
**Answer:**
If you make an HTTP call with `RestTemplate` without explicitly configuring a timeout factory, the underlying JDK networking implementation uses an infinite block for read timeouts. If the remote API hangs, your thread hangs permanently. Eventually, Tomcat runs out of threads and the entire server dies.
**Fix:** Always use a `RestTemplateBuilder` or `WebClient` configured with strict 2-3 second read/connect timeouts.

### 25. Throwing away Exception Stack Traces
**Answer:**
Developers often write `try { ... } catch (Exception e) { log.error("Error occurred"); throw new CustomException("Failed to save"); }`.
This completely destroys the original stack trace, making debugging impossible in production.
**Fix:** Always pass the original exception as the cause to the new exception wrapper: `throw new CustomException("Failed", e);` and make use of `@ControllerAdvice` for global exception handling.
