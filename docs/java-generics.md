
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
