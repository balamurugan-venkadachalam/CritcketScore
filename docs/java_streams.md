# Java Streams & Collectors — Complete Reference

## Table of Contents
1. [Stream Creation](#stream-creation)
2. [Intermediate Operations](#intermediate-operations)
3. [Terminal Operations](#terminal-operations)
4. [How collect() and Collector Work Internally](#how-collect-and-collector-work-internally)
5. [Key Collector Terminology](#key-collector-terminology--downstream-finisher-merger)
6. [Supplier, Accumulator, Combiner, Finisher — Deep Dive](#supplier-accumulator-combiner-finisher--deep-dive)
7. [Collectors — Complete Reference](#collectors--complete-reference)
8. [Java Generics — Deep Dive](#java-generics--deep-dive)
9. [Generics Interview Questions](#generics-interview-questions)

---

## Common Setup Used in Examples

```java
import java.util.*;
import java.util.stream.*;
import java.util.concurrent.*;

record Employee(String name, String department, double salary, List<String> skills) {}

List<Employee> employees = List.of(
    new Employee("Alice",   "Engineering", 95000, List.of("Java", "Spring")),
    new Employee("Bob",     "Engineering", 72000, List.of("Python", "Java")),
    new Employee("Charlie", "HR",          60000, List.of("Recruiting", "Training")),
    new Employee("Diana",   "HR",          55000, List.of("Recruiting")),
    new Employee("Eve",     "Sales",       80000, List.of("CRM", "Analytics"))
);

List<String> names = List.of("Alice", "Bob", "Charlie", "Alice", "Diana");
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
```

---

## Stream Creation

```java
// From values
Stream<String> s1 = Stream.of("a", "b", "c");

// From array
int[] arr = {1, 2, 3};
IntStream s2 = Arrays.stream(arr);

// From Collection
Stream<String> s3 = names.stream();

// Infinite stream with limit
Stream<Integer> s4 = Stream.iterate(0, n -> n + 2).limit(5);  // 0, 2, 4, 6, 8

// Supplier-based
Stream<Double> s5 = Stream.generate(Math::random).limit(3);

// Int ranges
IntStream range = IntStream.range(1, 5);        // [1, 2, 3, 4]
IntStream closed = IntStream.rangeClosed(1, 5);  // [1, 2, 3, 4, 5]
```

---

## Intermediate Operations

```java
// filter — keeps elements matching the predicate
List<Integer> evens = numbers.stream()
    .filter(n -> n % 2 == 0)
    .collect(Collectors.toList());
// [2, 4, 6, 8, 10]

// map — transforms each element
List<String> upper = names.stream()
    .map(String::toUpperCase)
    .collect(Collectors.toList());
// [ALICE, BOB, CHARLIE, ALICE, DIANA]

// flatMap — flattens nested collections into a single stream
List<String> allSkills = employees.stream()
    .flatMap(e -> e.skills().stream())
    .collect(Collectors.toList());
// [Java, Spring, Python, Java, Recruiting, Training, Recruiting, CRM, Analytics]

// distinct — removes duplicates using equals()
List<String> unique = names.stream()
    .distinct()
    .collect(Collectors.toList());
// [Alice, Bob, Charlie, Diana]

// sorted — sorts naturally or with a Comparator
List<String> sorted = names.stream()
    .sorted(Comparator.reverseOrder())
    .collect(Collectors.toList());
// [Diana, Charlie, Bob, Alice, Alice]

// limit — takes only the first N elements
List<Integer> first3 = numbers.stream()
    .limit(3)
    .collect(Collectors.toList());
// [1, 2, 3]

// skip — discards the first N elements
List<Integer> after3 = numbers.stream()
    .skip(3)
    .collect(Collectors.toList());
// [4, 5, 6, 7, 8, 9, 10]

// peek — performs an action on each element without consuming (useful for debugging)
List<Integer> debugged = numbers.stream()
    .peek(n -> System.out.println("Processing: " + n))
    .filter(n -> n > 5)
    .collect(Collectors.toList());

// mapToInt / mapToLong / mapToDouble — converts to primitive stream for numeric ops
int totalLength = names.stream()
    .mapToInt(String::length)
    .sum();
// 28
```

---

## Terminal Operations

```java
// forEach — performs an action on each element, returns void
names.stream().forEach(System.out::println);

// collect — gathers elements into a container using a Collector
List<String> list = names.stream().collect(Collectors.toList());

// reduce — combines all elements into a single value using an accumulator
int sum = numbers.stream().reduce(0, Integer::sum);
// 55

// count — returns the total number of elements
long count = numbers.stream().filter(n -> n > 5).count();
// 5

// findFirst — returns the first element wrapped in Optional
Optional<String> first = names.stream().findFirst();
// Optional[Alice]

// findAny — returns any element (non-deterministic in parallel streams)
Optional<String> any = names.parallelStream().findAny();

// anyMatch — returns true if at least one element matches
boolean hasEve = names.stream().anyMatch(n -> n.equals("Eve"));
// false

// allMatch — returns true only if every element matches
boolean allShort = names.stream().allMatch(n -> n.length() < 10);
// true

// noneMatch — returns true only if no elements match
boolean noEmpty = names.stream().noneMatch(String::isEmpty);
// true

// min / max — returns min or max element by Comparator
Optional<Integer> max = numbers.stream().max(Comparator.naturalOrder());
// Optional[10]

// toArray — converts stream to an array
String[] nameArray = names.stream().toArray(String[]::new);
// [Alice, Bob, Charlie, Alice, Diana]
```

---

## How `collect()` and `Collector` Work Internally

### The Big Picture

`collect()` is a terminal operation that transforms a Stream into a concrete result (List, Map, String, etc.).
It delegates all the work to a **Collector** — a recipe object that tells the stream **HOW** to accumulate elements.

```
stream.collect(someCollector)
       │           │
       │           └── The recipe (HOW to collect)
       └── The trigger (START collecting)
```

### The Collector Interface — 4 Functions

Every Collector is built from exactly 4 functions. Think of it as a **factory assembly line**:

```java
public interface Collector<T, A, R> {
    Supplier<A>          supplier();       // 1. Create empty container
    BiConsumer<A, T>     accumulator();    // 2. Add one element to container
    BinaryOperator<A>   combiner();       // 3. Merge two containers (parallel)
    Function<A, R>      finisher();       // 4. Final transformation
}
// T = input element type,  A = accumulation type,  R = result type
```

### Real-World Analogy: Packing Apples into Boxes

Imagine a fruit-packing factory where apples (stream elements) arrive on a conveyor belt:

```
 APPLES ON CONVEYOR BELT (Stream elements)
 ══════════════════════════════════════════

 Step 1: SUPPLIER — "Get an empty box"
 ┌─────────────┐
 │             │  ← Factory worker grabs a fresh empty box
 │  (empty)    │     Supplier<A> → creates new ArrayList / HashMap / StringBuilder
 └─────────────┘

 Step 2: ACCUMULATOR — "Put each apple into the box, one by one"
 ┌─────────────┐
 │ 🍎          │  ← apple 1 arrives → worker puts it in box
 │ 🍎🍎        │  ← apple 2 arrives → worker puts it in box
 │ 🍎🍎🍎      │  ← apple 3 arrives → worker puts it in box
 └─────────────┘     BiConsumer<A,T> → list.add(element)

 Step 3: COMBINER — "Merge boxes from different lanes" (only in parallel)
 ┌────────┐  ┌────────┐        ┌──────────────────┐
 │ 🍎🍎   │ +│ 🍎🍎🍎 │  ───►  │ 🍎🍎🍎🍎🍎       │
 │ Lane 1 │  │ Lane 2 │        │ Merged Box       │
 └────────┘  └────────┘        └──────────────────┘
     Worker 1 and Worker 2 merge their boxes into one
     BinaryOperator<A> → list1.addAll(list2)

 Step 4: FINISHER — "Seal the box and label it"
 ┌──────────────────┐       ╔══════════════════╗
 │ 🍎🍎🍎🍎🍎       │  ───► ║  Final Product   ║
 │ Mutable box      │       ║  (sealed box)    ║
 └──────────────────┘       ╚══════════════════╝
     Convert the intermediate container to the final result
     Function<A,R> → Collections.unmodifiableList(list)
```

### Each Function Explained with Code

```java
// Collector.of(supplier, accumulator, combiner, finisher)

Collector<Integer, ?, List<Integer>> myToList = Collector.of(

    // 1. SUPPLIER: creates the empty mutable container
    //    Called ONCE at the beginning (once per thread in parallel)
    //    Analogy: "Give me a fresh empty box"
    () -> new ArrayList<>(),              // ArrayList::new

    // 2. ACCUMULATOR: adds ONE element into the container
    //    Called ONCE PER ELEMENT in the stream
    //    Analogy: "Put this apple into the box"
    (list, element) -> list.add(element), // ArrayList::add

    // 3. COMBINER: merges two containers into one
    //    Called ONLY in parallel streams to merge partial results
    //    Analogy: "Pour box B into box A"
    (list1, list2) -> {
        list1.addAll(list2);
        return list1;
    },

    // 4. FINISHER: transforms the container into the final result
    //    Called ONCE at the very end
    //    Analogy: "Seal the box and put a label on it"
    //    If container IS the result, use Function.identity()
    list -> Collections.unmodifiableList(list)
);
```

### Step-by-Step: `numbers.stream().collect(Collectors.toList())`

```
 Source: List<Integer> numbers = List.of(1, 2, 3, 4, 5);

 ╔═══════════════════════════════════════════════════════════════════════╗
 ║                    SEQUENTIAL STREAM FLOW                           ║
 ╠═══════════════════════════════════════════════════════════════════════╣
 ║                                                                     ║
 ║  numbers.stream()                                                   ║
 ║     │                                                               ║
 ║     ▼                                                               ║
 ║  .collect(Collectors.toList())                                      ║
 ║     │                                                               ║
 ║     │  ┌──────────────────────────────────────────────────────┐      ║
 ║     │  │ Step 1: SUPPLIER — supplier.get()                   │      ║
 ║     │  │                                                     │      ║
 ║     │  │   ArrayList<Integer> container = new ArrayList<>(); │      ║
 ║     │  │   container = []                                    │      ║
 ║     │  └──────────────────────────────────────────────────────┘      ║
 ║     │                                                               ║
 ║     │  ┌──────────────────────────────────────────────────────┐      ║
 ║     │  │ Step 2: ACCUMULATOR — called for EACH element       │      ║
 ║     │  │                                                     │      ║
 ║     │  │   accumulator.accept(container, 1) → [1]            │      ║
 ║     │  │   accumulator.accept(container, 2) → [1, 2]         │      ║
 ║     │  │   accumulator.accept(container, 3) → [1, 2, 3]      │      ║
 ║     │  │   accumulator.accept(container, 4) → [1, 2, 3, 4]   │      ║
 ║     │  │   accumulator.accept(container, 5) → [1, 2, 3, 4, 5]│      ║
 ║     │  └──────────────────────────────────────────────────────┘      ║
 ║     │                                                               ║
 ║     │  ┌──────────────────────────────────────────────────────┐      ║
 ║     │  │ Step 3: COMBINER — SKIPPED (sequential stream)      │      ║
 ║     │  │         Only used in .parallelStream()               │      ║
 ║     │  └──────────────────────────────────────────────────────┘      ║
 ║     │                                                               ║
 ║     │  ┌──────────────────────────────────────────────────────┐      ║
 ║     │  │ Step 4: FINISHER — finisher.apply(container)        │      ║
 ║     │  │                                                     │      ║
 ║     │  │   toList() uses IDENTITY finisher                   │      ║
 ║     │  │   → returns the ArrayList as-is                     │      ║
 ║     │  │   result = [1, 2, 3, 4, 5]                          │      ║
 ║     │  └──────────────────────────────────────────────────────┘      ║
 ║     │                                                               ║
 ║     ▼                                                               ║
 ║  List<Integer> result = [1, 2, 3, 4, 5]                            ║
 ║                                                                     ║
 ╚═══════════════════════════════════════════════════════════════════════╝
```

### Parallel Stream Flow (when combiner matters)

```
 Source: List.of(1, 2, 3, 4, 5, 6).parallelStream().collect(Collectors.toList())

 ╔═══════════════════════════════════════════════════════════════════════╗
 ║                     PARALLEL STREAM FLOW                            ║
 ╠═══════════════════════════════════════════════════════════════════════╣
 ║                                                                     ║
 ║  Stream is split across threads by the ForkJoinPool:                ║
 ║                                                                     ║
 ║  Thread 1                Thread 2               Thread 3            ║
 ║  ┌──────────┐            ┌──────────┐           ┌──────────┐        ║
 ║  │SUPPLIER  │            │SUPPLIER  │           │SUPPLIER  │        ║
 ║  │ []       │            │ []       │           │ []       │        ║
 ║  ├──────────┤            ├──────────┤           ├──────────┤        ║
 ║  │ACCUMULATE│            │ACCUMULATE│           │ACCUMULATE│        ║
 ║  │ +1 → [1] │            │ +3 → [3] │           │ +5 → [5] │        ║
 ║  │ +2 → [1,2]│           │ +4 → [3,4]│          │ +6 → [5,6]│       ║
 ║  └────┬─────┘            └────┬─────┘           └────┬─────┘        ║
 ║       │                       │                      │              ║
 ║       └──────────┬────────────┘                      │              ║
 ║                  │ COMBINER                          │              ║
 ║                  ▼                                   │              ║
 ║           ┌────────────┐                             │              ║
 ║           │ [1,2,3,4]  │                             │              ║
 ║           └─────┬──────┘                             │              ║
 ║                 │              COMBINER              │              ║
 ║                 └──────────────┬─────────────────────┘              ║
 ║                                ▼                                    ║
 ║                      ┌──────────────────┐                           ║
 ║                      │ [1, 2, 3, 4, 5, 6]│                          ║
 ║                      └────────┬─────────┘                           ║
 ║                               │ FINISHER                            ║
 ║                               ▼                                     ║
 ║                     List<Integer> result                            ║
 ║                     = [1, 2, 3, 4, 5, 6]                            ║
 ╚═══════════════════════════════════════════════════════════════════════╝
```

---

## Key Collector Terminology

### `downstream`
A **downstream collector** is a collector passed as an argument to another collector. It processes elements **after** the outer collector has done its work. Think of it as a **sub-task** or **inner pipeline**.

```
 groupingBy(Employee::department,  ←── outer collector: splits into groups
     counting()                    ←── DOWNSTREAM: what to do WITHIN each group
 )
```

```java
// Without downstream → default is toList()
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department));
// Each group → List<Employee>

// With downstream → counting() replaces toList()
Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department, Collectors.counting()));
// Each group → Long (count)

// Nesting: downstream of a downstream
Map<String, Optional<String>> topNameByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,          // outer
        Collectors.collectingAndThen(                             // downstream 1
            Collectors.maxBy(Comparator.comparingDouble(Employee::salary)), // downstream 2
            opt -> opt.map(Employee::name))));
```

**Which collectors accept a downstream?**
`groupingBy`, `partitioningBy`, `mapping`, `flatMapping`, `filtering`, `collectingAndThen`, `teeing`

### `finisher`
A **finisher** is the final transformation applied to the accumulated result. It converts the mutable intermediate container (type `A`) into the final result (type `R`).

```
 Accumulated data (A)  ──── finisher ────►  Final result (R)
 ArrayList<String>     ──── finisher ────►  UnmodifiableList<String>
 StringBuilder         ──── finisher ────►  String
```

```java
// collectingAndThen: the 2nd argument IS the finisher
List<String> immutable = names.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),                    // downstream collects into ArrayList
        Collections::unmodifiableList));        // FINISHER: wraps as unmodifiable
//      ─────────────────────────────
//      This is the finisher function

// In toList(), finisher is Function.identity() — returns ArrayList as-is
// In joining(), finisher is StringBuilder::toString — converts builder to String
```

### `merger` (in `teeing`)
A **merger** is the function in `teeing()` that combines the results of two independent collectors into one final result. It runs **after** both downstream collectors finish.

```
 Stream elements
   │
   ├──► downstream1 ──► result1 ──┐
   │                               ├──► MERGER(result1, result2) ──► final result
   └──► downstream2 ──► result2 ──┘
```

```java
// merger combines count and sum into a formatted String
String report = numbers.stream()
    .collect(Collectors.teeing(
        Collectors.counting(),                    // downstream1 → Long
        Collectors.summingInt(n -> n),            // downstream2 → Integer
        (count, sum) -> "Count=" + count + ", Sum=" + sum));  // MERGER
//      ─────────────────────────────────────────────────────
//      This is the merger — takes (Long, Integer) → returns String
// "Count=10, Sum=55"
```

---

## Supplier, Accumulator, Combiner, Finisher — Deep Dive

### Real-World Analogy: Restaurant Salad Bar

Imagine you are collecting salad ingredients from a salad bar conveyor belt:

| Collector Part | Salad Bar Analogy | Java Equivalent |
|---|---|---|
| **Supplier** | Grab a fresh empty plate | `() -> new ArrayList<>()` |
| **Accumulator** | Pick one item and put it on your plate | `(plate, item) -> plate.add(item)` |
| **Combiner** | Your friend has another plate — dump theirs onto yours | `(plate1, plate2) -> { plate1.addAll(plate2); return plate1; }` |
| **Finisher** | Cover plate with cling wrap for takeaway | `plate -> Collections.unmodifiableList(plate)` |

### Complete Examples for Each

#### Supplier — Creates the empty mutable container
Called once (or once per thread in parallel). Must return a **new mutable** container each time.

```java
// Different suppliers for different result types:
() -> new ArrayList<>()          // for toList
() -> new HashSet<>()            // for toSet
() -> new StringJoiner(", ")     // for joining
() -> new HashMap<>()            // for toMap
() -> new int[1]                 // for counting (mutable int holder)
() -> new long[]{0L, 0L}         // for averaging (sum + count)
```

#### Accumulator — Adds one element to the container
Called once per stream element. Receives the container + current element.

```java
// Different accumulators:
(list, elem) -> list.add(elem)                 // toList: add to list
(set, elem) -> set.add(elem)                   // toSet: add to set
(joiner, str) -> joiner.add(str)               // joining: append string
(map, emp) -> map.put(emp.name(), emp.salary()) // toMap: put key-value
(counter, elem) -> counter[0]++                // counting: increment
```

#### Combiner — Merges two containers (parallel streams only)
Called when parallel sub-tasks complete. Must merge container B into container A.

```java
// Different combiners:
(list1, list2) -> { list1.addAll(list2); return list1; }   // toList
(set1, set2) -> { set1.addAll(set2); return set1; }        // toSet
(j1, j2) -> j1.merge(j2)                                   // joining
(map1, map2) -> { map1.putAll(map2); return map1; }         // toMap
(c1, c2) -> { c1[0] += c2[0]; return c1; }                 // counting
```

#### Finisher — Final transformation
Called once at the end to convert intermediate container → final result.

```java
// Different finishers:
Function.identity()                // toList: ArrayList IS the result
Function.identity()                // toSet: HashSet IS the result
StringJoiner::toString             // joining: StringJoiner → String
Collections::unmodifiableList      // toUnmodifiableList: wrap as immutable
list -> list.isEmpty() ?           // custom: build final value
    "empty" : String.join(",", list)
```

### Putting It All Together — Complete Custom Collector Example

```java
// Goal: Collect employee names into a comma-separated uppercase string
// Input:  Stream<Employee>
// Output: "ALICE, BOB, CHARLIE, DIANA, EVE"

Collector<Employee, StringJoiner, String> nameJoiner = Collector.of(

    // SUPPLIER: create a StringJoiner with ", " delimiter
    () -> new StringJoiner(", "),

    // ACCUMULATOR: extract name, uppercase it, add to joiner
    (joiner, emp) -> joiner.add(emp.name().toUpperCase()),

    // COMBINER: merge two joiners (for parallel streams)
    (joiner1, joiner2) -> joiner1.merge(joiner2),

    // FINISHER: convert StringJoiner → String
    StringJoiner::toString
);

String result = employees.stream().collect(nameJoiner);
// "ALICE, BOB, CHARLIE, DIANA, EVE"
```

### How `Collectors.toList()` Is Implemented (Simplified)

```java
// This is what happens inside Collectors.toList():
public static <T> Collector<T, ?, List<T>> toList() {
    return Collector.of(
        ArrayList::new,          // supplier:     () -> new ArrayList<>()
        ArrayList::add,          // accumulator:  (list, elem) -> list.add(elem)
        (l1, l2) -> {            // combiner:     merge two lists
            l1.addAll(l2);
            return l1;
        },
        Function.identity()      // finisher:     return list as-is (no transformation)
    );
}
```

### How `Collectors.joining(delimiter)` Is Implemented (Simplified)

```java
// This is what happens inside Collectors.joining(", "):
public static Collector<CharSequence, ?, String> joining(CharSequence delimiter) {
    return Collector.of(
        () -> new StringJoiner(delimiter),   // supplier:     fresh joiner
        StringJoiner::add,                   // accumulator:  joiner.add(element)
        StringJoiner::merge,                 // combiner:     joiner1.merge(joiner2)
        StringJoiner::toString               // finisher:     joiner → "a, b, c"
    );
}
```

### Execution Sequence Diagram

```
 ┌─────────────────────────────────────────────────────────────────────┐
 │          numbers.stream().collect(Collectors.toList())              │
 └─────────────┬───────────────────────────────────────────────────────┘
               │
               ▼
 ┌─────────────────────────────┐
 │ Stream calls collector      │
 │ .supplier().get()           │
 │                             │     ┌──────────────────────────────┐
 │ "Create empty container"    │────►│ new ArrayList<>()            │
 └─────────────┬───────────────┘     │ container = []               │
               │                     └──────────────────────────────┘
               ▼
 ┌─────────────────────────────┐
 │ Stream iterates elements    │
 │ For EACH element, calls:    │
 │ accumulator.accept(         │
 │     container, element)     │
 ├─────────────────────────────┤     ┌──────────────────────────────┐
 │ accept([], 1)               │────►│ [1]                          │
 │ accept([1], 2)              │────►│ [1, 2]                       │
 │ accept([1,2], 3)            │────►│ [1, 2, 3]                    │
 │ accept([1,2,3], 4)          │────►│ [1, 2, 3, 4]                 │
 │ accept([1,2,3,4], 5)        │────►│ [1, 2, 3, 4, 5]              │
 └─────────────┬───────────────┘     └──────────────────────────────┘
               │
               ▼
 ┌─────────────────────────────┐
 │ Stream calls                │
 │ finisher.apply(container)   │
 │                             │     ┌──────────────────────────────┐
 │ toList() uses IDENTITY      │────►│ return [1, 2, 3, 4, 5] as-is│
 │ (no transformation needed)  │     └──────────────────────────────┘
 └─────────────┬───────────────┘
               │
               ▼
 ┌─────────────────────────────┐
 │ Result:                     │
 │ List<Integer> = [1,2,3,4,5] │
 └─────────────────────────────┘
```

### Summary Table

| Part | When Called | How Many Times | Purpose |
|---|---|---|---|
| **Supplier** | At the start | Once (once per thread in parallel) | Create empty mutable container |
| **Accumulator** | For each element | N times (N = element count) | Add one element to container |
| **Combiner** | After parallel sub-tasks | 0 times (sequential) / multiple (parallel) | Merge two partial containers |
| **Finisher** | At the very end | Once | Convert container → final result |

---

## Collectors — Complete Reference

### 1. `toList()` / `toUnmodifiableList()`
Collects all elements into a List. Unmodifiable version throws on add/remove.
```java
List<String> result = names.stream()
    .filter(n -> n.length() > 3)
    .collect(Collectors.toList());
// [Alice, Charlie, Alice, Diana]

List<String> immutable = names.stream()
    .collect(Collectors.toUnmodifiableList());
// immutable.add("X") → throws UnsupportedOperationException
```

### 2. `toSet()` / `toUnmodifiableSet()`
Collects into a Set, automatically removing duplicates.
```java
Set<String> uniqueNames = names.stream()
    .collect(Collectors.toSet());
// [Alice, Bob, Charlie, Diana]

Set<String> immutableSet = names.stream()
    .collect(Collectors.toUnmodifiableSet());
// immutableSet.add("X") → throws UnsupportedOperationException
```

### 3. `toCollection(Supplier)`
Collects into any specific Collection type you provide.
```java
LinkedList<String> linked = names.stream()
    .collect(Collectors.toCollection(LinkedList::new));
// LinkedList: [Alice, Bob, Charlie, Alice, Diana]

TreeSet<String> sortedSet = names.stream()
    .collect(Collectors.toCollection(TreeSet::new));
// TreeSet (sorted, no dupes): [Alice, Bob, Charlie, Diana]
```

### 4. `toMap(keyMapper, valueMapper)`
Builds a Map by extracting key and value from each element. Throws on duplicate keys unless merge function provided.
```java
Map<String, Integer> nameLengths = names.stream()
    .distinct()
    .collect(Collectors.toMap(n -> n, String::length));
// {Alice=5, Bob=3, Charlie=7, Diana=5}

// With merge function — handles duplicate keys (keeps first)
Map<String, Integer> safe = names.stream()
    .collect(Collectors.toMap(n -> n, String::length, (existing, dup) -> existing));
// {Alice=5, Bob=3, Charlie=7, Diana=5}

// With merge + specific map type
Map<String, Double> salaryMap = employees.stream()
    .collect(Collectors.toMap(Employee::name, Employee::salary, (a, b) -> a, TreeMap::new));
// TreeMap: {Alice=95000.0, Bob=72000.0, Charlie=60000.0, Diana=55000.0, Eve=80000.0}
```

### 5. `toUnmodifiableMap(keyMapper, valueMapper)`
Same as toMap but returns an immutable Map.
```java
Map<String, Double> immutableMap = employees.stream()
    .collect(Collectors.toUnmodifiableMap(Employee::name, Employee::salary));
// immutableMap.put("X", 1.0) → throws UnsupportedOperationException
```

### 6. `toConcurrentMap(keyMapper, valueMapper)`
Thread-safe Map for parallel streams.
```java
ConcurrentMap<String, Double> concMap = employees.parallelStream()
    .collect(Collectors.toConcurrentMap(Employee::name, Employee::salary));
// ConcurrentHashMap: {Alice=95000.0, Bob=72000.0, ...}
```

### 7. `joining()`
Concatenates CharSequence elements into a single String. Supports delimiter, prefix, and suffix.
```java
String plain = names.stream()
    .collect(Collectors.joining());
// "AliceBobCharlieAliceDiana"

String csv = names.stream()
    .collect(Collectors.joining(", "));
// "Alice, Bob, Charlie, Alice, Diana"

String formatted = names.stream()
    .distinct()
    .collect(Collectors.joining(" | ", "[ ", " ]"));
// "[ Alice | Bob | Charlie | Diana ]"
```

### 8. `counting()`
Counts the number of elements. Mainly useful as a downstream collector inside groupingBy.
```java
long total = employees.stream()
    .collect(Collectors.counting());
// 5

Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department, Collectors.counting()));
// {Engineering=2, HR=2, Sales=1}
```

### 9. `summingInt()` / `summingLong()` / `summingDouble()`
Sums a numeric property extracted from each element.
```java
double totalSalary = employees.stream()
    .collect(Collectors.summingDouble(Employee::salary));
// 362000.0

Map<String, Double> salaryByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department, Collectors.summingDouble(Employee::salary)));
// {Engineering=167000.0, HR=115000.0, Sales=80000.0}
```

### 10. `averagingInt()` / `averagingLong()` / `averagingDouble()`
Computes the arithmetic mean of a numeric property.
```java
double avgSalary = employees.stream()
    .collect(Collectors.averagingDouble(Employee::salary));
// 72400.0

Map<String, Double> avgByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department, Collectors.averagingDouble(Employee::salary)));
// {Engineering=83500.0, HR=57500.0, Sales=80000.0}
```

### 11. `summarizingInt()` / `summarizingLong()` / `summarizingDouble()`
Returns count, sum, min, max, and average in one pass.
```java
DoubleSummaryStatistics stats = employees.stream()
    .collect(Collectors.summarizingDouble(Employee::salary));
System.out.println(stats.getCount());    // 5
System.out.println(stats.getSum());      // 362000.0
System.out.println(stats.getMin());      // 55000.0
System.out.println(stats.getMax());      // 95000.0
System.out.println(stats.getAverage());  // 72400.0
```

### 12. `minBy(Comparator)` / `maxBy(Comparator)`
Finds the min or max element by a Comparator. Returns Optional.
```java
Optional<Employee> lowestPaid = employees.stream()
    .collect(Collectors.minBy(Comparator.comparingDouble(Employee::salary)));
// Optional[Employee[name=Diana, department=HR, salary=55000.0, ...]]

Optional<Employee> highestPaid = employees.stream()
    .collect(Collectors.maxBy(Comparator.comparingDouble(Employee::salary)));
// Optional[Employee[name=Alice, department=Engineering, salary=95000.0, ...]]
```

### 13. `groupingBy(classifier)`
Groups elements into a `Map<K, List<V>>` using the classifier function.
```java
Map<String, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department));
// {Engineering=[Alice, Bob], HR=[Charlie, Diana], Sales=[Eve]}

Map<Integer, List<String>> byLength = names.stream()
    .collect(Collectors.groupingBy(String::length));
// {3=[Bob], 5=[Alice, Alice, Diana], 7=[Charlie]}
```

### 14. `groupingBy(classifier, downstream)`
Groups elements and applies a downstream collector to each group instead of collecting to List.
```java
// Count per department
Map<String, Long> headcount = employees.stream()
    .collect(Collectors.groupingBy(Employee::department, Collectors.counting()));
// {Engineering=2, HR=2, Sales=1}

// Sum salary per department
Map<String, Double> totalSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department, Collectors.summingDouble(Employee::salary)));
// {Engineering=167000.0, HR=115000.0, Sales=80000.0}

// Collect just names per department
Map<String, List<String>> namesByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.mapping(Employee::name, Collectors.toList())));
// {Engineering=[Alice, Bob], HR=[Charlie, Diana], Sales=[Eve]}

// Joining names per department
Map<String, String> joinedNames = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.mapping(Employee::name, Collectors.joining(", "))));
// {Engineering="Alice, Bob", HR="Charlie, Diana", Sales="Eve"}
```

### 15. `groupingBy(classifier, mapFactory, downstream)`
Same as above but lets you choose the Map implementation.
```java
TreeMap<String, Long> sortedHeadcount = employees.stream()
    .collect(Collectors.groupingBy(Employee::department, TreeMap::new, Collectors.counting()));
// TreeMap (sorted keys): {Engineering=2, HR=2, Sales=1}
```

### 16. `groupingByConcurrent(classifier)`
Thread-safe grouping for parallel streams. Returns ConcurrentMap.
```java
ConcurrentMap<String, List<Employee>> concGrouped = employees.parallelStream()
    .collect(Collectors.groupingByConcurrent(Employee::department));
// ConcurrentHashMap: {Engineering=[Alice, Bob], HR=[Charlie, Diana], Sales=[Eve]}
```

### 17. `partitioningBy(predicate)`
Splits elements into exactly two groups: `true` and `false`.
```java
Map<Boolean, List<Integer>> evenOdd = numbers.stream()
    .collect(Collectors.partitioningBy(n -> n % 2 == 0));
// {true=[2, 4, 6, 8, 10], false=[1, 3, 5, 7, 9]}

Map<Boolean, List<Employee>> highLow = employees.stream()
    .collect(Collectors.partitioningBy(e -> e.salary() > 70000));
// {true=[Alice, Bob, Eve], false=[Charlie, Diana]}
```

### 18. `partitioningBy(predicate, downstream)`
Partitions and applies a downstream collector to each partition.
```java
Map<Boolean, Long> evenOddCount = numbers.stream()
    .collect(Collectors.partitioningBy(n -> n % 2 == 0, Collectors.counting()));
// {true=5, false=5}

Map<Boolean, String> partitionedNames = employees.stream()
    .collect(Collectors.partitioningBy(e -> e.salary() > 70000,
        Collectors.mapping(Employee::name, Collectors.joining(", "))));
// {true="Alice, Bob, Eve", false="Charlie, Diana"}
```

### 19. `mapping(mapper, downstream)`
Applies a transformation before passing to the downstream collector. Most useful inside groupingBy.
```java
// Extract just the names grouped by department
Map<String, List<String>> namesByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.mapping(Employee::name, Collectors.toList())));
// {Engineering=[Alice, Bob], HR=[Charlie, Diana], Sales=[Eve]}

// Collect uppercased names into a Set
Set<String> upperNames = employees.stream()
    .collect(Collectors.mapping(e -> e.name().toUpperCase(), Collectors.toSet()));
// [ALICE, BOB, CHARLIE, DIANA, EVE]
```

### 20. `flatMapping(mapper, downstream)` *(Java 9+)*
Flat-maps each element to a stream, then collects. Essential for flattening nested collections inside groupingBy.
```java
// All unique skills per department
Map<String, Set<String>> skillsByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.flatMapping(e -> e.skills().stream(), Collectors.toSet())));
// {Engineering=[Java, Spring, Python], HR=[Recruiting, Training], Sales=[CRM, Analytics]}

// All skills across all employees (flat)
Set<String> allSkills = employees.stream()
    .collect(Collectors.flatMapping(e -> e.skills().stream(), Collectors.toSet()));
// [Java, Spring, Python, Recruiting, Training, CRM, Analytics]
```

### 21. `filtering(predicate, downstream)` *(Java 9+)*
Filters elements before passing to downstream. Unlike stream.filter(), this preserves empty groups in groupingBy.
```java
// Count high earners (>70k) per department — departments with 0 still appear
Map<String, Long> highEarners = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.filtering(e -> e.salary() > 70000, Collectors.counting())));
// {Engineering=2, HR=0, Sales=1}
//  ↑ HR appears with 0 — stream.filter() would have dropped it entirely

Map<String, List<String>> highEarnerNames = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.filtering(e -> e.salary() > 70000,
            Collectors.mapping(Employee::name, Collectors.toList()))));
// {Engineering=[Alice, Bob], HR=[], Sales=[Eve]}
```

### 22. `collectingAndThen(downstream, finisher)`
Collects using a downstream collector, then applies a final transformation on the result.
```java
// Collect to list, then make it unmodifiable
List<String> immutable = names.stream()
    .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
// immutable.add("X") → throws UnsupportedOperationException

// Get max salary employee as a non-Optional value
Employee topEarner = employees.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.maxBy(Comparator.comparingDouble(Employee::salary)),
        opt -> opt.orElseThrow()));
// Employee[name=Alice, department=Engineering, salary=95000.0, ...]

// Get size after collecting to a set (count unique)
int uniqueCount = names.stream()
    .collect(Collectors.collectingAndThen(Collectors.toSet(), Set::size));
// 4
```

### 23. `teeing(downstream1, downstream2, merger)` *(Java 12+)*
Sends every element to two independent collectors simultaneously, then merges their results.
```java
// Get both min and max salary in one pass
String minMaxReport = employees.stream()
    .collect(Collectors.teeing(
        Collectors.minBy(Comparator.comparingDouble(Employee::salary)),
        Collectors.maxBy(Comparator.comparingDouble(Employee::salary)),
        (min, max) -> "Lowest: " + min.orElseThrow().name()
                     + ", Highest: " + max.orElseThrow().name()));
// "Lowest: Diana, Highest: Alice"

// Count and average in one pass
record CountAvg(long count, double average) {}
CountAvg result = employees.stream()
    .collect(Collectors.teeing(
        Collectors.counting(),
        Collectors.averagingDouble(Employee::salary),
        CountAvg::new));
// CountAvg[count=5, average=72400.0]

// Sum and list in one pass
record SalaryReport(double total, List<String> names) {}
SalaryReport report = employees.stream()
    .collect(Collectors.teeing(
        Collectors.summingDouble(Employee::salary),
        Collectors.mapping(Employee::name, Collectors.toList()),
        SalaryReport::new));
// SalaryReport[total=362000.0, names=[Alice, Bob, Charlie, Diana, Eve]]
```

### 24. `reducing()` *(Collector version of Stream.reduce)*
Performs a reduction as a Collector. Useful as a downstream collector inside groupingBy.
```java
// Sum all salaries via reducing
Optional<Double> totalSalary = employees.stream()
    .map(Employee::salary)
    .collect(Collectors.reducing(Double::sum));
// Optional[362000.0]

// With identity — returns value directly, not Optional
double totalWithIdentity = employees.stream()
    .map(Employee::salary)
    .collect(Collectors.reducing(0.0, Double::sum));
// 362000.0

// Highest salary per department using reducing
Map<String, Optional<Employee>> topPerDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.reducing(BinaryOperator.maxBy(
            Comparator.comparingDouble(Employee::salary)))));
// {Engineering=Optional[Alice], HR=Optional[Charlie], Sales=Optional[Eve]}
```

---

## Advanced: Nested / Chained Collectors

```java
// Top earner name per department (groupingBy + collectingAndThen + maxBy + mapping)
Map<String, String> topEarnerByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.collectingAndThen(
            Collectors.maxBy(Comparator.comparingDouble(Employee::salary)),
            opt -> opt.map(Employee::name).orElse("N/A"))));
// {Engineering="Alice", HR="Charlie", Sales="Eve"}

// Department → comma-separated skills (groupingBy + flatMapping + joining)
Map<String, String> deptSkills = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.flatMapping(e -> e.skills().stream(),
            Collectors.joining(", "))));
// {Engineering="Java, Spring, Python, Java", HR="Recruiting, Training, Recruiting", Sales="CRM, Analytics"}

// Salary stats per department (groupingBy + summarizingDouble)
Map<String, DoubleSummaryStatistics> statsByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::department,
        Collectors.summarizingDouble(Employee::salary)));
// Engineering → {count=2, sum=167000, min=72000, max=95000, avg=83500}
```

---

## Custom Collector

You can build your own Collector using `Collector.of()`.
```java
// Custom collector: join strings with " and " for the last element
Collector<String, ?, String> oxfordJoin = Collector.of(
    ArrayList::new,                                   // supplier
    ArrayList::add,                                   // accumulator
    (a, b) -> { a.addAll(b); return a; },             // combiner
    list -> {                                         // finisher
        if (list.isEmpty()) return "";
        if (list.size() == 1) return list.get(0).toString();
        return String.join(", ", list.subList(0, list.size() - 1))
            + ", and " + list.get(list.size() - 1);
    }
);

String result = Stream.of("Alice", "Bob", "Charlie")
    .collect(oxfordJoin);
// "Alice, Bob, and Charlie"
```

---

## Quick Cheat Sheet

| Goal | Collector | Example |
|------|-----------|---------|
| List | `toList()` | `stream.collect(toList())` |
| Immutable List | `toUnmodifiableList()` | `stream.collect(toUnmodifiableList())` |
| Set | `toSet()` | `stream.collect(toSet())` |
| Specific Collection | `toCollection(Supplier)` | `stream.collect(toCollection(TreeSet::new))` |
| Map | `toMap(k, v)` | `stream.collect(toMap(e::id, e::name))` |
| String concat | `joining(delim, pre, suf)` | `stream.collect(joining(", ", "[", "]"))` |
| Count | `counting()` | `groupingBy(fn, counting())` |
| Sum | `summingDouble(fn)` | `groupingBy(fn, summingDouble(e::sal))` |
| Average | `averagingDouble(fn)` | `groupingBy(fn, averagingDouble(e::sal))` |
| Statistics | `summarizingDouble(fn)` | `stream.collect(summarizingDouble(e::sal))` |
| Min / Max | `minBy(cmp)` / `maxBy(cmp)` | `stream.collect(maxBy(comparing(e::sal)))` |
| Group | `groupingBy(fn)` | `stream.collect(groupingBy(e::dept))` |
| Group + aggregate | `groupingBy(fn, downstream)` | `groupingBy(e::dept, counting())` |
| Split true/false | `partitioningBy(pred)` | `stream.collect(partitioningBy(n -> n>0))` |
| Transform + collect | `mapping(fn, downstream)` | `groupingBy(fn, mapping(e::name, toList()))` |
| Flatten + collect | `flatMapping(fn, downstream)` | `groupingBy(fn, flatMapping(e::skills, toSet()))` |
| Filter + collect | `filtering(pred, downstream)` | `groupingBy(fn, filtering(pred, counting()))` |
| Post-process | `collectingAndThen(down, fn)` | `collectingAndThen(toList(), unmodifiableList)` |
| Two at once | `teeing(d1, d2, merger)` | `teeing(counting(), summingInt(x), Rec::new)` |
| Reduce | `reducing(op)` | `groupingBy(fn, reducing(Double::sum))` |

---

## Java Generics — Deep Dive

Generics power every functional interface (`Function<T, R>`, `Predicate<T>`, etc.) and the entire Stream API. Understanding them is essential.

### What are Generics?

Generics allow you to write **type-safe, reusable** code. Instead of hardcoding a type, you use a **type parameter** that gets filled in when the class/method is used.

```java
// WITHOUT generics — unsafe, requires casting
List list = new ArrayList();
list.add("hello");
String s = (String) list.get(0);   // manual cast — can fail at runtime

// WITH generics — type-safe, no casting
List<String> list = new ArrayList<>();
list.add("hello");
String s = list.get(0);   // no cast needed — compiler guarantees String
// list.add(123);          // ❌ COMPILE ERROR — only String allowed
```

---

### `<T>` — Unbounded Type Parameter

`T` is a **placeholder** for any type. It gets replaced with a real type when used. You can name it anything, but conventions are:

```
 T = Type       E = Element     K = Key
 V = Value      R = Return      N = Number
```

**Analogy: Blank Name Tag** — `T` is like a blank name tag on a locker. Any student can stick their name on it. Once Alice writes her name, that locker only holds Alice's stuff.

```java
// Generic class — T is decided when you create an instance
class Box<T> {
    private T item;

    public void put(T item)  { this.item = item; }
    public T    get()         { return item; }
}

Box<String>  strBox = new Box<>();     // T = String
strBox.put("Hello");
String s = strBox.get();               // returns String, no cast

Box<Integer> intBox = new Box<>();     // T = Integer
intBox.put(42);
Integer n = intBox.get();              // returns Integer, no cast

// Generic method — T is inferred from arguments
public static <T> List<T> singletonList(T item) {
    return List.of(item);
}
List<String> list = singletonList("Hello");   // T inferred as String
```

---

### `<T extends Something>` — Bounded Type Parameter

Restricts `T` to a specific type or its subtypes. This **guarantees** you can call methods from the bound.

**Analogy: VIP Lounge** — Not just anyone can enter. You need a VIP pass (`extends VIP`). Once inside, the staff knows you have VIP privileges (methods) and can offer VIP services.

```java
// T must be a Number or subclass (Integer, Double, Long, etc.)
class MathBox<T extends Number> {
    private T value;

    public MathBox(T value) { this.value = value; }

    public double doubleValue() {
        return value.doubleValue();   // ✅ safe — T IS-A Number, so .doubleValue() exists
    }
}

MathBox<Integer> intBox = new MathBox<>(42);
intBox.doubleValue();   // 42.0

MathBox<Double> dblBox = new MathBox<>(3.14);
dblBox.doubleValue();   // 3.14

// MathBox<String> strBox = new MathBox<>("hi");  // ❌ COMPILE ERROR — String is not a Number

// Multiple bounds — T must implement BOTH Comparable AND Serializable
class SortableBox<T extends Comparable<T> & Serializable> {
    // T guaranteed to have .compareTo() AND be serializable
}
```

```
 ┌───────────────────────────────────────────────────────────────┐
 │  <T>                    →  ANY type. No restrictions.         │
 │  <T extends Number>     →  Number or its subclasses only.    │
 │  <T extends Comparable  →  Must implement BOTH interfaces.   │
 │       & Serializable>                                        │
 └───────────────────────────────────────────────────────────────┘
```

---

### `<?>` — Wildcard

`?` means "some unknown type." Used in **method parameters** when you don't care about the specific type — you just want to accept any generic variant.

**Analogy: "Any Gate" at an Airport** — You don't care which gate number. You just need A gate. You can look at flights (read), but you can't assign a new flight to an unknown gate (write).

```java
// Without wildcard — only accepts List<String>, nothing else
public void printStrings(List<String> list) { ... }

// With wildcard — accepts List<String>, List<Integer>, List<anything>
public void printAll(List<?> list) {
    for (Object item : list) {
        System.out.println(item);   // ✅ can read as Object
    }
    // list.add("hello");           // ❌ COMPILE ERROR — can't add (type unknown)
    list.add(null);                 // ✅ null is the only thing you can add
}

printAll(List.of("Alice", "Bob"));    // ✅ List<String>
printAll(List.of(1, 2, 3));            // ✅ List<Integer>
printAll(List.of(3.14, 2.71));         // ✅ List<Double>
```

---

### `? extends T` vs `? super T` — Bounded Wildcards (PECS)

This is the most important generics concept for interviews: **PECS = Producer Extends, Consumer Super**.

```
 ┌─────────────────────────────────────────────────────────────────┐
 │                     TYPE HIERARCHY                             │
 │                                                                │
 │                       Object                                   │
 │                         │                                      │
 │                       Number                                   │
 │                      /      \                                  │
 │                 Integer    Double                               │
 │                                                                │
 │              ──────────────────────                             │
 │                                                                │
 │                       Fruit                                    │
 │                      /     \                                   │
 │                  Apple    Banana                                │
 │                   /                                            │
 │              GreenApple                                        │
 └─────────────────────────────────────────────────────────────────┘
```

#### `? extends T` — Upper Bounded Wildcard (PRODUCER)

"I accept T **or any subclass** of T." You can **READ** from it (produces values), but **CANNOT WRITE** to it.

**Analogy: Museum Display Case** — You can LOOK at the items inside (read), but you CANNOT put anything in (write). The case might hold paintings or sculptures — you don't know exactly which, so you can't safely add anything.

```java
// "A list of some type that IS-A Number" — could be List<Integer>, List<Double>, etc.
public double sum(List<? extends Number> numbers) {
    double total = 0;
    for (Number n : numbers) {
        total += n.doubleValue();   // ✅ READ: safe — everything IS-A Number
    }
    // numbers.add(42);             // ❌ WRITE: unsafe — what if it's List<Double>?
    return total;
}

sum(List.of(1, 2, 3));              // ✅ List<Integer> — Integer extends Number
sum(List.of(1.1, 2.2));             // ✅ List<Double> — Double extends Number
sum(List.of(1L, 2L));               // ✅ List<Long> — Long extends Number
// sum(List.of("a", "b"));          // ❌ String doesn't extend Number
```

**Why can't you write?** The compiler doesn't know the EXACT type. If the list is actually `List<Double>`, adding an `Integer` would corrupt it.

#### `? super T` — Lower Bounded Wildcard (CONSUMER)

"I accept T **or any superclass** of T." You can **WRITE** T to it (consumes values), but reading gives you `Object`.

**Analogy: Recycling Bin** — You can THROW IN (write) specific items. The bin might be for "plastic" or "all recyclables" or "everything" — but it definitely accepts plastic. When you look inside (read), you just see "stuff" (Object).

```java
// "A list of some type that is a SUPERCLASS of Integer"
// Could be List<Integer>, List<Number>, or List<Object>
public void addNumbers(List<? super Integer> list) {
    list.add(1);     // ✅ WRITE: safe — list holds Integer or a parent type
    list.add(2);     // ✅ WRITE: Integer fits into Integer, Number, or Object
    list.add(3);     // ✅

    Object item = list.get(0);  // READ: only guaranteed to be Object
    // Integer i = list.get(0); // ❌ might be List<Number> holding a Double
}

List<Integer> ints = new ArrayList<>();
addNumbers(ints);      // ✅ Integer super Integer

List<Number> nums = new ArrayList<>();
addNumbers(nums);      // ✅ Number super Integer

List<Object> objs = new ArrayList<>();
addNumbers(objs);      // ✅ Object super Integer

// List<Double> dbls = new ArrayList<>();
// addNumbers(dbls);   // ❌ Double is NOT a superclass of Integer
```

#### PECS Visual Summary

```
 ╔══════════════════════════════════════════════════════════════════════╗
 ║                   PECS: Producer Extends, Consumer Super           ║
 ╠══════════════════════════════════════════════════════════════════════╣
 ║                                                                     ║
 ║  ? extends T  (PRODUCER — READ-ONLY)                               ║
 ║  ┌─────────────────┐                                                ║
 ║  │ List<? extends  │     ✅ Can READ as T                           ║
 ║  │    Number>      │     ❌ Cannot WRITE (except null)               ║
 ║  │                 │     "I produce Number values for you"          ║
 ║  │  [Integer ✅]   │                                                ║
 ║  │  [Double  ✅]   │     USE WHEN: you only need to READ/GET        ║
 ║  │  [Long    ✅]   │     from the collection                        ║
 ║  └─────────────────┘                                                ║
 ║                                                                     ║
 ║  ? super T  (CONSUMER — WRITE-ONLY)                                ║
 ║  ┌─────────────────┐                                                ║
 ║  │ List<? super    │     ✅ Can WRITE T into it                     ║
 ║  │    Integer>     │     ❌ Cannot READ as T (only as Object)       ║
 ║  │                 │     "I consume Integer values from you"        ║
 ║  │  [Integer ✅]   │                                                ║
 ║  │  [Number  ✅]   │     USE WHEN: you only need to ADD/PUT         ║
 ║  │  [Object  ✅]   │     into the collection                        ║
 ║  └─────────────────┘                                                ║
 ║                                                                     ║
 ║  MEMORY AID:                                                        ║
 ║    extends = EXIT data from collection  (read/produce)              ║
 ║    super   = STUFF data into collection (write/consume)             ║
 ║                                                                     ║
 ╚══════════════════════════════════════════════════════════════════════╝
```

#### PECS in Real Code — `Collections.copy()`

```java
// Java's actual Collections.copy() signature uses PECS perfectly:
public static <T> void copy(List<? super T> dest, List<? extends T> src) {
//                               ^^^^^ CONSUMER       ^^^^^^^ PRODUCER
//                               (writes TO dest)     (reads FROM src)
    for (int i = 0; i < src.size(); i++) {
        dest.set(i, src.get(i));   // read from src (extends), write to dest (super)
    }
}

List<Number> destination = new ArrayList<>(List.of(0, 0, 0));
List<Integer> source = List.of(1, 2, 3);
Collections.copy(destination, source);   // ✅ copies Integer → Number
```

#### PECS in Functional Interfaces

Java's functional interfaces use PECS extensively:

```java
// Function signature:
// R apply(T t)
// But the full generic signature is:
// Function<? super T, ? extends R>

// Predicate.and() accepts a broader predicate:
// Predicate<? super T> other

// Why? Consider:
Predicate<Number> isPositive = n -> n.doubleValue() > 0;
Predicate<Integer> isEven = n -> n % 2 == 0;

// Integer IS-A Number, so a Predicate<Number> can test Integers:
Predicate<Integer> positiveAndEven = isEven.and(isPositive);  // ✅ works because of ? super T
```

---

### Type Erasure

Generics exist only at **compile time**. At runtime, the JVM erases all generic type information — this is called **type erasure**.

**Analogy: Airport Boarding Pass** — At the check-in counter (compile time), your pass says "Business Class" or "Economy." But once you're through the gate (runtime), the plane just sees "passenger." The class distinction is gone.

```
 ╔══════════════════════════════════════════════════════════════╗
 ║                      TYPE ERASURE                           ║
 ╠══════════════════════════════════════════════════════════════╣
 ║                                                             ║
 ║  COMPILE TIME (generics exist)    RUNTIME (generics erased) ║
 ║  ────────────────────────────     ────────────────────────── ║
 ║  List<String>                 →   List                      ║
 ║  List<Integer>                →   List                      ║
 ║  Map<String, Employee>        →   Map                       ║
 ║  Function<String, Integer>    →   Function                  ║
 ║  Box<T>                       →   Box                       ║
 ║  Box<T extends Number>        →   Box  (T → Number)         ║
 ║                                                             ║
 ║  At runtime, List<String> and List<Integer>                 ║
 ║  are THE SAME CLASS: java.util.List                         ║
 ╚══════════════════════════════════════════════════════════════╝
```

```java
// What you write:
public class Box<T> {
    private T item;
    public T get() { return item; }
}

// What JVM sees after erasure:
public class Box {
    private Object item;            // T → Object
    public Object get() { return item; }
}

// With bounded type:
public class MathBox<T extends Number> {
    private T value;
}
// JVM sees:
public class MathBox {
    private Number value;           // T → Number (the bound)
}
```

#### What you CANNOT do because of type erasure:

```java
// ❌ Cannot create instance of T
public <T> T create() {
    return new T();                 // ERROR — JVM doesn't know what T is
}

// ❌ Cannot use instanceof with generics
if (list instanceof List<String>) { }  // ERROR — erased to List at runtime

// ❌ Cannot create generic array
T[] array = new T[10];             // ERROR

// ❌ Cannot overload by generic type only
void process(List<String> list) { }
void process(List<Integer> list) { }  // ERROR — both erase to process(List)

// ❌ Cannot use .class on parameterized type
Class<?> c = List<String>.class;   // ERROR — only List.class exists

// ✅ What you CAN do
if (list instanceof List<?>) { }   // ✅ unbounded wildcard OK
list.getClass() == ArrayList.class // ✅ checks raw type
```

#### Workaround: Pass Class<T> token

```java
// Since T is erased, pass the Class object explicitly:
public <T> T create(Class<T> clazz) throws Exception {
    return clazz.getDeclaredConstructor().newInstance();   // ✅ works
}

String s = create(String.class);       // ✅
Employee e = create(Employee.class);   // ✅
```

#### Type Erasure with Functional Interfaces

```java
// These two are THE SAME at runtime:
Function<String, Integer> f1 = String::length;
Function<Employee, Double> f2 = Employee::salary;

// Both erase to: Function (with Object apply(Object))
// The compiler inserts casts for you:
// f1.apply("hello") → compiler adds (Integer) cast on return
```

---

### `<T>` vs `<?>` — When to Use Which

```
 ┌────────────────────────────────────────────────────────────────────┐
 │  <T>                              │  <?>                          │
 ├────────────────────────────────────────────────────────────────────┤
 │  Declares a new type variable     │  Refers to an unknown type   │
 │  Used in class/method definition  │  Used in method parameters   │
 │  You CAN reference T later       │  You CANNOT reference ? later │
 │  Can have bounds: T extends X     │  Can have bounds: ? extends X │
 ├────────────────────────────────────────────────────────────────────┤
 │                                                                    │
 │  // T — I need to USE the type:                                   │
 │  <T> T firstOf(List<T> list) {                                    │
 │      return list.get(0);          // return TYPE is T             │
 │  }                                                                │
 │                                                                    │
 │  // ? — I DON'T CARE about the type:                              │
 │  void printSize(List<?> list) {                                   │
 │      System.out.println(list.size()); // don't use element type   │
 │  }                                                                │
 └────────────────────────────────────────────────────────────────────┘
```

```java
// USE <T> when you need the type in multiple places:
public <T> void copy(List<T> src, List<T> dest) {
    // T links src and dest — they MUST be the same type
}

// USE <?> when you only need to read and don't care about the type:
public void printAll(List<?> items) {
    items.forEach(System.out::println);  // just prints — doesn't need type
}

// USE <T> when the return type depends on input:
public <T> T last(List<T> list) {
    return list.get(list.size() - 1);    // return TYPE must match list TYPE
}

// USE <?> when any type works and you don't return it:
public boolean isEmpty(Collection<?> c) {
    return c.size() == 0;               // don't care about element type
}
```

---

## Generics Interview Questions

### Q1: What is the difference between `<T>`, `<T extends Something>`, and `<?>`?

**A:** They serve different purposes in the generics system:

| Syntax | Name | Purpose | Example |
|---|---|---|---|
| `<T>` | Type parameter | Declares a named placeholder for any type | `class Box<T> { T item; }` |
| `<T extends Number>` | Bounded type parameter | Restricts T to Number or subclasses | `<T extends Comparable<T>> T max(T a, T b)` |
| `<?>` | Unbounded wildcard | Accepts any generic type, name not needed | `void print(List<?> list)` |
| `<? extends T>` | Upper bounded wildcard | Read-only — produces T values | `double sum(List<? extends Number> list)` |
| `<? super T>` | Lower bounded wildcard | Write-only — consumes T values | `void addInts(List<? super Integer> list)` |

```java
// <T> — I declare a type and USE it in multiple places
public <T> T pick(T a, T b) { return Math.random() > 0.5 ? a : b; }

// <T extends Comparable> — T must have compareTo()
public <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;    // ✅ compareTo() guaranteed
}

// <?> — I don't need to know or use the type
public int size(List<?> list) { return list.size(); }

// <? extends Number> — read Numbers from any numeric list
public double sum(List<? extends Number> nums) {
    return nums.stream().mapToDouble(Number::doubleValue).sum();
}

// <? super Integer> — write Integers into a compatible list
public void fill(List<? super Integer> list, int count) {
    for (int i = 0; i < count; i++) list.add(i);
}
```

---

### Q2: What is type erasure? What are its limitations?

**A:** Type erasure means the compiler removes all generic type info after compilation. At runtime, `List<String>` and `List<Integer>` are both just `List`. This was done for **backward compatibility** with pre-generics Java code.

**Limitations:**
```java
// 1. Cannot create instances of T
// new T()                    ❌

// 2. Cannot use instanceof with parameterized types
// obj instanceof List<String> ❌  (use List<?> instead)

// 3. Cannot create generic arrays
// new T[10]                  ❌

// 4. Cannot overload by generic type alone
// void process(List<String>)  ❌  both erase to process(List)
// void process(List<Integer>)

// 5. Static fields cannot use class type parameter
// class Box<T> { static T value; }  ❌
```

**How the compiler handles erasure:**
```java
// You write:
List<String> list = new ArrayList<>();
list.add("hello");
String s = list.get(0);

// Compiler produces (after erasure):
List list = new ArrayList();
list.add("hello");
String s = (String) list.get(0);   // compiler inserts the cast
```

---

### Q3: Explain PECS (Producer Extends, Consumer Super) with an example.

**A:** PECS tells you which bounded wildcard to use based on whether the collection **produces** or **consumes** values.

```java
// PRODUCER (extends) — you READ from it
public double sumAll(List<? extends Number> numbers) {
    double sum = 0;
    for (Number n : numbers) sum += n.doubleValue();   // ✅ READ
    // numbers.add(42);                                 // ❌ can't WRITE
    return sum;
}
sumAll(List.of(1, 2, 3));         // ✅ List<Integer>
sumAll(List.of(1.1, 2.2));        // ✅ List<Double>

// CONSUMER (super) — you WRITE to it
public void addNumbers(List<? super Integer> target) {
    target.add(1);                   // ✅ WRITE
    target.add(2);
    // Integer n = target.get(0);    // ❌ can only READ as Object
}
addNumbers(new ArrayList<Number>());  // ✅ Number is super of Integer
addNumbers(new ArrayList<Object>());  // ✅ Object is super of Integer

// BOTH in one method — the classic pattern
public <T> void copy(List<? super T> dest, List<? extends T> src) {
    for (T item : src) {    // READ from src (extends = producer)
        dest.add(item);     // WRITE to dest (super = consumer)
    }
}
```

**Quick rule:**
- Use `extends` when you only **GET** values out
- Use `super` when you only **PUT** values in
- Use exact type `<T>` when you both get and put

---

### Q4: Can you overload methods that differ only in generic type parameters?

**A:** No. Due to type erasure, both methods have the same signature at runtime.

```java
// ❌ COMPILE ERROR — both erase to process(List)
public void process(List<String> strings)  { }
public void process(List<Integer> numbers) { }

// ✅ Workaround 1: Use different method names
public void processStrings(List<String> strings)  { }
public void processNumbers(List<Integer> numbers) { }

// ✅ Workaround 2: Use generics with a single method
public <T> void process(List<T> items) { }

// ✅ Workaround 3: Add a distinguishing parameter
public void process(List<String> strings, String marker) { }
public void process(List<Integer> numbers, Integer marker) { }
```

---

### Q5: What is the difference between `List<Object>` and `List<?>`?

**A:** `List<Object>` is a concrete type — a list that holds Objects. `List<?>` is a wildcard — a list of some unknown type. They are NOT interchangeable.

```java
// List<Object> — you KNOW it holds Objects, can read AND write
List<Object> objects = new ArrayList<>();
objects.add("hello");    // ✅ can add
objects.add(42);         // ✅ can add anything
Object o = objects.get(0); // ✅ can read

// List<?> — unknown type, READ-only (as Object)
List<?> unknown = List.of("hello", "world");
Object o = unknown.get(0);   // ✅ read as Object
// unknown.add("new");        // ❌ can't write — type is unknown

// Key difference in method parameters:
void takeObjects(List<Object> list) { }
void takeAnything(List<?> list) { }

takeObjects(List.of("a"));          // ❌ List<String> is NOT List<Object>
takeAnything(List.of("a"));         // ✅ List<?> accepts any List<X>
takeAnything(List.of(1, 2));        // ✅
```

**Why isn't `List<String>` a subtype of `List<Object>`?**
```java
// If it were allowed, you could corrupt the list:
List<String> strings = new ArrayList<>();
List<Object> objects = strings;       // hypothetically allowed...
objects.add(42);                      // put Integer into a String list!
String s = strings.get(0);           // ClassCastException! 42 is not String
// This is why Java disallows it.
```

---

### Q6: How do generics work with functional interfaces? Explain `Function<? super T, ? extends R>`.

**A:** Functional interface methods use PECS in their signatures for maximum flexibility. The `Function.andThen` signature demonstrates this:

```java
// Simplified Function interface:
interface Function<T, R> {
    R apply(T t);

    // andThen signature uses PECS:
    default <V> Function<T, V> andThen(
        Function<? super R, ? extends V> after    // ← PECS here
    ) {
        return (T t) -> after.apply(this.apply(t));
    }
}
```

```
 Why ? super R?  →  "after" CONSUMES R (reads our output)
                    so it can accept R or any parent of R
 Why ? extends V? → "after" PRODUCES V (generates output)
                    so it can return V or any subclass of V
```

```java
// Real example showing why PECS matters here:
Function<String, Integer> strToInt = Integer::parseInt;     // String → Integer
Function<Number, String>  numToStr = n -> "Value: " + n;    // Number → String

// Without PECS: would fail because Number ≠ Integer
// With PECS: works because Number is ? super Integer
Function<String, String> pipeline = strToInt.andThen(numToStr);
pipeline.apply("42");   // "Value: 42"
```

---

### Quick Reference: Generics

| Syntax | Meaning | Read/Write | Use When |
|---|---|---|---|
| `<T>` | Any type | Both | Declaring classes/methods |
| `<T extends X>` | T must be X or subclass | Both | Need methods from X |
| `<?>` | Unknown type | Read (as Object) | Don't care about type |
| `<? extends T>` | T or subclass | Read only | Getting FROM collection (Producer) |
| `<? super T>` | T or superclass | Write only | Putting INTO collection (Consumer) |
