# Java Interview Questions & Answers - Part 3 (Q101–Q150)

## Concurrency, JVM Internals, and Performance Troubleshooting

### 101. Explain Java Memory Model (JMM) in simple terms.

**Answer:**
JMM defines:
- how threads see memory (visibility)
- ordering guarantees (happens-before)
- allowed reordering by compiler/CPU

---

### 102. What is a happens-before relationship?

**Answer:**
A guarantee that one action’s effects are visible to another.
Examples:
- unlock happens-before lock
- write to `volatile` happens-before subsequent read
- thread start happens-before actions in started thread

---

### 103. Why is `volatile` not enough for `count++`?

**Answer:**
`count++` is read-modify-write (non-atomic). `volatile` ensures visibility, not atomicity.
Use `AtomicInteger` or synchronization.

---

### 104. What are atomic classes and how do they work?

**Answer:**
`AtomicInteger`, `AtomicLong`, etc. use CAS (compare-and-swap) for lock-free updates.

```java
AtomicInteger c = new AtomicInteger();
c.incrementAndGet();
```

---

### 105. Explain CAS and ABA problem.

**Answer:**
CAS updates a value if it matches expected.
ABA: value changes A→B→A, CAS succeeds incorrectly.
Fix: versioning (`AtomicStampedReference`).

---

### 106. What is `synchronized` and what does it guarantee?

**Answer:**
- mutual exclusion
- visibility via happens-before on lock/unlock
- prevents some reorderings

---

### 107. What is `ReentrantLock` and when would you choose it?

**Answer:**
Use it when you need:
- `tryLock()`
- timed lock
- fairness
- interruptible lock acquisition

---

### 108. What is a read-write lock?

**Answer:**
`ReentrantReadWriteLock` allows multiple readers, single writer.
Good for read-heavy shared structures.

---

### 109. Explain thread states.

**Answer:**
- NEW
- RUNNABLE
- BLOCKED (monitor lock)
- WAITING
- TIMED_WAITING
- TERMINATED

---

### 110. `wait()` vs `sleep()` vs `join()`?

**Answer:**
- `wait()`: releases monitor, waits for notify
- `sleep()`: pauses thread, doesn’t release monitor
- `join()`: waits for another thread to finish

---

### 111. What is thread interruption? Best practices?

**Answer:**
Interruption is a cooperative cancellation signal.
Best practices:
- don’t swallow `InterruptedException`
- restore interrupt flag if you can’t handle it

```java
catch (InterruptedException e) {
  Thread.currentThread().interrupt();
}
```

---

### 112. Explain executor framework vs creating threads manually.

**Answer:**
Executors provide:
- pooling
- lifecycle management
- task submission
- better control over queueing/backpressure

---

### 113. What are common thread pool types and pitfalls?

**Answer:**
- `newFixedThreadPool`: bounded threads, unbounded queue → OOM risk
- `newCachedThreadPool`: unbounded threads → resource exhaustion
- `newSingleThreadExecutor`: ordering

Prefer `ThreadPoolExecutor` with bounded queue + rejection policy.

---

### 114. Explain bounded queues and rejection policies.

**Answer:**
Bounded queue prevents unlimited memory use.
Rejection policies:
- AbortPolicy
- CallerRunsPolicy (backpressure)
- DiscardPolicy
- DiscardOldestPolicy

---

### 115. What is `CompletableFuture` and typical use cases?

**Answer:**
Async composition and non-blocking workflows.

```java
CompletableFuture<User> u = CompletableFuture.supplyAsync(() -> loadUser(id));
CompletableFuture<Order> o = u.thenCompose(user -> loadOrder(user));
```

---

### 116. Explain `ForkJoinPool` and work stealing.

**Answer:**
Used for divide-and-conquer tasks.
Workers steal tasks from others to balance load.
Powers parallel streams.

---

### 117. What is `ThreadLocal`? Common pitfalls?

**Answer:**
Provides per-thread storage.
Pitfalls:
- memory leaks in thread pools if not removed
- hidden coupling

```java
try {
  ctx.set(v);
} finally {
  ctx.remove();
}
```

---

### 118. What is false sharing?

**Answer:**
When independent variables share a CPU cache line causing invalidation traffic.
Mitigate with padding or `@Contended` (JVM flag).

---

### 119. What are common concurrency bugs?

**Answer:**
- race conditions
- deadlocks
- livelocks
- starvation
- visibility issues

---

### 120. How do you debug deadlocks in production?

**Answer:**
- take thread dump (`jstack`, kill -3)
- look for “Found one Java-level deadlock”
- check lock ordering
- add timeouts and lock monitoring

---

## JVM Internals

### 121. What are JVM runtime data areas?

**Answer:**
- Heap
- Metaspace
- Java stacks
- PC register
- Native method stacks

---

### 122. Explain class loading phases.

**Answer:**
- Loading
- Linking: verify, prepare, resolve
- Initialization

---

### 123. What is Metaspace? How is it different from PermGen?

**Answer:**
Metaspace stores class metadata and grows in native memory; PermGen was fixed-size heap region (removed in Java 8).

---

### 124. Explain GC roots.

**Answer:**
Objects reachable from GC roots are not collected.
Roots include:
- thread stacks
- static fields
- JNI references

---

### 125. What is stop-the-world (STW) pause?

**Answer:**
GC pauses all application threads to perform parts of collection.
Goal: minimize pause time (G1/ZGC).

---

### 126. Compare common garbage collectors (high level).

**Answer:**
- **Serial**: single thread; small apps
- **Parallel**: throughput
- **CMS** (legacy): low pause, fragmentation
- **G1**: balanced, default in many JDKs
- **ZGC/Shenandoah**: ultra-low pause for large heaps

---

### 127. What are young and old generations?

**Answer:**
Most objects die young → young gen optimized for fast allocation and collection.
Long-lived promoted to old gen.

---

### 128. What is GC tuning and when do you do it?

**Answer:**
Tune only after:
- measuring latency/throughput
- understanding allocation rate
- confirming GC is bottleneck

Use GC logs and profiling.

---

### 129. What is allocation rate and why does it matter?

**Answer:**
High allocation rate increases GC frequency.
Reduce via:
- object reuse carefully
- avoid excessive boxing
- use primitives, arrays
- avoid creating temporary objects in hot loops

---

### 130. How do you analyze GC logs?

**Answer:**
Look for:
- pause times
- frequency
- promotion failures
- old gen growth

Tools: GCViewer, GCEasy.

---

## Performance Troubleshooting

### 131. What are the first steps when an API is slow in production?

**Answer:**
- confirm SLA and scope (which endpoints)
- check metrics: latency, p95/p99, error rate
- check saturation: CPU, memory, DB, thread pools
- add tracing (OpenTelemetry)

---

### 132. How do you differentiate CPU-bound vs I/O-bound latency?

**Answer:**
- CPU high + runnable threads → CPU-bound
- low CPU but many blocked/waiting threads → I/O-bound (DB, network)

---

### 133. Explain backpressure in Java services.

**Answer:**
Backpressure prevents overload by slowing producers.
Examples:
- bounded queues
- caller-runs policy
- rate limiting

---

### 134. What is a memory leak in Java and how do you detect it?

**Answer:**
Leak = objects unintentionally retained.
Detect:
- heap dump (`jmap`) + MAT
- allocation profiling
- monitor old-gen growth

---

### 135. Common causes of memory leaks in Java services?

**Answer:**
- static caches without eviction
- ThreadLocal not removed
- listeners not deregistered
- unbounded collections
- classloader leaks

---

### 136. How do you troubleshoot `OutOfMemoryError`?

**Answer:**
- capture heap dump on OOM
- inspect dominator tree
- find retention path
- fix root cause (eviction, reduce caching, streaming)

---

### 137. What is lock contention and how to detect it?

**Answer:**
Threads frequently BLOCKED waiting for locks.
Detect:
- thread dumps
- async-profiler lock profiling
- JFR (Java Flight Recorder)

---

### 138. How to reduce lock contention?

**Answer:**
- reduce critical section
- use concurrent structures
- use read-write locks
- partition locks (striping)
- avoid nested locks

---

### 139. What is context switching overhead?

**Answer:**
Too many threads cause CPU spent on switching, reducing throughput.
Fix:
- right-size thread pools
- avoid blocking in CPU pools

---

### 140. Explain connection pool tuning (DB/HTTP).

**Answer:**
Tune based on:
- throughput
- latency
- downstream capacity

Avoid:
- too small → waits
- too large → DB overload

---

### 141. What is N+1 problem and why it impacts performance?

**Answer:**
One query per row/entity → huge DB load.
Fix: join fetch/entity graphs/batch fetching/projections.

---

### 142. Explain caching levels in an application.

**Answer:**
- in-process (Caffeine)
- distributed (Redis)
- HTTP/CDN caching

Must consider invalidation and consistency.

---

### 143. What is JIT compilation and how can it affect performance tests?

**Answer:**
JIT optimizes “hot” code after warm-up.
Performance tests should include warm-up iterations.

---

### 144. What is escape analysis?

**Answer:**
JIT can allocate objects on stack / eliminate allocation if object doesn’t escape.
Impacts microbenchmarks.

---

### 145. How do you do reliable microbenchmarking in Java?

**Answer:**
Use JMH.
Avoid `System.nanoTime()` loops.
Account for warm-up, dead-code elimination.

---

### 146. What is the difference between throughput and latency optimization?

**Answer:**
- throughput: maximize ops/sec
- latency: minimize response time (especially tail p99)
Often trade-off.

---

### 147. How do you find hotspots in Java?

**Answer:**
- async-profiler (CPU/alloc)
- JFR
- flame graphs
- sampling profilers

---

### 148. Explain p95/p99 latency and why averages are misleading.

**Answer:**
Average hides tail latency. Users feel p95/p99.
Tail issues often come from GC pauses, contention, slow DB queries.

---

### 149. What is circuit breaker and why is it needed?

**Answer:**
Prevents cascading failures by stopping calls to failing dependency.
Supports half-open recovery.

---

### 150. What is bulkheading?

**Answer:**
Isolate resources (thread pools, connection pools) per dependency so one failure doesn’t take down everything.
