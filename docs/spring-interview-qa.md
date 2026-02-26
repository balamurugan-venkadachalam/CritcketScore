# Spring Framework Interview Questions & Answers

## Core Spring Concepts

### 1. What is Dependency Injection and how does Spring implement it?

**Answer:**
Dependency Injection (DI) is a design pattern where objects receive their dependencies from external sources rather than creating them internally. Spring implements DI through:

- **Constructor Injection**: Dependencies passed through class constructors
- **Setter Injection**: Dependencies set through setter methods
- **Field Injection**: Dependencies injected directly into fields using @Autowired

```java
// Constructor Injection (Recommended)
@Service
public class UserService {
    private final UserRepository userRepository;
    
    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// Setter Injection
@Service
public class OrderService {
    private OrderRepository orderRepository;
    
    @Autowired
    public void setOrderRepository(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}

// Field Injection (Not recommended for testing)
@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;
}
```

**Benefits:**
- Loose coupling between components
- Easier unit testing with mock dependencies
- Better code maintainability
- Supports single responsibility principle

### 2. Explain the Spring Bean lifecycle

**Answer:**
The Spring Bean lifecycle consists of several phases:

1. **Instantiation**: Container creates bean instance
2. **Populate Properties**: Dependencies are injected
3. **BeanNameAware**: setBeanName() called if bean implements BeanNameAware
4. **BeanFactoryAware**: setBeanFactory() called if implements BeanFactoryAware
5. **ApplicationContextAware**: setApplicationContext() called if implements ApplicationContextAware
6. **Pre-Initialization**: BeanPostProcessor.postProcessBeforeInitialization()
7. **InitializingBean**: afterPropertiesSet() called if implements InitializingBean
8. **Custom Init Method**: Method specified in @Bean(initMethod="...")
9. **Post-Initialization**: BeanPostProcessor.postProcessAfterInitialization()
10. **Bean Ready**: Bean is ready for use
11. **Destruction**: DisposableBean.destroy() or custom destroy method

```java
@Component
public class LifecycleBean implements InitializingBean, DisposableBean {
    
    @PostConstruct
    public void postConstruct() {
        System.out.println("PostConstruct called");
    }
    
    @Override
    public void afterPropertiesSet() {
        System.out.println("InitializingBean afterPropertiesSet called");
    }
    
    @PreDestroy
    public void preDestroy() {
        System.out.println("PreDestroy called");
    }
    
    @Override
    public void destroy() {
        System.out.println("DisposableBean destroy called");
    }
}
```

### 3. What are the different bean scopes in Spring?

**Answer:**
Spring provides several bean scopes:

1. **Singleton (Default)**: One instance per Spring IoC container
2. **Prototype**: New instance created each time bean is requested
3. **Request**: One instance per HTTP request (Web applications)
4. **Session**: One instance per HTTP session (Web applications)
5. **Application**: One instance per ServletContext (Web applications)
6. **WebSocket**: One instance per WebSocket session

```java
// Singleton scope (default)
@Component
@Scope("singleton")
public class SingletonBean {
}

// Prototype scope
@Component
@Scope("prototype")
public class PrototypeBean {
}

// Request scope
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedBean {
}

// Session scope
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessionScopedBean {
}
```

### 4. What is the difference between @Component, @Service, @Repository, and @Controller?

**Answer:**
These are all stereotype annotations for component scanning, but serve different purposes:

- **@Component**: Generic stereotype for any Spring-managed component
- **@Service**: Specialization of @Component for service layer classes
- **@Repository**: Specialization of @Component for DAO layer, provides exception translation
- **@Controller**: Specialization of @Component for MVC controllers
- **@RestController**: Combines @Controller and @ResponseBody

```java
// Generic component
@Component
public class UtilityComponent {
}

// Service layer
@Service
public class UserService {
    public User findById(Long id) {
        // Business logic
    }
}

// Repository layer
@Repository
public class UserRepository {
    public User save(User user) {
        // Data access logic
    }
}

// MVC Controller
@Controller
public class UserController {
    @GetMapping("/users")
    public String getUsers(Model model) {
        return "users";
    }
}

// REST Controller
@RestController
@RequestMapping("/api/users")
public class UserRestController {
    @GetMapping
    public List<User> getUsers() {
        return userService.findAll();
    }
}
```

### 5. Explain Spring AOP and its use cases

**Answer:**
Aspect-Oriented Programming (AOP) allows separation of cross-cutting concerns from business logic.

**Key Concepts:**
- **Aspect**: Module that encapsulates cross-cutting concern
- **Join Point**: Point in program execution (method call, exception thrown)
- **Advice**: Action taken at a join point (before, after, around)
- **Pointcut**: Expression that matches join points
- **Weaving**: Process of applying aspects to target objects

```java
@Aspect
@Component
public class LoggingAspect {
    
    // Before advice
    @Before("execution(* com.example.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("Before method: " + joinPoint.getSignature().getName());
    }
    
    // After returning advice
    @AfterReturning(pointcut = "execution(* com.example.service.*.*(..))", 
                    returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        System.out.println("Method returned: " + result);
    }
    
    // Around advice
    @Around("execution(* com.example.service.*.*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - start;
        System.out.println("Execution time: " + executionTime + "ms");
        return result;
    }
    
    // After throwing advice
    @AfterThrowing(pointcut = "execution(* com.example.service.*.*(..))", 
                   throwing = "error")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable error) {
        System.out.println("Exception in: " + joinPoint.getSignature().getName());
    }
}
```

**Common Use Cases:**
- Logging and auditing
- Transaction management
- Security
- Performance monitoring
- Caching
- Error handling

### 6. What is Spring Boot and how does it differ from Spring Framework?

**Answer:**
Spring Boot is an opinionated framework built on top of Spring Framework that simplifies application development.

**Key Differences:**

| Spring Framework | Spring Boot |
|-----------------|-------------|
| Requires manual configuration | Auto-configuration |
| Need to configure web server | Embedded server (Tomcat, Jetty) |
| Manual dependency management | Starter dependencies |
| Requires deployment descriptor | Standalone JAR/WAR |
| More boilerplate code | Convention over configuration |

```java
// Traditional Spring Configuration
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "com.example")
public class WebConfig implements WebMvcConfigurer {
    
    @Bean
    public ViewResolver viewResolver() {
        InternalResourceViewResolver resolver = new InternalResourceViewResolver();
        resolver.setPrefix("/WEB-INF/views/");
        resolver.setSuffix(".jsp");
        return resolver;
    }
}

// Spring Boot - Auto-configured
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**Spring Boot Features:**
- Auto-configuration based on classpath
- Starter POMs for dependency management
- Embedded servers
- Production-ready features (metrics, health checks)
- No XML configuration required
- Spring Boot CLI for rapid development

### 7. Explain @Autowired and its alternatives

**Answer:**
@Autowired enables automatic dependency injection in Spring.

```java
// Field injection (not recommended)
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
}

// Constructor injection (recommended)
@Service
public class UserService {
    private final UserRepository userRepository;
    
    @Autowired // Optional in Spring 4.3+ for single constructor
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// Setter injection
@Service
public class UserService {
    private UserRepository userRepository;
    
    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// @Autowired with required=false
@Service
public class UserService {
    @Autowired(required = false)
    private OptionalService optionalService;
}

// Using Optional
@Service
public class UserService {
    @Autowired
    private Optional<OptionalService> optionalService;
}
```

**Alternatives:**

```java
// @Inject (JSR-330)
@Service
public class UserService {
    @Inject
    private UserRepository userRepository;
}

// @Resource (JSR-250) - by name
@Service
public class UserService {
    @Resource(name = "userRepository")
    private UserRepository userRepository;
}

// Constructor injection without @Autowired (Lombok)
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
}
```

### 8. What is Spring Data JPA and how does it work?

**Answer:**
Spring Data JPA simplifies data access layer implementation by providing repository abstractions.

```java
// Entity
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String username;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Order> orders;
}

// Repository interface
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Query method - Spring generates implementation
    User findByEmail(String email);
    
    List<User> findByUsernameContaining(String username);
    
    // Custom query with @Query
    @Query("SELECT u FROM User u WHERE u.email = :email")
    User findUserByEmail(@Param("email") String email);
    
    // Native query
    @Query(value = "SELECT * FROM users WHERE email = ?1", nativeQuery = true)
    User findByEmailNative(String email);
    
    // Modifying query
    @Modifying
    @Query("UPDATE User u SET u.username = :username WHERE u.id = :id")
    int updateUsername(@Param("id") Long id, @Param("username") String username);
    
    // Pagination and sorting
    Page<User> findByUsernameContaining(String username, Pageable pageable);
}

// Service usage
@Service
public class UserService {
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public Page<User> searchUsers(String username, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("username").ascending());
        return userRepository.findByUsernameContaining(username, pageable);
    }
}
```

**Key Features:**
- Automatic CRUD operations
- Query derivation from method names
- Custom queries with @Query
- Pagination and sorting support
- Auditing capabilities
- Specifications for dynamic queries

### 9. Explain Spring Transaction Management

**Answer:**
Spring provides declarative and programmatic transaction management.

```java
// Declarative transaction management
@Service
public class OrderService {
    
    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);
        inventoryService.updateStock(order.getItems());
        // If exception occurs, entire transaction rolls back
    }
    
    // Read-only transaction
    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }
    
    // Custom isolation level
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void criticalOperation() {
        // High isolation level for critical operations
    }
    
    // Custom propagation
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void independentTransaction() {
        // Always creates new transaction
    }
    
    // Rollback rules
    @Transactional(rollbackFor = Exception.class, 
                   noRollbackFor = ValidationException.class)
    public void processPayment(Payment payment) {
        // Custom rollback behavior
    }
    
    // Timeout
    @Transactional(timeout = 30)
    public void longRunningOperation() {
        // Transaction times out after 30 seconds
    }
}

// Programmatic transaction management
@Service
public class PaymentService {
    private final TransactionTemplate transactionTemplate;
    
    public PaymentService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }
    
    public void processPayment(Payment payment) {
        transactionTemplate.execute(status -> {
            try {
                paymentRepository.save(payment);
                accountService.debit(payment.getAmount());
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }
}
```

**Transaction Propagation Types:**
- REQUIRED (default): Use existing or create new
- REQUIRES_NEW: Always create new transaction
- SUPPORTS: Use existing if available, non-transactional otherwise
- NOT_SUPPORTED: Execute non-transactionally
- MANDATORY: Must have existing transaction
- NEVER: Must not have existing transaction
- NESTED: Execute within nested transaction

### 10. What is Spring WebFlux and when should you use it?

**Answer:**
Spring WebFlux is a reactive web framework for building non-blocking, asynchronous applications.

```java
// Traditional Spring MVC (blocking)
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id); // Blocks thread
    }
}

// Spring WebFlux (non-blocking)
@RestController
@RequestMapping("/api/users")
public class UserReactiveController {
    
    @GetMapping("/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return userService.findById(id); // Non-blocking
    }
    
    @GetMapping
    public Flux<User> getAllUsers() {
        return userService.findAll(); // Stream of users
    }
    
    @PostMapping
    public Mono<User> createUser(@RequestBody Mono<User> userMono) {
        return userMono.flatMap(userService::save);
    }
}

// Reactive repository
public interface UserReactiveRepository extends ReactiveCrudRepository<User, Long> {
    Flux<User> findByUsername(String username);
    Mono<User> findByEmail(String email);
}

// Reactive service
@Service
public class UserReactiveService {
    private final UserReactiveRepository repository;
    private final WebClient webClient;
    
    public Mono<User> findById(Long id) {
        return repository.findById(id);
    }
    
    public Flux<User> findAll() {
        return repository.findAll();
    }
    
    // Combining multiple reactive calls
    public Mono<UserProfile> getUserProfile(Long userId) {
        Mono<User> userMono = repository.findById(userId);
        Mono<List<Order>> ordersMono = webClient.get()
            .uri("/orders/user/" + userId)
            .retrieve()
            .bodyToFlux(Order.class)
            .collectList();
            
        return Mono.zip(userMono, ordersMono)
            .map(tuple -> new UserProfile(tuple.getT1(), tuple.getT2()));
    }
}
```

**When to use WebFlux:**
- High concurrency requirements
- Streaming data
- Microservices with many I/O operations
- Event-driven architectures
- Limited resources (threads)

**When NOT to use WebFlux:**
- Blocking dependencies (JDBC, JPA)
- Simple CRUD applications
- Team unfamiliar with reactive programming
- CPU-intensive operations

### 11. Explain Spring Profiles

**Answer:**
Spring Profiles allow environment-specific configuration.

```java
// Configuration class with profile
@Configuration
@Profile("dev")
public class DevConfig {
    
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
    }
}

@Configuration
@Profile("prod")
public class ProdConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getProperty("db.url"));
        config.setUsername(env.getProperty("db.username"));
        config.setPassword(env.getProperty("db.password"));
        return new HikariDataSource(config);
    }
}

// Component with profile
@Component
@Profile("!prod")
public class DevDataLoader implements CommandLineRunner {
    
    @Override
    public void run(String... args) {
        // Load test data in non-production environments
    }
}

// Multiple profiles
@Configuration
@Profile({"dev", "test"})
public class NonProdConfig {
}

// Profile expressions
@Configuration
@Profile("!prod & !staging")
public class LocalConfig {
}
```

**application.yml:**
```yaml
spring:
  profiles:
    active: dev

---
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:h2:mem:testdb
    
---
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:postgresql://prod-db:5432/mydb
```

**Activating profiles:**
```bash
# Command line
java -jar app.jar --spring.profiles.active=prod

# Environment variable
export SPRING_PROFILES_ACTIVE=prod

# Programmatically
SpringApplication app = new SpringApplication(Application.class);
app.setAdditionalProfiles("dev");
app.run(args);
```

### 12. What is Spring Cloud and its key components?

**Answer:**
Spring Cloud provides tools for building distributed systems and microservices.

**Key Components:**

1. **Service Discovery (Eureka)**
```java
// Eureka Server
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
}

// Eureka Client
@SpringBootApplication
@EnableDiscoveryClient
public class UserServiceApplication {
}
```

2. **API Gateway (Spring Cloud Gateway)**
```java
@Configuration
public class GatewayConfig {
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user-service", r -> r.path("/users/**")
                .filters(f -> f.addRequestHeader("X-Gateway", "true"))
                .uri("lb://USER-SERVICE"))
            .route("order-service", r -> r.path("/orders/**")
                .uri("lb://ORDER-SERVICE"))
            .build();
    }
}
```

3. **Circuit Breaker (Resilience4j)**
```java
@Service
public class UserService {
    
    @CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
    public User getUser(Long id) {
        return restTemplate.getForObject("http://user-service/users/" + id, User.class);
    }
    
    public User getUserFallback(Long id, Exception e) {
        return new User(id, "Fallback User");
    }
    
    @Retry(name = "userService")
    public User getUserWithRetry(Long id) {
        return restTemplate.getForObject("http://user-service/users/" + id, User.class);
    }
}
```

4. **Configuration Server**
```java
// Config Server
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
}

// Config Client
@SpringBootApplication
@RefreshScope
public class UserServiceApplication {
    
    @Value("${user.max-connections}")
    private int maxConnections;
}
```

5. **Load Balancing**
```java
@Configuration
public class LoadBalancerConfig {
    
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@Service
public class OrderService {
    private final RestTemplate restTemplate;
    
    public Order getOrder(Long id) {
        // Automatically load-balanced across instances
        return restTemplate.getForObject("http://ORDER-SERVICE/orders/" + id, Order.class);
    }
}
```
