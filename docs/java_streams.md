# Java Streams & Collectors — Complete Reference

## Table of Contents
1. [Stream Creation](#stream-creation)
2. [Intermediate Operations](#intermediate-operations)
3. [Terminal Operations](#terminal-operations)
4. [Collectors — Complete Reference](#collectors--complete-reference)

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
