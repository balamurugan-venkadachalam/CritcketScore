# Spring Advanced Interview Questions - Part 3 (Questions 51-65)

## Advanced Testing: Unit, Integration & End-to-End

### 51. What is the difference between `@Mock`, `@InjectMocks`, and `@MockBean`?
**Answer:**
- **`@Mock` (Mockito):** Creates a lightweight, completely isolated fake object. It does not start the Spring Application Context. It is extremely fast and perfect for pure Unit Tests.
- **`@InjectMocks` (Mockito):** Marks a real object on which the injection should be performed. Mockito will look at the fields annotated with `@Mock` and automatically inject them into this real object via constructor or setter injection.
- **`@MockBean` (Spring Boot Test):** Specifically adds a mock to the Spring Application Context. If a bean of the same type exists, it is completely replaced. This is heavily used in Integration Testing (e.g., inside `@WebMvcTest`) to mock out database layers or external API calls while testing the real Controller and HttpMessageConverters. Note: Using `@MockBean` dirties the Spring Context, forcing it to reload entirely for the next test class, which drastically slows down test execution times.

### 52. How do you test a Spring MVC Controller purely without starting the full application?
**Answer:**
Using the `@WebMvcTest(MyController.class)` annotation combined with `MockMvc`.
This annotation is an *auto-configured slice* of the application context. It only loads beans required to test the web layer (Controllers, Filters, ControllerAdvice, Interceptors, Jackson/Gson converters, ExceptionHandlers) but **does not** load `@Service`, `@Repository`, or Database configurations.
You must use `@MockBean` to mock out the underlying Service layer that the Controller depends on. 

### 53. How do you test JPA Repositories in isolation?
**Answer:**
Using the `@DataJpaTest` annotation.
This is another *slice* test. It completely ignores web layers and `@Service` classes.
By default, `@DataJpaTest` automatically configures an in-memory embedded database (like H2), configures Hibernate, Spring Data, and the `DataSource`, and rolls back every transaction at the end of each test method to ensure tests run in total isolation without state leaking.
If you want to run these tests against a real database (like PostgreSQL), you can disable the embedded db replacement with `@AutoConfigureTestDatabase(replace = Replace.NONE)`.

### 54. What are Testcontainers and why are they preferred over in-memory databases like H2?
**Answer:**
**The Problem:** H2 is a fantastic lightweight database, but it is not 100% syntactically identical to PostgreSQL or MySQL. If your code relies on Postgres-specific features (like JSONB fields, Window Functions, or UUID generation logic), your code might pass perfectly inside H2 but fail entirely in Production.
**The Solution:** Testcontainers is a Java library that uses Docker APIs to spin up real, disposable instances of PostgreSQL, Redis, Kafka, or LocalStack (AWS) dynamically right before your Integration Tests run, and destroys them afterward. It perfectly mimics the production environment directly on your local machine and CI pipeline without writing external scripts.

### 55. How do you implement Integration Testing for an entire Spring Boot Application?
**Answer:**
You use **`@SpringBootTest`**. This annotation essentially calls `main()` to boot up the entire Spring Application Context exactly as it would in production.
- `webEnvironment = WebEnvironment.MOCK`: The default. Boots everything but doesn't actually bind to a real HTTP port. You interact with it using `MockMvc`.
- `webEnvironment = WebEnvironment.RANDOM_PORT`: Actually binds embedded Tomcat to a random available port. Excellent for true end-to-end tests when combined with the **`TestRestTemplate`** or **`WebTestClient`** to perform genuine HTTP network calls over the local loopback adapter against your running application instance.

### 56. What is Spring Cloud Contract and Consumer-Driven Contract Testing?
**Answer:**
In a microservices architecture, a massive problem is testing that Service A (Consumer) and Service B (Provider) can still talk to each other correctly. `MyObject` in Service B might change a field from "userName" to "firstName", instantly breaking Service A.
**Consumer-Driven Contracts:**
Instead of running expensive end-to-end tests across 15 real microservices simply to check a payload, you write a text "Contract" document specifying exactly what the HTTP request will look like and what the JSON response should be.
- **Provider Side:** Spring Cloud Contract automatically generates JUnit tests from this text file to ensure the actual Service B code legitimately outputs that exact JSON payload. Upon passing, a "stub.jar" is generated and pushed to an artifact repository.
- **Consumer Side:** Service A downloads the "stub.jar" globally, which uses WireMock to intercept the HTTP call locally and return the exact signed response. The two services safely test their integration completely offline and disconnected.

### 57. How do you mock external API calls during Integration Testing?
**Answer:**
Never make network calls to external third-party services (like Stripe or Google Maps) during automated testing. They rate-limit, require API keys, timeout randomly, and break the build.
**Implementation:**
Use **WireMock**. It acts as a lightweight standalone HTTP server that intercepts network calls generated by `RestTemplate` or `WebClient`.
You define strict JSON stubs `stubFor(get(urlEqualTo("/api/v1/users/5")).willReturn(aResponse().withBody("{ \"name\": \"John\" }")))`. The Spring application actually executes the real external network code, but FireMock captures it locally and returns your mock payload synchronously, ensuring resilient determinism.

### 58. How do you avoid `ApplicationContext` reloading and dramatically speed up `@SpringBootTest` execution?
**Answer:**
If you have 50 classes annotated with `@SpringBootTest`, Spring will try to re-use the same cached ApplicationContext to boot up instantly. This is the **Context Cache**.
However, if you dirty the context, Spring drops the entire JVM from memory and takes 15 seconds to rebuild it globally from scratch.
**Cache Busters (Avoid these where possible):**
- **`@MockBean` or `@SpyBean`**: Replacing a specific bean alters the context signature. Separate mock tests into a dedicated suite.
- **`@TestPropertySource`**: Injecting unique properties mid-test creates a unique configuration signature.
- **`@ActiveProfiles`**: Running tests under overlapping, shifting active profiles continuously invalidates the tree.
- **`@DirtiesContext`**: The explicit command to destroy the context; only use when you have irreversibly corrupted singleton state (e.g. wiped an in-memory DB or toggled a critical global static variable).

### 59. Best Practices for Testing Kafka Producers and Consumers in Spring Boot
**Answer:**
Instead of mocking the `KafkaTemplate` (which proves nothing about actual serialization), use an **Embedded Kafka Broker** or **Testcontainers**.
By adding `@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:9092" })` to your test class, Spring completely spins up a miniature embedded Apache Kafka cluster in the JVM. 
Your `@SpringBootTest` will naturally produce events into the embedded broker, and your `@KafkaListener` methods will naturally consume them. This verifies JSON payload serialization, Deserialization, topic bindings, auto-commit logic, and DLQ handling end-to-end without needing a running external Kafka instance.

### 60. How do you securely test an application heavily guarded by Spring Security without disabling it?
**Answer:**
If all your APIs require a JWT or an actively authenticated Session, your `@WebMvcTest` controllers will reject all test requests with a `401 Unauthorized` or `403 Forbidden` response.
**Implementation:**
Use the **`@WithMockUser`** annotation dynamically above the setup method.
`@WithMockUser(username = "admin", authorities = { "ROLE_ADMIN", "SCOPE_read" })`
This instructs the `SecurityContextHolder` to instantly populate an active `Authentication` principal right before the test executes, completely bypassing the actual Authentication Filter Chain, allowing you to test complex Authorization logic (`@PreAuthorize("hasRole('ADMIN')")`) elegantly and safely.

### 61. Property-Based Testing vs Example-Based Testing
**Answer:**
Standard JUnit testing is **Example-Based**. You write one specific test: `assertEquals(5, sum(2, 3))`.
**Property-Based Testing (using tools like jqwik):**
Instead of defining specific parameters, you define *invariants* (properties that must always be true) and allow the framework to generate 10,000 completely random edge-case payloads to try and break your logic automatically.
For example, instead of testing a JSON URL Encoder with a space character, you declare a property, and the framework assaults your method with Arabic numerals, nulls, special invisible characters, and emoticons simultaneously, heavily stressing data validation boundaries.

### 62. How do you test asynchronous background jobs (`@Async` / `@Scheduled`)?
**Answer:**
By their nature, `@Async` methods jump to a background thread. If you call the service method in a JUnit test, the test thread finishes and asserts the database before the background thread even wakes up.
**Implementation:**
Use the **Awaitility** library.
Instead of implementing unreliable `Thread.sleep(5000)`, Awaitility gracefully polls the assertion.
```java
await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
    assertEquals("COMPLETED", myService.getJobStatus("123"));
});
```
This forces the test to wait synchronously until the background Spring Thread safely finishes processing and updating the state mapping.

### 63. What are JUnit 5 Extensions?
**Answer:**
JUnit 4 used `@RunWith(SpringRunner.class)` or `Rule`. A class could only have one runner.
JUnit 5 transitioned to arguably its greatest feature: **Extensions**. 
You declare `@ExtendWith(SpringExtension.class)` to integrate the Spring TestContext Framework. Because it is highly modular, you can extend multiple plugins on the same class, e.g., `@ExtendWith({SpringExtension.class, MockitoExtension.class, TimingExtension.class})`. This gives your test class the simultaneous ability to load the Spring Application Context while completely managing highly isolated Mockito inline lifecycles gracefully.

### 64. Explain TDD vs BDD and how it's supported in Spring Testing
**Answer:**
- **TDD (Test-Driven Development):** You write failing Unit tests immediately *before* writing the java code. It focuses on the developer verifying logic correctness.
- **BDD (Behavior-Driven Development):** Extremely popular in enterprise logic validation. Instead of traditional Java code syntax, you write plain-English behavior scenarios using the **Gherkin syntax** (`Given a User is logged in, When they check out, Then deduct tax`).
In the Java ecosystem, **Cucumber** bridges the gap. The English lines automatically trigger specific Java methods annotated with `@Given`, `@When`, `@Then`. Within those generic methods, you invoke your Spring `@Service` beans or `TestRestTemplate` directly.

### 65. How do you analyze Test Coverage and why is 100% considered an anti-pattern?
**Answer:**
We analyze tests using tools like **JaCoCo**, which instruments the bytecode and generates an HTML report showing exactly which lines of code were completely ignored during the entire Maven build.
**100% Anti-Pattern:**
Forcing 100% test coverage heavily incentivizes writing "garbage" tests just to cover trivial Getters/Setters, un-throwable checked exception wrapper blocks, and auto-generated equals/hashCode methods. This massively bloats the test hierarchy and makes codebase refactoring impossible.
A high-quality standard focuses heavily on **Mutation Testing** (e.g. PIT or PITest), which proactively edits your source code (+ to -) and ensures the test suite actually fails, proving the tests are legitimately verifying business logic operations, not simply touching lines.
