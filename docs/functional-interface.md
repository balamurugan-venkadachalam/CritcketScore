# Java Functional Interfaces — Complete Reference

## Table of Contents
1. [What is a Functional Interface?](#what-is-a-functional-interface)
2. [How Functional Interfaces Work Internally](#how-functional-interfaces-work-internally)
3. [Core Functional Interfaces (java.util.function)](#core-functional-interfaces)
   - [Function](#1-functiont-r)
   - [Predicate](#2-predicatet)
   - [Consumer](#3-consumert)
   - [Supplier](#4-suppliert)
   - [BiFunction](#5-bifunctiont-u-r)
   - [BiPredicate](#6-bipredicatet-u)
   - [BiConsumer](#7-biconsumert-u)
   - [UnaryOperator](#8-unaryoperatort)
   - [BinaryOperator](#9-binaryoperatort)
4. [Primitive Specializations](#primitive-specializations)
5. [Older Functional Interfaces (Pre-Java 8)](#older-functional-interfaces-pre-java-8)
6. [Creating Custom Functional Interfaces](#creating-custom-functional-interfaces)
7. [Method References as Functional Interfaces](#method-references-as-functional-interfaces)
8. [Composition and Chaining](#composition-and-chaining)
9. [Real-World Usage in Stream API](#real-world-usage-in-stream-api)
10. [Interview Questions & Answers](#interview-questions--answers)

---

## What is a Functional Interface?

A functional interface has **exactly one abstract method** (SAM — Single Abstract Method). It can have any number of `default` or `static` methods. The `@FunctionalInterface` annotation is optional but recommended.

```java
@FunctionalInterface
public interface Greeting {
    String greet(String name);          // THE one abstract method (SAM)

    // These are allowed:
    default String shout(String name) { return greet(name).toUpperCase(); }
    static String wave() { return "👋"; }
}
```

### Why do they exist?

They are the **foundation of lambdas**. A lambda expression is just a compact way to implement a functional interface:

```
 BEFORE Java 8 — Anonymous inner class (verbose)
 ───────────────────────────────────────────────
 Greeting g = new Greeting() {
     @Override
     public String greet(String name) {
         return "Hello, " + name;
     }
 };

 AFTER Java 8 — Lambda expression (concise)
 ───────────────────────────────────────────
 Greeting g = name -> "Hello, " + name;

 Both produce the EXACT same result.
 The lambda IS an implementation of the functional interface.
```

### Real-World Analogy: Power Outlet and Plug

```
 ╔══════════════════════════════════════════════════════════════╗
 ║  Functional Interface = Power Outlet (standard shape)       ║
 ║  Lambda / Method Ref  = Plug (anything that fits the shape) ║
 ╠══════════════════════════════════════════════════════════════╣
 ║                                                             ║
 ║  The outlet defines the CONTRACT:                           ║
 ║    "Give me something with 2 prongs"                        ║
 ║    → Function<String, Integer> = "give me String, return Integer" ║
 ║                                                             ║
 ║  Any plug that fits will work:                              ║
 ║    🔌 Lamp        → s -> s.length()                         ║
 ║    🔌 Fan         → String::length                          ║
 ║    🔌 Charger     → s -> Integer.parseInt(s)                ║
 ║                                                             ║
 ║  The outlet doesn't care WHAT the plug does internally,     ║
 ║  only that it matches the shape (input → output types).     ║
 ╚══════════════════════════════════════════════════════════════╝
```

---

## How Functional Interfaces Work Internally

### Lambda → Functional Interface Mapping

When you write a lambda, the compiler matches it to a functional interface based on the **method signature**:

```
 Lambda Expression              Matched Interface         SAM Signature
 ─────────────────              ─────────────────         ─────────────
 x -> x > 5                    Predicate<Integer>        boolean test(T)
 x -> x * 2                    Function<Integer,Integer> R apply(T)
 x -> System.out.println(x)    Consumer<String>          void accept(T)
 () -> new Random().nextInt()  Supplier<Integer>         T get()
 (a, b) -> a + b               BiFunction<I,I,I>         R apply(T, U)
 (a, b) -> a > b               BiPredicate<I,I>          boolean test(T, U)
```

### Under the Hood: What the JVM Does

```
 Your code:
   Predicate<Integer> isEven = n -> n % 2 == 0;

 What JVM sees:
 ┌────────────────────────────────────────────────────────┐
 │ 1. Compiler verifies lambda matches Predicate.test()  │
 │    - Input: Integer ✓   Return: boolean ✓             │
 │                                                       │
 │ 2. JVM generates a hidden class at runtime via        │
 │    invokedynamic (NOT anonymous inner class)           │
 │                                                       │
 │ 3. The hidden class implements Predicate<Integer>:     │
 │    class Lambda$$1 implements Predicate<Integer> {     │
 │        public boolean test(Integer n) {               │
 │            return n % 2 == 0;                         │
 │        }                                              │
 │    }                                                  │
 │                                                       │
 │ 4. isEven now holds an instance of this hidden class  │
 └────────────────────────────────────────────────────────┘
```

---

## Core Functional Interfaces

### 1. `Function<T, R>`
Takes one input of type T, returns a result of type R. The **transformer/converter** of functional interfaces.

**Analogy: Juice Maker** — You put in an orange (input), you get out orange juice (output). Input and output can be different types.

```java
// SAM: R apply(T t)

// String → Integer (extract length)
Function<String, Integer> strLength = s -> s.length();
strLength.apply("Hello");   // 5

// String → String (transform)
Function<String, String> toUpper = String::toUpperCase;
toUpper.apply("hello");     // "HELLO"

// Employee → String (extract field)
Function<Employee, String> getName = Employee::name;
getName.apply(new Employee("Alice", "Eng", 90000, List.of()));  // "Alice"

// Parse string to integer
Function<String, Integer> parser = Integer::parseInt;
parser.apply("42");         // 42

// Used in Stream.map()
List<Integer> lengths = names.stream()
    .map(strLength)         // Function passed to map()
    .collect(Collectors.toList());
// [5, 3, 7, 5, 5]
```

**Key methods:**
```java
// andThen — chain: apply this THEN that
Function<String, String> upperThenTrim = toUpper.andThen(String::trim);

// compose — chain: apply that FIRST, then this
Function<String, Integer> trimThenLength = strLength.compose(String::trim);

// identity — returns input as-is (useful as default/no-op)
Function<String, String> noOp = Function.identity();
noOp.apply("hello");  // "hello"
```

---

### 2. `Predicate<T>`
Takes one input, returns `boolean`. The **filter/checker** of functional interfaces.

**Analogy: Airport Security Scanner** — Every passenger (input) goes through. Scanner returns YES (pass) or NO (reject). It doesn't transform the passenger, just tests them.

```java
// SAM: boolean test(T t)

// Check if number is even
Predicate<Integer> isEven = n -> n % 2 == 0;
isEven.test(4);    // true
isEven.test(7);    // false

// Check if string is not empty
Predicate<String> notEmpty = s -> !s.isEmpty();
notEmpty.test("");       // false
notEmpty.test("hello");  // true

// Check employee salary
Predicate<Employee> highEarner = e -> e.salary() > 70000;

// Used in Stream.filter()
List<Integer> evens = numbers.stream()
    .filter(isEven)           // Predicate passed to filter()
    .collect(Collectors.toList());
// [2, 4, 6, 8, 10]
```

**Key methods:**
```java
// and — logical AND (both must be true)
Predicate<Integer> evenAndPositive = isEven.and(n -> n > 0);

// or — logical OR (either can be true)
Predicate<String> emptyOrNull = Predicate.<String>isEqual(null).or(String::isEmpty);

// negate — logical NOT (flip result)
Predicate<Integer> isOdd = isEven.negate();

// isEqual — static factory for equality check
Predicate<String> isAlice = Predicate.isEqual("Alice");
isAlice.test("Alice");  // true
isAlice.test("Bob");    // false

// not — static negation (Java 11+)
Predicate<String> notBlank = Predicate.not(String::isBlank);
```

---

### 3. `Consumer<T>`
Takes one input, returns **nothing** (void). The **end-of-the-line action** — it consumes data without producing a result.

**Analogy: Paper Shredder** — You feed in a document (input). The shredder processes it. Nothing comes back out. It performs a **side effect** (shredding).

```java
// SAM: void accept(T t)

// Print to console
Consumer<String> printer = System.out::println;
printer.accept("Hello!");   // prints: Hello!

// Log with prefix
Consumer<String> logger = msg -> System.out.println("[LOG] " + msg);
logger.accept("Started");   // prints: [LOG] Started

// Modify mutable object
Consumer<List<String>> addDefault = list -> list.add("N/A");
List<String> items = new ArrayList<>(List.of("a"));
addDefault.accept(items);   // items = [a, N/A]

// Used in Stream.forEach()
names.stream()
    .forEach(printer);      // Consumer passed to forEach()
// prints each name

// Used in Stream.peek() (for debugging)
names.stream()
    .peek(logger)           // Consumer passed to peek()
    .collect(Collectors.toList());
```

**Key methods:**
```java
// andThen — chain consumers sequentially
Consumer<String> printAndLog = printer.andThen(logger);
printAndLog.accept("Test");
// prints: Test
// prints: [LOG] Test
```

---

### 4. `Supplier<T>`
Takes **no input**, returns a result. The **factory/generator** — it produces values out of thin air.

**Analogy: Vending Machine** — You don't put anything in (no input). Press a button, and a snack (output) comes out. It **supplies** something on demand.

```java
// SAM: T get()

// Return constant value
Supplier<String> greeting = () -> "Hello, World!";
greeting.get();   // "Hello, World!"

// Generate random number
Supplier<Double> randomNum = Math::random;
randomNum.get();  // 0.7234... (different each time)

// Create new object (factory pattern)
Supplier<List<String>> listFactory = ArrayList::new;
List<String> newList = listFactory.get();   // fresh empty ArrayList

// Lazy initialization
Supplier<Connection> dbConnection = () -> DriverManager.getConnection("jdbc:...");
// Connection is NOT created until .get() is called

// Current timestamp
Supplier<LocalDateTime> now = LocalDateTime::now;
now.get();   // 2026-03-09T14:30:00

// Used in Stream.generate()
Stream.generate(randomNum)
    .limit(3)
    .forEach(System.out::println);
// 0.123, 0.456, 0.789

// Used in Optional.orElseGet()
Optional<String> opt = Optional.empty();
String val = opt.orElseGet(greeting);   // "Hello, World!"
// Supplier is lazy — greeting.get() only called if Optional is empty
```

---

### 5. `BiFunction<T, U, R>`
Takes **two inputs** (T and U), returns a result (R). Like Function but with two parameters.

**Analogy: Blender** — You put in fruit (input 1) and milk (input 2), you get a smoothie (output). Two different ingredients combine into one result.

```java
// SAM: R apply(T t, U u)

// Add two numbers
BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
add.apply(3, 4);   // 7

// Format full name
BiFunction<String, String, String> fullName = (first, last) -> first + " " + last;
fullName.apply("John", "Doe");   // "John Doe"

// Map key-value to string
BiFunction<String, Double, String> formatSalary =
    (name, sal) -> name + " earns $" + sal;
formatSalary.apply("Alice", 95000.0);   // "Alice earns $95000.0"

// Calculate area
BiFunction<Double, Double, Double> area = (length, width) -> length * width;
area.apply(5.0, 3.0);   // 15.0

// Used in Map.merge()
Map<String, Integer> scores = new HashMap<>(Map.of("Alice", 80));
scores.merge("Alice", 10, Integer::sum);   // BiFunction merges old + new
// {Alice=90}

// Used in Map.replaceAll()
Map<String, String> map = new HashMap<>(Map.of("key", "value"));
map.replaceAll((k, v) -> v.toUpperCase()); // BiFunction transforms each entry
// {key=VALUE}
```

---

### 6. `BiPredicate<T, U>`
Takes **two inputs**, returns `boolean`. Like Predicate but tests two values together.

**Analogy: Bouncer at a Club** — Checks your ID (input 1) AND dress code (input 2). Both must pass the test for entry.

```java
// SAM: boolean test(T t, U u)

// Check if string has given length
BiPredicate<String, Integer> hasLength = (s, len) -> s.length() == len;
hasLength.test("Hello", 5);    // true
hasLength.test("Hi", 5);       // false

// Check if employee belongs to department
BiPredicate<Employee, String> isInDept =
    (emp, dept) -> emp.department().equals(dept);

// Check if number is in range
BiPredicate<Integer, Integer> isGreaterThan = (a, b) -> a > b;
isGreaterThan.test(10, 5);    // true

// Check if string contains substring
BiPredicate<String, String> contains = String::contains;
contains.test("Hello World", "World");   // true

// Chaining with and/or/negate
BiPredicate<String, Integer> hasLengthAndNotEmpty =
    hasLength.and((s, len) -> !s.isEmpty());
```

---

### 7. `BiConsumer<T, U>`
Takes **two inputs**, returns **nothing**. Performs a side effect using two arguments.

**Analogy: Printer with Paper Tray** — You give it a document (input 1) and paper size (input 2). It prints. Nothing is returned — the printing IS the action.

```java
// SAM: void accept(T t, U u)

// Print key-value pair
BiConsumer<String, Integer> printEntry = (k, v) ->
    System.out.println(k + " = " + v);
printEntry.accept("age", 25);   // prints: age = 25

// Add entry to map
BiConsumer<Map<String, Integer>, String> addToMap = (map, key) ->
    map.put(key, key.length());

// Used in Map.forEach()
Map<String, Integer> scores = Map.of("Alice", 90, "Bob", 85);
scores.forEach((name, score) ->
    System.out.println(name + " scored " + score));
// prints: Alice scored 90
// prints: Bob scored 85

// Log with level
BiConsumer<String, String> log = (level, msg) ->
    System.out.println("[" + level + "] " + msg);
log.accept("ERROR", "Connection failed");
// prints: [ERROR] Connection failed
```

---

### 8. `UnaryOperator<T>`
Takes one input, returns **same type**. It's a special case of `Function<T, T>`.

**Analogy: Photo Filter** — You put in a photo (input), you get back a photo (same type) — just modified. The type never changes, only the value.

```java
// SAM: T apply(T t) — inherited from Function<T,T>

// Increment
UnaryOperator<Integer> increment = n -> n + 1;
increment.apply(5);   // 6

// Uppercase
UnaryOperator<String> shout = String::toUpperCase;
shout.apply("hello");   // "HELLO"

// Negate
UnaryOperator<Integer> negate = n -> -n;
negate.apply(42);   // -42

// Trim and lowercase (chained)
UnaryOperator<String> normalize = s -> s.trim().toLowerCase();
normalize.apply("  HELLO  ");   // "hello"

// Used in List.replaceAll()
List<String> items = new ArrayList<>(List.of("Alice", "Bob"));
items.replaceAll(String::toUpperCase);   // UnaryOperator transforms in-place
// [ALICE, BOB]

// Used in Stream.iterate()
Stream.iterate(1, increment)
    .limit(5)
    .forEach(System.out::println);
// 1, 2, 3, 4, 5

// identity — returns input unchanged
UnaryOperator<String> noChange = UnaryOperator.identity();
noChange.apply("hello");   // "hello"
```

---

### 9. `BinaryOperator<T>`
Takes **two inputs of same type**, returns **same type**. Special case of `BiFunction<T, T, T>`.

**Analogy: Arm Wrestling** — Two players (same type: Person) compete. One winner (same type: Person) comes out. Input types and output type are always the same.

```java
// SAM: T apply(T t1, T t2) — inherited from BiFunction<T,T,T>

// Add two integers
BinaryOperator<Integer> sum = Integer::sum;
sum.apply(3, 7);   // 10

// Find max
BinaryOperator<Integer> max = Integer::max;
max.apply(3, 7);   // 7

// Concatenate strings
BinaryOperator<String> concat = String::concat;
concat.apply("Hello, ", "World!");   // "Hello, World!"

// Used in Stream.reduce()
int total = numbers.stream()
    .reduce(0, Integer::sum);   // BinaryOperator as accumulator
// 55

Optional<Integer> biggest = numbers.stream()
    .reduce(Integer::max);      // BinaryOperator finds max
// Optional[10]

// Used in toMap merge function
Map<String, Integer> map = names.stream()
    .collect(Collectors.toMap(
        n -> n,
        String::length,
        Integer::sum));   // BinaryOperator merges duplicate keys
// {Alice=5, Bob=3, Charlie=7, Diana=5}

// minBy / maxBy — static factory methods
BinaryOperator<String> shortest = BinaryOperator.minBy(Comparator.comparingInt(String::length));
shortest.apply("Hi", "Hello");   // "Hi"

BinaryOperator<String> longest = BinaryOperator.maxBy(Comparator.comparingInt(String::length));
longest.apply("Hi", "Hello");    // "Hello"
```

---

## All 4 Core Interfaces at a Glance

```
 ┌──────────────────────────────────────────────────────────────────────┐
 │                  INPUT(S)    →    OUTPUT                            │
 ├──────────────────────────────────────────────────────────────────────┤
 │                                                                     │
 │  Supplier<T>        ()       →    T          Factory / Generator    │
 │  Consumer<T>        (T)      →    void       Side effect / Sink     │
 │  Predicate<T>       (T)      →    boolean    Test / Filter          │
 │  Function<T,R>      (T)      →    R          Transform / Convert   │
 │                                                                     │
 │  BiConsumer<T,U>    (T, U)   →    void       Two-arg side effect   │
 │  BiPredicate<T,U>   (T, U)   →    boolean    Two-arg test          │
 │  BiFunction<T,U,R>  (T, U)   →    R          Two-arg transform     │
 │                                                                     │
 │  UnaryOperator<T>   (T)      →    T          Same-type transform   │
 │  BinaryOperator<T>  (T, T)   →    T          Two same-type combine │
 │                                                                     │
 ├──────────────────────────────────────────────────────────────────────┤
 │  MEMORY AID:                                                        │
 │                                                                     │
 │  "Supplier GIVES, Consumer TAKES, Predicate CHECKS,                │
 │   Function CONVERTS, Operator KEEPS THE TYPE"                       │
 └──────────────────────────────────────────────────────────────────────┘
```

---

## Primitive Specializations

Java provides primitive versions to avoid autoboxing overhead. The naming convention:

```
 Pattern:  [Input]To[Output]Function    or   [Type]Predicate / [Type]Consumer
```

### IntFunction, LongFunction, DoubleFunction
Specialized input type, generic output.
```java
IntFunction<String> intToStr = n -> "Number: " + n;
intToStr.apply(42);   // "Number: 42"

IntFunction<int[]> arrayCreator = int[]::new;
int[] arr = arrayCreator.apply(5);   // new int[5]
```

### ToIntFunction, ToLongFunction, ToDoubleFunction
Generic input, specialized output.
```java
ToIntFunction<String> strLen = String::length;
strLen.applyAsInt("Hello");   // 5 (returns primitive int, no boxing)

ToDoubleFunction<Employee> salary = Employee::salary;
salary.applyAsDouble(emp);    // 95000.0 (primitive double)
```

### IntToDoubleFunction, IntToLongFunction, etc.
Both input and output are primitive.
```java
IntToDoubleFunction half = n -> n / 2.0;
half.applyAsDouble(7);   // 3.5
```

### IntPredicate, LongPredicate, DoublePredicate
Predicate for primitives.
```java
IntPredicate isPositive = n -> n > 0;
isPositive.test(5);    // true (no autoboxing)
isPositive.test(-3);   // false
```

### IntConsumer, LongConsumer, DoubleConsumer
Consumer for primitives.
```java
IntConsumer printSquare = n -> System.out.println(n * n);
printSquare.accept(4);   // prints: 16
```

### IntSupplier, LongSupplier, DoubleSupplier, BooleanSupplier
Supplier for primitives.
```java
IntSupplier dice = () -> (int) (Math.random() * 6) + 1;
dice.getAsInt();   // random 1-6

BooleanSupplier coinFlip = () -> Math.random() > 0.5;
coinFlip.getAsBoolean();   // true or false
```

### IntUnaryOperator, LongUnaryOperator, DoubleUnaryOperator
UnaryOperator for primitives.
```java
IntUnaryOperator doubleIt = n -> n * 2;
doubleIt.applyAsInt(5);   // 10

// Chaining
IntUnaryOperator doubleThenAdd10 = doubleIt.andThen(n -> n + 10);
doubleThenAdd10.applyAsInt(5);   // 20
```

### IntBinaryOperator, LongBinaryOperator, DoubleBinaryOperator
BinaryOperator for primitives.
```java
IntBinaryOperator multiply = (a, b) -> a * b;
multiply.applyAsInt(3, 4);   // 12
```

### Complete Primitive Specialization Table

| Base Interface | Int | Long | Double |
|---|---|---|---|
| `Predicate<T>` | `IntPredicate` | `LongPredicate` | `DoublePredicate` |
| `Consumer<T>` | `IntConsumer` | `LongConsumer` | `DoubleConsumer` |
| `Supplier<T>` | `IntSupplier` | `LongSupplier` | `DoubleSupplier` |
| `Function<T,R>` | `IntFunction<R>` | `LongFunction<R>` | `DoubleFunction<R>` |
| `To_Function<T>` | `ToIntFunction<T>` | `ToLongFunction<T>` | `ToDoubleFunction<T>` |
| `UnaryOperator<T>` | `IntUnaryOperator` | `LongUnaryOperator` | `DoubleUnaryOperator` |
| `BinaryOperator<T>` | `IntBinaryOperator` | `LongBinaryOperator` | `DoubleBinaryOperator` |
| — | `BooleanSupplier` | — | — |
| `ObjConsumer` | `ObjIntConsumer<T>` | `ObjLongConsumer<T>` | `ObjDoubleConsumer<T>` |

---

## Older Functional Interfaces (Pre-Java 8)

These existed before Java 8 and are retroactively functional interfaces:

```java
// Runnable — no input, no output (void → void)
Runnable task = () -> System.out.println("Running in thread");
new Thread(task).start();

// Callable<V> — no input, returns V (can throw Exception)
Callable<String> fetchData = () -> {
    Thread.sleep(1000);
    return "Data loaded";
};
Future<String> future = executor.submit(fetchData);

// Comparator<T> — takes two T, returns int
Comparator<String> byLength = (a, b) -> a.length() - b.length();
Comparator<String> byLengthClean = Comparator.comparingInt(String::length);
names.sort(byLengthClean);

// ActionListener (Swing) — takes ActionEvent, returns void
button.addActionListener(e -> System.out.println("Clicked!"));
```

---

## Creating Custom Functional Interfaces

```java
// Custom functional interface with generics
@FunctionalInterface
interface Converter<F, T> {
    T convert(F from);
}

Converter<String, Integer> toInt = Integer::parseInt;
toInt.convert("123");   // 123

Converter<String, LocalDate> toDate = LocalDate::parse;
toDate.convert("2026-03-09");   // 2026-03-09


// Custom with multiple parameters
@FunctionalInterface
interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);
}

TriFunction<String, String, Integer, String> buildProfile =
    (name, city, age) -> name + " from " + city + ", age " + age;
buildProfile.apply("Alice", "NYC", 30);   // "Alice from NYC, age 30"


// Custom with exception handling
@FunctionalInterface
interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
}

ThrowingFunction<String, Integer> riskyParse = Integer::parseInt;
// Can be wrapped for use in streams:
public static <T, R> Function<T, R> wrap(ThrowingFunction<T, R> fn) {
    return t -> {
        try { return fn.apply(t); }
        catch (Exception e) { throw new RuntimeException(e); }
    };
}
```

---

## Method References as Functional Interfaces

Method references are shorthand for lambdas where the lambda just calls an existing method:

```
 ┌──────────────────────┬──────────────────────────┬─────────────────────────┐
 │ Type                 │ Method Reference         │ Equivalent Lambda       │
 ├──────────────────────┼──────────────────────────┼─────────────────────────┤
 │ Static method        │ Integer::parseInt        │ s -> Integer.parseInt(s)│
 │ Instance (bound)     │ System.out::println      │ s -> System.out.println(s)│
 │ Instance (unbound)   │ String::toUpperCase      │ s -> s.toUpperCase()    │
 │ Constructor          │ ArrayList::new           │ () -> new ArrayList<>() │
 └──────────────────────┴──────────────────────────┴─────────────────────────┘
```

```java
// Static method reference → Function
Function<String, Integer> parse = Integer::parseInt;      // static method

// Bound instance method → Consumer
Consumer<String> print = System.out::println;             // specific object's method

// Unbound instance method → Function
Function<String, String> upper = String::toUpperCase;     // any String's method

// Constructor reference → Supplier or Function
Supplier<List<String>> newList = ArrayList::new;           // no-arg constructor
Function<Integer, int[]> newArray = int[]::new;            // array constructor
```

---

## Composition and Chaining

### Function Chaining

```java
Function<String, String> trim    = String::trim;
Function<String, String> lower   = String::toLowerCase;
Function<String, Integer> length = String::length;

// andThen: execute left-to-right → trim → lower → length
Function<String, Integer> pipeline = trim.andThen(lower).andThen(length);
pipeline.apply("  HELLO  ");   // 5

// compose: execute right-to-left → lower first, then trim
Function<String, String> composed = trim.compose(lower);
composed.apply("  HELLO  ");   // "hello" (trimmed after lowercasing)
```

```
 andThen: A → B → C → D  (left to right, natural reading order)
 compose: D ← C ← B ← A  (right to left, mathematical f(g(x)))
```

### Predicate Chaining

```java
Predicate<String> notNull   = Objects::nonNull;
Predicate<String> notEmpty  = s -> !s.isEmpty();
Predicate<String> startsA   = s -> s.startsWith("A");

// Combine with and / or / negate
Predicate<String> valid = notNull.and(notEmpty).and(startsA);
valid.test("Alice");   // true
valid.test("");        // false
valid.test("Bob");     // false

Predicate<String> invalid = valid.negate();
invalid.test("Bob");   // true
```

### Consumer Chaining

```java
Consumer<String> log   = s -> System.out.println("[LOG] " + s);
Consumer<String> audit = s -> System.out.println("[AUDIT] " + s);
Consumer<String> save  = s -> System.out.println("[SAVE] " + s);

// Execute all three in sequence
Consumer<String> fullPipeline = log.andThen(audit).andThen(save);
fullPipeline.accept("UserLogin");
// [LOG] UserLogin
// [AUDIT] UserLogin
// [SAVE] UserLogin
```

---

## Real-World Usage in Stream API

Which functional interface is used where in the Stream API:

```
 ┌──────────────────────────────────────────────────────────────────────┐
 │              Stream<T> API  →  Functional Interface Used            │
 ├──────────────────────────────────────────────────────────────────────┤
 │                                                                     │
 │  .filter(...)        →  Predicate<T>                                │
 │  .map(...)           →  Function<T, R>                              │
 │  .mapToInt(...)      →  ToIntFunction<T>                            │
 │  .flatMap(...)       →  Function<T, Stream<R>>                      │
 │  .forEach(...)       →  Consumer<T>                                 │
 │  .peek(...)          →  Consumer<T>                                 │
 │  .sorted(...)        →  Comparator<T>                               │
 │  .reduce(identity, acc)           →  BinaryOperator<T>              │
 │  .reduce(identity, acc, combiner) →  BiFunction + BinaryOperator    │
 │  .anyMatch(...)      →  Predicate<T>                                │
 │  .allMatch(...)      →  Predicate<T>                                │
 │  .noneMatch(...)     →  Predicate<T>                                │
 │  .collect(supplier, acc, combiner)                                  │
 │       supplier       →  Supplier<R>                                 │
 │       accumulator    →  BiConsumer<R, T>                            │
 │       combiner       →  BiConsumer<R, R>                            │
 │                                                                     │
 │  Stream.generate(..) →  Supplier<T>                                 │
 │  Stream.iterate(..)  →  UnaryOperator<T>                            │
 │                                                                     │
 │  Optional:                                                          │
 │  .orElseGet(...)     →  Supplier<T>                                 │
 │  .ifPresent(...)     →  Consumer<T>                                 │
 │  .map(...)           →  Function<T, R>                              │
 │  .filter(...)        →  Predicate<T>                                │
 │                                                                     │
 │  Map:                                                               │
 │  .forEach(...)       →  BiConsumer<K, V>                            │
 │  .merge(k, v, ...)   →  BiFunction<V, V, V>                        │
 │  .computeIfAbsent(..)→  Function<K, V>                              │
 │  .replaceAll(...)    →  BiFunction<K, V, V>                         │
 └──────────────────────────────────────────────────────────────────────┘
```

---

## Interview Questions & Answers

### Q1: What is a functional interface? Can it have multiple methods?
**A:** A functional interface has **exactly one abstract method** (SAM). It CAN have multiple `default` methods and `static` methods — those don't count. The `@FunctionalInterface` annotation makes the compiler enforce the single-abstract-method rule.

```java
@FunctionalInterface
interface MyInterface {
    void doWork();                          // ✅ THE one abstract method
    default void helper() { }              // ✅ allowed (default)
    static void utility() { }             // ✅ allowed (static)
    boolean equals(Object o);              // ✅ allowed (from Object)
    // void doMore();                      // ❌ COMPILE ERROR — 2nd abstract method
}
```

---

### Q2: Is `@FunctionalInterface` mandatory?
**A:** No. Any interface with exactly one abstract method IS a functional interface, with or without the annotation. The annotation is optional but recommended because:
- It makes your **intent clear** to other developers
- The **compiler enforces** the one-abstract-method rule — if someone adds a second abstract method, compilation fails

```java
// This is a valid functional interface even without the annotation
interface Printer {
    void print(String msg);
}
Printer p = msg -> System.out.println(msg);   // ✅ works fine
```

---

### Q3: What is the difference between `Function` and `UnaryOperator`?

**A:** `UnaryOperator<T>` extends `Function<T, T>`. The only difference is UnaryOperator guarantees **input and output are the same type**.

```java
Function<String, String>   fn = s -> s.toUpperCase();   // input: String, output: String
UnaryOperator<String>      op = s -> s.toUpperCase();   // same thing, more specific type

Function<String, Integer>  fn2 = String::length;        // ✅ different types OK
// UnaryOperator<String>   op2 = String::length;        // ❌ COMPILE ERROR — output must be String
```

| | Function<T,R> | UnaryOperator<T> |
|---|---|---|
| Input type | T | T |
| Output type | R (any) | T (same as input) |
| Use when | Types differ | Types are same |

---

### Q4: What's the difference between `Predicate` and `Function<T, Boolean>`?

**A:** Functionally similar, but `Predicate` is semantically clearer, avoids autoboxing, and provides `and()`, `or()`, `negate()` chaining methods that `Function` doesn't have.

```java
Predicate<Integer> p = n -> n > 5;       // returns primitive boolean, has and/or/negate
Function<Integer, Boolean> f = n -> n > 5; // returns boxed Boolean, no chaining methods

// Predicate can chain:
Predicate<Integer> combined = p.and(n -> n < 100).or(n -> n == 0);

// Function cannot chain with and/or:
// f.and(...) → COMPILE ERROR
```

---

### Q5: Can a lambda throw a checked exception?

**A:** Built-in functional interfaces do **NOT** declare checked exceptions. If your lambda throws a checked exception, you must either:
1. Catch it inside the lambda
2. Create a custom functional interface that declares the exception

```java
// ❌ WON'T COMPILE — Function.apply() doesn't throw Exception
Function<String, Integer> bad = s -> Integer.parseInt(s); // NumberFormatException is unchecked — OK
// Function<String, Object> bad = s -> Class.forName(s);  // ClassNotFoundException is checked — ERROR

// ✅ Option 1: Catch inside lambda
Function<String, Object> safe = s -> {
    try { return Class.forName(s); }
    catch (ClassNotFoundException e) { throw new RuntimeException(e); }
};

// ✅ Option 2: Custom functional interface
@FunctionalInterface
interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;
}
ThrowingFunction<String, Object> risky = Class::forName;   // ✅ compiles
```

---

### Q6: What's the difference between `Supplier` and `Callable`?

**A:** Both take no arguments and return a value. Key differences:

| | `Supplier<T>` | `Callable<V>` |
|---|---|---|
| Package | `java.util.function` | `java.util.concurrent` |
| Method | `T get()` | `V call() throws Exception` |
| Checked exception | ❌ No | ✅ Yes |
| Use case | Lazy values, factories, streams | Thread pools, async tasks |

```java
Supplier<String> supplier = () -> "Hello";       // cannot throw checked exception
Callable<String> callable = () -> {              // CAN throw checked exception
    if (error) throw new IOException("fail");
    return "Hello";
};
```

---

### Q7: Why do primitive specializations exist? (IntFunction, ToIntFunction, etc.)

**A:** To **avoid autoboxing/unboxing overhead**. Generic types like `Function<Integer, Integer>` use boxed `Integer` objects. Primitive specializations work directly with `int`, `long`, `double` — no object creation.

```java
// BAD: autoboxes int → Integer for every element
Function<Integer, Integer> boxed = n -> n * 2;
// Each call: int → Integer (box) → Integer (box) → int (unbox)

// GOOD: no boxing at all
IntUnaryOperator primitive = n -> n * 2;
// Each call: int → int (no objects created)

// In streams this matters for millions of elements:
numbers.stream()
    .mapToInt(n -> n)        // convert to IntStream (primitive)
    .filter(n -> n > 5)      // IntPredicate — no boxing
    .sum();                   // direct primitive sum
```

---

### Q8: Explain `andThen` vs `compose` in Function.

**A:** Both chain functions. The difference is **execution order**:

```
 andThen:  this.apply(x)  → then.apply(result)     LEFT to RIGHT
 compose:  before.apply(x) → this.apply(result)     RIGHT to LEFT
```

```java
Function<Integer, Integer> doubleIt = n -> n * 2;
Function<Integer, Integer> addTen  = n -> n + 10;

// andThen: double FIRST, then add 10
doubleIt.andThen(addTen).apply(5);     // (5 * 2) + 10 = 20

// compose: add 10 FIRST, then double
doubleIt.compose(addTen).apply(5);     // (5 + 10) * 2 = 30
```

---

### Q9: How is `Consumer.andThen` different from `Function.andThen`?

**A:** `Consumer.andThen` chains two void operations sequentially — the same input is consumed by both. `Function.andThen` passes the **output** of the first function as **input** to the second.

```java
// Function.andThen: OUTPUT of first → INPUT of second
Function<String, String> upper = String::toUpperCase;
Function<String, Integer> length = String::length;
upper.andThen(length).apply("hello");   // "hello" → "HELLO" → 5

// Consumer.andThen: SAME INPUT goes to both
Consumer<String> print = System.out::println;
Consumer<String> log = s -> System.out.println("[LOG] " + s);
print.andThen(log).accept("hello");
// prints: hello          ← print received "hello"
// prints: [LOG] hello    ← log ALSO received "hello"
```

---

### Q10: Can a functional interface extend another interface?

**A:** Yes, as long as the total abstract method count remains ONE. If the parent has an abstract method, the child cannot add another one.

```java
@FunctionalInterface
interface A {
    void doA();
}

// ✅ Valid — inherits doA(), adds no new abstract methods
@FunctionalInterface
interface B extends A {
    default void doB() { }  // default method — doesn't count
}

// ❌ Invalid — two abstract methods (doA + doC)
// @FunctionalInterface
// interface C extends A {
//     void doC();  // COMPILE ERROR
// }

// ✅ Valid — overrides parent's abstract method (still one SAM)
@FunctionalInterface
interface D extends A {
    @Override
    void doA();  // same method — count stays at 1
}
```

---

### Q11: What is `Function.identity()` and when do you use it?

**A:** `Function.identity()` returns a function that always returns its input argument unchanged. It's equivalent to `t -> t`.

```java
Function<String, String> id = Function.identity();
id.apply("Hello");   // "Hello" — unchanged

// Common use 1: toMap where key IS the element itself
Map<String, Integer> nameLengths = names.stream()
    .collect(Collectors.toMap(Function.identity(), String::length));
// {Alice=5, Bob=3, Charlie=7, Diana=5}

// Common use 2: flatMap passthrough
Stream<List<String>> nested = Stream.of(List.of("a"), List.of("b"));
// ... identity can be used as no-op in composition chains

// Common use 3: Collector finisher when no transformation needed
// Collectors.toList() internally uses Function.identity() as its finisher
```

---

### Q12: Write a custom functional interface that takes 3 arguments.

**A:** Java only provides up to `BiFunction` (2 args). For 3+, create your own:

```java
@FunctionalInterface
interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);

    // Optional: add andThen for chaining
    default <V> TriFunction<A, B, C, V> andThen(Function<? super R, ? extends V> after) {
        return (a, b, c) -> after.apply(apply(a, b, c));
    }
}

// Usage
TriFunction<String, String, Integer, String> buildEmail =
    (name, domain, id) -> name + "." + id + "@" + domain;

buildEmail.apply("alice", "company.com", 42);
// "alice.42@company.com"

// With andThen
TriFunction<String, String, Integer, String> upperEmail =
    buildEmail.andThen(String::toUpperCase);
upperEmail.apply("alice", "company.com", 42);
// "ALICE.42@COMPANY.COM"
```

---

### Q13: What's the difference between `Comparator` and `BiFunction<T,T,Integer>`?

**A:** Both take two arguments and return a value. But `Comparator` is specialized for **ordering** and provides rich chaining methods:

```java
// BiFunction — bare bones, no helpers
BiFunction<String, String, Integer> bf = (a, b) -> a.length() - b.length();

// Comparator — rich API with chaining, nulls handling, reversing
Comparator<String> comp = Comparator
    .comparingInt(String::length)          // primary sort
    .thenComparing(Comparator.naturalOrder()) // secondary sort
    .reversed();                           // reverse entire order

// Comparator also provides:
// nullsFirst(), nullsLast(), thenComparing(), reversed()
// BiFunction has NONE of these.
```

---

### Q14: How do you handle null-safe functional interfaces?

```java
// Wrap with null checks
Function<String, String> nullSafeUpper = s -> s == null ? null : s.toUpperCase();

// Use Optional
Function<String, String> safeUpper = s -> Optional.ofNullable(s)
    .map(String::toUpperCase)
    .orElse("N/A");

safeUpper.apply(null);     // "N/A"
safeUpper.apply("hello");  // "HELLO"

// Null-safe Predicate
Predicate<String> notNull = Objects::nonNull;
Predicate<String> safeNotEmpty = notNull.and(s -> !s.isEmpty());
safeNotEmpty.test(null);   // false (doesn't throw NPE)
safeNotEmpty.test("");     // false
safeNotEmpty.test("hi");   // true
```

---

### Q15: Why can't lambdas modify local variables? (Effectively Final Rule)

**A:** Lambdas can only access local variables that are **effectively final** (assigned once and never changed). This is because lambdas may execute in a different thread, and Java doesn't support shared mutable state across threads for local variables.

```java
// ❌ WON'T COMPILE — counter is modified
int counter = 0;
names.forEach(n -> counter++);   // ERROR: "variable must be effectively final"

// ✅ Workaround 1: Use AtomicInteger
AtomicInteger counter = new AtomicInteger(0);
names.forEach(n -> counter.incrementAndGet());   // ✅ AtomicInteger is effectively final (reference doesn't change)

// ✅ Workaround 2: Use array
int[] counter = {0};
names.forEach(n -> counter[0]++);   // ✅ array reference is effectively final

// ✅ Best approach: Use stream operations instead
long count = names.stream().count();   // no mutable state needed
```

---

### Quick Reference: When to Use Which Interface

| Scenario | Interface | Example |
|---|---|---|
| Transform/convert a value | `Function<T,R>` | `s -> s.length()` |
| Test a condition | `Predicate<T>` | `n -> n > 0` |
| Consume/use a value (no return) | `Consumer<T>` | `System.out::println` |
| Generate/create a value (no input) | `Supplier<T>` | `ArrayList::new` |
| Combine two values into one | `BiFunction<T,U,R>` | `(a, b) -> a + b` |
| Same type in → same type out | `UnaryOperator<T>` | `String::toUpperCase` |
| Two same types → one same type | `BinaryOperator<T>` | `Integer::sum` |
| Sort/compare two elements | `Comparator<T>` | `Comparator.comparingInt(...)` |
| Run in a thread (void→void) | `Runnable` | `() -> doWork()` |
| Async task with return + exception | `Callable<V>` | `() -> fetchData()` |
