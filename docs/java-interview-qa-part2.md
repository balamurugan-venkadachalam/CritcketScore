# Java Interview Questions & Answers - Part 2 (Q51–Q100)

## Collections, Streams, Functional, I/O, Date/Time

### 51. How does `HashMap` work internally (Java 8+)?

**Answer:**
`HashMap` stores entries in buckets based on `hash(key)`. Collisions are handled by:
- linked list (before)
- **tree bin (red-black tree)** when bucket size exceeds threshold (Java 8+)

Key points:
- Good `hashCode()` distribution is critical
- Default load factor: `0.75`
- Rehash happens when size exceeds `capacity * loadFactor`

---

### 52. What is a good `hashCode()` implementation strategy?

**Answer:**
- Use immutable fields
- Use `Objects.hash(...)` for simplicity
- Avoid random/hash that changes between runs

```java
@Override
public int hashCode() {
  return Objects.hash(id, email);
}
```

---

### 53. What is the difference between `HashMap`, `LinkedHashMap`, and `TreeMap`?

**Answer:**
- `HashMap`: no ordering
- `LinkedHashMap`: insertion order (or access order for LRU)
- `TreeMap`: sorted by key (RB-tree), O(log n)

---

### 54. How do you implement an LRU cache using `LinkedHashMap`?

**Answer:**

```java
class LruCache<K, V> extends LinkedHashMap<K, V> {
  private final int capacity;
  LruCache(int capacity) {
    super(capacity, 0.75f, true); // accessOrder = true
    this.capacity = capacity;
  }
  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > capacity;
  }
}
```

---

### 55. Explain `ConcurrentHashMap` vs `Collections.synchronizedMap`.

**Answer:**
- `synchronizedMap`: single global lock → lower concurrency
- `ConcurrentHashMap`: finer-grained locking/CAS → better scalability; weakly consistent iterators

---

### 56. When would you use `CopyOnWriteArrayList`?

**Answer:**
For **read-heavy** and **write-rare** workloads.
- Reads are lock-free
- Writes copy the entire array (expensive)

Examples: listener lists, configuration snapshots.

---

### 57. Explain `equals()`/`hashCode()` pitfalls with mutable keys in `HashMap`.

**Answer:**
If a key’s fields used by `hashCode()` change after insertion, you can’t find it again.

Rule: keys in hash maps should be **effectively immutable**.

---

### 58. What is the difference between `Iterator` and `ListIterator`?

**Answer:**
- `Iterator`: forward traversal, remove
- `ListIterator`: bidirectional, add/set, index access

---

### 59. What is `Comparable` contract? Common mistakes?

**Answer:**
- Must be consistent and transitive
- Prefer `Comparator` chain

```java
Comparator<User> c = Comparator
  .comparing(User::age)
  .thenComparing(User::name);
```

Mistake: inconsistent compareTo with equals.

---

### 60. How does `ArrayList` grow internally?

**Answer:**
It uses an internal array.
When capacity is insufficient, it allocates a bigger array (roughly 1.5x growth) and copies elements → expensive.

Optimization: initialize with expected size:

```java
new ArrayList<>(expectedSize);
```

---

### 61. What is the difference between `Set` implementations (`HashSet`, `LinkedHashSet`, `TreeSet`)?

**Answer:**
- `HashSet`: hash-based
- `LinkedHashSet`: insertion order
- `TreeSet`: sorted set (RB-tree)

---

### 62. What are `Queue` implementations and typical uses?

**Answer:**
- `ArrayDeque`: fast queue/stack, no nulls
- `PriorityQueue`: heap ordering
- `LinkedList`: also a queue but usually slower

---

### 63. Explain `PriorityQueue` internal behavior.

**Answer:**
Binary heap.
- `offer`/`poll`: O(log n)
- `peek`: O(1)
Used for top-K, scheduling.

---

### 64. How do you choose between `List`, `Set`, `Map` in API design?

**Answer:**
- Use `List` when order & duplicates matter
- Use `Set` for uniqueness constraints
- Use `Map` for key-based lookups

Prefer interfaces in method signatures.

---

### 65. What are Java Streams and when to avoid them?

**Answer:**
Streams provide functional pipelines for processing collections.
Avoid when:
- extremely hot loops where allocations/boxing matter
- debugging clarity is more important
- you need complex mutation

---

### 66. What’s the difference between intermediate and terminal stream operations?

**Answer:**
- Intermediate: `map`, `filter`, `sorted` (lazy)
- Terminal: `collect`, `reduce`, `forEach` (triggers execution)

---

### 67. Explain `map` vs `flatMap`.

**Answer:**
- `map`: 1-to-1 transform
- `flatMap`: 1-to-many then flatten

```java
List<String> words = List.of("a b", "c d");
List<String> tokens = words.stream()
  .flatMap(s -> Arrays.stream(s.split(" ")))
  .toList();
```

---

### 68. How does `Collectors.groupingBy` work? Give example.

**Answer:**

```java
Map<String, List<User>> byRole = users.stream()
  .collect(Collectors.groupingBy(User::role));
```

For counting:

```java
Map<String, Long> counts = users.stream()
  .collect(Collectors.groupingBy(User::role, Collectors.counting()));
```

---

### 69. Explain `reduce()` vs `collect()`.

**Answer:**
- `reduce`: combines elements into single value (associative)
- `collect`: mutable reduction (lists/maps)

Use `collect` for containers.

---

### 70. What are side effects in streams and why are they risky?

**Answer:**
Side effects = mutating external state inside pipeline.
Risky because:
- ordering assumptions
- parallel stream hazards

Prefer returning new results via `collect()`.

---

### 71. When are `parallelStream()` useful and when harmful?

**Answer:**
Useful:
- CPU-heavy stateless operations
- large datasets

Harmful:
- small collections
- blocking I/O
- shared mutable state
- custom thread management requirements

---

### 72. Explain functional interfaces and provide examples.

**Answer:**
Single abstract method interfaces.
Examples:
- `Function<T,R>`
- `Predicate<T>`
- `Supplier<T>`
- `Consumer<T>`

---

### 73. What is method reference and what are its forms?

**Answer:**
- `ClassName::staticMethod`
- `obj::instanceMethod`
- `ClassName::instanceMethod`
- `ClassName::new`

---

### 74. Explain lambda capture rules.

**Answer:**
Captured local variables must be **effectively final**.

```java
int x = 10;
Runnable r = () -> System.out.println(x);
```

---

### 75. What are default methods in interfaces? When to use them?

**Answer:**
Allow evolving interfaces without breaking implementations.
Use sparingly to provide shared behavior.

---

### 76. What is `Optional` map/flatMap usage?

**Answer:**

```java
Optional<User> u = findUser();
String city = u.map(User::address)
  .map(Address::city)
  .orElse("UNKNOWN");
```
Use `flatMap` when mapping returns `Optional`.

---

### 77. Explain Java I/O vs NIO.

**Answer:**
- I/O: stream-based, blocking
- NIO: buffer/channel-based, supports non-blocking and selectors

---

### 78. When do you use `BufferedInputStream` / `BufferedReader`?

**Answer:**
Buffering reduces syscall overhead.
Use for reading many small chunks.

---

### 79. What’s the difference between `Reader/Writer` and `InputStream/OutputStream`?

**Answer:**
- `InputStream/OutputStream`: bytes
- `Reader/Writer`: characters (encoding-aware)

---

### 80. How do you correctly handle character encoding?

**Answer:**
Always specify charset, avoid platform default.

```java
Files.readString(path, StandardCharsets.UTF_8);
```

---

### 81. Explain Java NIO `Path`, `Files`, and common operations.

**Answer:**

```java
Path p = Paths.get("/tmp/a.txt");
Files.writeString(p, "hi", StandardCharsets.UTF_8);
String s = Files.readString(p, StandardCharsets.UTF_8);
```

---

### 82. How do you safely handle large file processing?

**Answer:**
- stream line-by-line
- avoid reading entire file into memory

```java
try (Stream<String> lines = Files.lines(path, UTF_8)) {
  lines.forEach(this::process);
}
```

---

### 83. What is `Serializable` and why is Java serialization discouraged for APIs?

**Answer:**
It’s JVM-specific binary serialization.
Discouraged because:
- security risks
- brittle versioning
- hard to debug
Prefer JSON/Protobuf/Avro.

---

### 84. Explain `transient` and `serialVersionUID`.

**Answer:**
- `transient`: exclude field from serialization
- `serialVersionUID`: version for compatibility

---

### 85. Explain `java.time` vs `java.util.Date`.

**Answer:**
`java.time` is immutable, thread-safe, and clearer (`Instant`, `LocalDateTime`, `ZonedDateTime`).
`Date` is mutable and poorly designed.

---

### 86. What’s the difference between `Instant`, `LocalDateTime`, and `ZonedDateTime`?

**Answer:**
- `Instant`: point on UTC timeline
- `LocalDateTime`: date/time without zone
- `ZonedDateTime`: local date/time + time zone

---

### 87. How do you handle time zones correctly in backend systems?

**Answer:**
- store timestamps in **UTC** (`Instant`)
- convert at boundaries (UI/reporting)
- store zone ID if needed for business rules

---

### 88. What is `Duration` vs `Period`?

**Answer:**
- `Duration`: time-based (seconds, nanos)
- `Period`: date-based (days, months, years)

---

### 89. Explain `BigDecimal` pitfalls.

**Answer:**
- Use string constructor: `new BigDecimal("0.1")`
- Beware scale/rounding

```java
BigDecimal x = new BigDecimal("10.00");
BigDecimal y = new BigDecimal("10");
System.out.println(x.equals(y)); // false
System.out.println(x.compareTo(y) == 0); // true
```

---

### 90. What are `Enum` best practices?

**Answer:**
- Use for fixed known values
- Don’t store ordinal in DB; store name or explicit code

```java
enum Status { ACTIVE, INACTIVE }
```

---

### 91. What is `switch` expression and why is it useful?

**Answer:**
More concise and safer, supports returning values.

```java
String label = switch (status) {
  case ACTIVE -> "A";
  case INACTIVE -> "I";
};
```

---

### 92. What is `try-with-resources` and why is it better than `finally` close?

**Answer:**
Ensures deterministic close even with exceptions and supports suppressed exceptions.

---

### 93. Explain `ClassLoader` basics and common issues.

**Answer:**
Loads classes at runtime.
Issues:
- classpath conflicts (multiple versions)
- `ClassNotFoundException` vs `NoClassDefFoundError`

---

### 94. `ClassNotFoundException` vs `NoClassDefFoundError`?

**Answer:**
- `ClassNotFoundException`: class not found at load time (checked)
- `NoClassDefFoundError`: was present during compile but missing at runtime or failed init

---

### 95. What is reflection and a safe use case?

**Answer:**
Use reflection for frameworks (DI, serialization, mapping), but limit usage in business logic.
Prefer compile-time generation when possible.

---

### 96. What is `ServiceLoader` and when to use it?

**Answer:**
Java SPI mechanism to load implementations at runtime.
Used for plugins.

---

### 97. What is `Collections.unmodifiableList` vs `List.copyOf`?

**Answer:**
- `unmodifiableList`: view; underlying list changes reflect
- `copyOf`: immutable copy

---

### 98. Explain defensive copying.

**Answer:**
Avoid exposing internal mutable state.

```java
class A {
  private final List<String> items;
  A(List<String> items) { this.items = List.copyOf(items); }
  List<String> items() { return items; }
}
```

---

### 99. Explain `Collectors.toMap` collisions and how to handle them.

**Answer:**
If duplicate keys occur, you must provide merge function.

```java
Map<String, User> m = users.stream().collect(Collectors.toMap(
  User::email,
  u -> u,
  (a, b) -> a // keep first
));
```

---

### 100. What’s a good approach to design DTO mapping in Java?

**Answer:**
Options:
- manual mapping (fastest, explicit)
- MapStruct (compile-time)
- reflection mappers (slower)

Prefer explicit mapping at boundaries.
