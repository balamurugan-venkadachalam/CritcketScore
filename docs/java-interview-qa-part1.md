# Java Interview Questions & Answers - Part 1 (Q1–Q50)

## Core Java & OOP Fundamentals

### 1. What is the difference between JDK, JRE, and JVM?

**Answer:**
- **JVM**: Executes Java bytecode, provides runtime (GC, JIT, class loading).
- **JRE**: JVM + standard libraries needed to run apps.
- **JDK**: JRE + dev tools (javac, javadoc, jdb, etc.).

---

### 2. What happens when you run a Java program (high-level flow)?

**Answer:**
- **Compile**: `javac` turns `.java` into `.class` (bytecode).
- **Class Loading**: ClassLoader loads bytecode into JVM.
- **Bytecode Verification**: Safety checks.
- **Execution**: Interpreter + JIT compiles hot paths to native code.
- **GC**: Reclaims unreachable objects.

---

### 3. Explain Java’s “write once, run anywhere”.

**Answer:**
Java compiles to **bytecode**, which runs on any platform that has a compatible **JVM**.

---

### 4. What are the main OOP principles?

**Answer:**
- **Encapsulation**: Hide state; expose behavior.
- **Abstraction**: Expose essential features; hide details.
- **Inheritance**: Reuse/extend behavior.
- **Polymorphism**: Same interface, different implementations (overriding).

---

### 5. What is the difference between an `interface` and an `abstract class`?

**Answer:**
- **Abstract class**:
  - Can have state (fields), constructors, concrete + abstract methods.
  - Single inheritance.
- **Interface**:
  - Contract; can have `default`/`static` methods.
  - Multiple implementation.

Rule of thumb:
- Use **interface** for capability/contract.
- Use **abstract class** when sharing code/state across closely related types.

---

### 6. What is method overloading vs overriding?

**Answer:**
- **Overloading**: Same method name, different params (compile-time).
- **Overriding**: Subclass changes behavior of superclass method (runtime).

```java
class A {
  void f(int x) {}
  void f(String s) {} // overload
}

class B extends A {
  @Override
  void f(int x) {} // override
}
```

---

### 7. What is the difference between `==` and `equals()`?

**Answer:**
- `==` compares **references** for objects (and values for primitives).
- `equals()` compares **logical equality** (if implemented).

```java
String a = new String("x");
String b = new String("x");
System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
```

---

### 8. Why must `hashCode()` be consistent with `equals()`?

**Answer:**
Hash-based collections (`HashMap`, `HashSet`) use `hashCode()` to pick a bucket and `equals()` to resolve collisions.

Contract:
- If `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` must be true.

---

### 9. What is the difference between `String`, `StringBuilder`, and `StringBuffer`?

**Answer:**
- `String`: immutable.
- `StringBuilder`: mutable, not thread-safe, faster.
- `StringBuffer`: mutable, synchronized (thread-safe), slower.

---

### 10. What is the String constant pool?

**Answer:**
A JVM-managed pool to reuse identical string literals to save memory.

```java
String a = "hello";
String b = "hello";
System.out.println(a == b); // true (interned literals)

String c = new String("hello");
System.out.println(a == c); // false
```

---

### 11. What is immutability and why is it useful?

**Answer:**
An immutable object’s state cannot change after construction.

Benefits:
- Thread-safety without locking
- Safe sharing/caching
- Easier reasoning

```java
public final class Money {
  private final long cents;
  public Money(long cents) { this.cents = cents; }
  public long cents() { return cents; }
}
```

---

### 12. What does `final` mean for class, method, and variable?

**Answer:**
- `final class`: cannot be subclassed
- `final method`: cannot be overridden
- `final variable`: cannot be reassigned (object may still be mutable)

---

### 13. Explain `static` keyword usage.

**Answer:**
- Belongs to **class**, not instance.
- Common for constants, utility methods, shared state.

```java
class MathUtil {
  static final double PI = 3.14159;
  static int add(int a, int b) { return a + b; }
}
```

---

### 14. What is the difference between stack and heap memory in Java?

**Answer:**
- **Stack**: method frames, local variables, references; fast, thread-local.
- **Heap**: objects/arrays; shared across threads; managed by GC.

---

### 15. Explain pass-by-value in Java.

**Answer:**
Java is **always pass-by-value**.
- For primitives: value copied.
- For objects: reference value copied (still points to same object).

```java
void change(StringBuilder sb) { sb.append("x"); }
void reassign(StringBuilder sb) { sb = new StringBuilder(); }
```

---

### 16. What are access modifiers in Java?

**Answer:**
- `private`: class only
- package-private (no modifier): package
- `protected`: package + subclasses
- `public`: everywhere

---

### 17. What is the difference between composition and inheritance?

**Answer:**
- **Inheritance**: “is-a”, tight coupling.
- **Composition**: “has-a”, flexible, preferred for reuse.

---

### 18. What is autoboxing and unboxing? What are pitfalls?

**Answer:**
Automatic conversion between primitives and wrappers.

Pitfalls:
- `NullPointerException` when unboxing `null`
- performance overhead in tight loops

```java
Integer x = null;
// int y = x; // NPE
```

---

### 19. What are wrapper classes and when do you use them?

**Answer:**
`Integer`, `Long`, `Boolean`, etc.
Used for:
- Collections (generic types require objects)
- nullable values
- utility methods (`Integer.parseInt`)

---

### 20. What is the difference between checked and unchecked exceptions?

**Answer:**
- **Checked**: must be declared/handled (`IOException`).
- **Unchecked** (`RuntimeException`): programming errors (`NullPointerException`).

Guideline:
- Use checked for recoverable conditions.
- Use unchecked for bugs/invalid state.

---

### 21. When should you create custom exceptions?

**Answer:**
When you want domain-specific meaning and handling.

```java
class InsufficientBalanceException extends RuntimeException {
  InsufficientBalanceException(String msg) { super(msg); }
}
```

---

### 22. What is `try-with-resources`?

**Answer:**
Auto-closes resources implementing `AutoCloseable`.

```java
try (BufferedReader br = Files.newBufferedReader(path)) {
  return br.readLine();
}
```

---

### 23. What is the difference between `throw` and `throws`?

**Answer:**
- `throw`: actually throws an exception.
- `throws`: declares method may throw.

---

### 24. What is `finally` used for? When does it not run?

**Answer:**
Used for cleanup; runs even when exception occurs.
May not run if:
- JVM crashes
- `System.exit()` called

---

### 25. What are `Error` vs `Exception`?

**Answer:**
- `Error`: serious JVM problems (OutOfMemoryError), typically not handled.
- `Exception`: application-level issues.

---

### 26. What is a `NullPointerException` and how to reduce it?

**Answer:**
NPE occurs when dereferencing `null`.
Reduce by:
- using `Objects.requireNonNull`
- validating inputs
- using `Optional` for return values (carefully)
- avoiding returning null collections

---

### 27. Explain `Optional` best practices.

**Answer:**
- Good for return types to represent “may be absent”.
- Avoid using it for fields/serialization boundaries.

```java
Optional<User> u = repo.findById(id);
User user = u.orElseThrow();
```

---

### 28. What is the difference between `List.of()` and `Arrays.asList()`?

**Answer:**
- `List.of()` returns **immutable** list (throws on add/set).
- `Arrays.asList()` returns fixed-size view backed by array (can `set`, cannot `add/remove`).

---

### 29. Explain Java Generics and type erasure.

**Answer:**
Generics provide compile-time type safety.
At runtime, generics are removed (**type erasure**), so `List<String>` and `List<Integer>` are both `List`.

---

### 30. Why can’t you create `new T()` in Java generics?

**Answer:**
Because `T` is erased at runtime; the actual type isn’t available.
Workarounds:
- pass `Class<T>`
- use factories/suppliers

```java
<T> T newInstance(Class<T> c) throws Exception {
  return c.getDeclaredConstructor().newInstance();
}
```

---

### 31. What is covariance and contravariance in generics?

**Answer:**
- `? extends T`: covariance (read as T)
- `? super T`: contravariance (write T)

Rule: **PECS**
- Producer: `extends`
- Consumer: `super`

```java
List<? extends Number> nums = List.of(1, 2, 3);
// nums.add(4); // not allowed

List<? super Integer> out = new ArrayList<Number>();
out.add(1);
```

---

### 32. Explain `Comparable` vs `Comparator`.

**Answer:**
- `Comparable`: natural ordering inside class (`compareTo`).
- `Comparator`: external ordering.

```java
Collections.sort(list, Comparator.comparing(User::getAge));
```

---

### 33. What is the difference between `Object` methods `toString`, `equals`, `hashCode`?

**Answer:**
- `toString`: human-readable representation
- `equals`: logical equality
- `hashCode`: hash for hash-based collections

---

### 34. What is `instanceof` pattern matching (newer Java)?

**Answer:**
Allows binding variable if check passes.

```java
if (obj instanceof String s) {
  System.out.println(s.length());
}
```

---

### 35. Explain `record` in Java.

**Answer:**
`record` is a concise immutable data carrier.

```java
public record Point(int x, int y) {}
```
Provides `equals/hashCode/toString` and accessors.

---

### 36. What is the difference between `var` and dynamic typing?

**Answer:**
`var` is **local type inference**; type is still static and fixed at compile time.

```java
var list = new ArrayList<String>(); // list is ArrayList<String>
```

---

### 37. What are annotations and how are they used?

**Answer:**
Metadata used by compiler/frameworks.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Audited {}

@Audited
void pay() {}
```

---

### 38. Explain reflection. When is it dangerous?

**Answer:**
Reflection inspects/changes classes at runtime.
Risks:
- breaks encapsulation
- slower
- fragile (refactoring breaks)
- security concerns

---

### 39. What is serialization? What are pitfalls?

**Answer:**
Serialization converts object to bytes.
Pitfalls:
- security vulnerabilities
- versioning issues
- performance overhead
Prefer JSON/protobuf for external APIs.

---

### 40. Explain `transient` keyword.

**Answer:**
Marks field as not serialized by Java serialization.

```java
class Session implements Serializable {
  transient String token;
}
```

---

### 41. What is `volatile`?

**Answer:**
Guarantees visibility of changes across threads and prevents reordering around the variable.
It does **not** make compound operations atomic.

```java
volatile boolean shutdown;
```

---

### 42. Explain `synchronized`.

**Answer:**
Provides mutual exclusion and happens-before relationship.

```java
synchronized (lock) {
  count++;
}
```

---

### 43. What is the difference between `synchronized` and `Lock` (ReentrantLock)?

**Answer:**
`Lock` provides:
- tryLock
- timed lock
- interruptible lock
- fairness options

---

### 44. What is a deadlock? Give a small example.

**Answer:**
Deadlock occurs when threads wait forever for each other’s locks.

```java
Object a = new Object();
Object b = new Object();

Thread t1 = new Thread(() -> {
  synchronized (a) { synchronized (b) {} }
});
Thread t2 = new Thread(() -> {
  synchronized (b) { synchronized (a) {} }
});
```

---

### 45. What is a race condition?

**Answer:**
When output depends on timing/interleavings of threads.
Fix with synchronization/atomic classes.

---

### 46. Explain `static` initialization order (class loading).

**Answer:**
- static fields initialized in declaration order
- static blocks executed in order
- runs once when class is first initialized

---

### 47. What is the difference between `HashMap` and `Hashtable`?

**Answer:**
- `HashMap`: not synchronized, allows null key/value.
- `Hashtable`: synchronized legacy, disallows null.
Use `ConcurrentHashMap` instead of `Hashtable`.

---

### 48. What is the difference between `ArrayList` and `LinkedList`?

**Answer:**
- `ArrayList`: fast random access, expensive middle inserts.
- `LinkedList`: fast inserts/removes at ends, slow random access.

---

### 49. What is the difference between `fail-fast` and `fail-safe` iterators?

**Answer:**
- Fail-fast: throws `ConcurrentModificationException` (e.g., `ArrayList`).
- Fail-safe: iterates over snapshot/uses concurrent structure (e.g., `CopyOnWriteArrayList`).

---

### 50. How do you design a clean `equals()` / `hashCode()` for entities?

**Answer:**
Guidelines:
- Use immutable business key when possible.
- Avoid using mutable fields.
- Be careful with ORM entities: ID may be null before persistence.

```java
@Override
public boolean equals(Object o) {
  if (this == o) return true;
  if (!(o instanceof User other)) return false;
  return Objects.equals(email, other.email);
}

@Override
public int hashCode() {
  return Objects.hash(email);
}
```
