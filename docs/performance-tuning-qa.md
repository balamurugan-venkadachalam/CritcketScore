# Performance Tuning Interview Questions & Answers

## Application Performance Optimization

### 1. What are the key areas to focus on for Spring Boot application performance tuning?

**Answer:**
Performance tuning in Spring Boot applications involves multiple layers:

**Key Areas:**
1. Database optimization (queries, indexing, connection pooling)
2. Caching strategies
3. JVM tuning
4. Thread pool configuration
5. API response optimization
6. Resource management
7. Monitoring and profiling

```java
// Application performance configuration
@Configuration
public class PerformanceConfig {
    
    // Connection pool tuning
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Performance settings
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        return new HikariDataSource(config);
    }
    
    // Thread pool configuration
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    // HTTP client connection pool
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        
        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(20);
        
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build();
        factory.setHttpClient(httpClient);
        
        return new RestTemplate(factory);
    }
}

// application.yml performance settings
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
        generate_statistics: false
    show-sql: false
  
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  
  jackson:
    default-property-inclusion: non_null
  
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    max-connections: 10000
    accept-count: 100
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain
```

### 2. How do you optimize database queries and use proper indexing?

**Answer:**

```java
// Entity with proper indexing
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_created_date", columnList = "created_date"),
    @Index(name = "idx_status_created", columnList = "status, created_date")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String username;
    
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    
    @Column(name = "created_date")
    private LocalDateTime createdDate;
}

// Optimized queries
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Use specific fields instead of SELECT *
    @Query("SELECT new com.example.dto.UserDTO(u.id, u.username, u.email) " +
           "FROM User u WHERE u.status = :status")
    List<UserDTO> findActiveUsersOptimized(@Param("status") UserStatus status);
    
    // Use JOIN FETCH to avoid N+1 problem
    @Query("SELECT u FROM User u " +
           "LEFT JOIN FETCH u.roles " +
           "WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);
    
    // Pagination for large datasets
    @Query("SELECT u FROM User u WHERE u.status = :status")
    Page<User> findByStatus(@Param("status") UserStatus status, Pageable pageable);
    
    // Use native query for complex operations
    @Query(value = "SELECT * FROM users u " +
                   "WHERE u.created_date > :date " +
                   "AND u.status = :status " +
                   "ORDER BY u.created_date DESC " +
                   "LIMIT :limit", 
           nativeQuery = true)
    List<User> findRecentUsers(@Param("date") LocalDateTime date,
                               @Param("status") String status,
                               @Param("limit") int limit);
    
    // Batch operations
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id IN :ids")
    int updateStatusBatch(@Param("ids") List<Long> ids, 
                         @Param("status") UserStatus status);
}

// Service with query optimization
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    
    // Avoid N+1 queries
    @Transactional(readOnly = true)
    public List<UserDTO> getUsersWithRoles() {
        return entityManager.createQuery(
            "SELECT new com.example.dto.UserDTO(u.id, u.username, r.name) " +
            "FROM User u JOIN u.roles r", 
            UserDTO.class)
            .getResultList();
    }
    
    // Batch insert
    @Transactional
    public void batchInsertUsers(List<User> users) {
        int batchSize = 20;
        for (int i = 0; i < users.size(); i++) {
            entityManager.persist(users.get(i));
            if (i % batchSize == 0 && i > 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
    
    // Use projection for read-only queries
    @Transactional(readOnly = true)
    public List<UserProjection> getUserProjections() {
        return userRepository.findAllProjectedBy();
    }
    
    // Streaming large result sets
    @Transactional(readOnly = true)
    public void processLargeDataset() {
        try (Stream<User> userStream = userRepository.streamAllBy()) {
            userStream.forEach(user -> {
                // Process each user
                processUser(user);
            });
        }
    }
}

// Projection interface
public interface UserProjection {
    Long getId();
    String getUsername();
    String getEmail();
}

// DTO for optimized queries
@Data
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String email;
}

// Query hints for performance
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "50"))
    @Query("SELECT o FROM Order o WHERE o.status = :status")
    List<Order> findByStatusWithHint(@Param("status") OrderStatus status);
    
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdCacheable(@Param("id") Long id);
}
```

### 3. How do you implement caching strategies in Spring Boot?

**Answer:**

```java
// Cache configuration
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "users", "products", "orders"
        );
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats();
    }
    
    // Redis cache configuration
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();
        
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("users", config.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("products", config.entryTtl(Duration.ofHours(1)));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}

// Service with caching
@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    // Cache result
    @Cacheable(value = "users", key = "#id")
    public User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }
    
    // Cache with condition
    @Cacheable(value = "users", key = "#username", 
               condition = "#username.length() > 3")
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));
    }
    
    // Cache with unless
    @Cacheable(value = "users", key = "#email", 
               unless = "#result == null")
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    // Update cache
    @CachePut(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }
    
    // Evict cache
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    
    // Evict all cache entries
    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsers() {
        // Clear operation
    }
    
    // Multiple cache operations
    @Caching(
        cacheable = @Cacheable(value = "users", key = "#id"),
        put = @CachePut(value = "userDetails", key = "#id")
    )
    public User getUserWithDetails(Long id) {
        return userRepository.findByIdWithDetails(id);
    }
}

// Custom cache key generator
@Component
public class CustomCacheKeyGenerator implements KeyGenerator {
    
    @Override
    public Object generate(Object target, Method method, Object... params) {
        return target.getClass().getSimpleName() + "_" +
               method.getName() + "_" +
               Arrays.stream(params)
                   .map(Object::toString)
                   .collect(Collectors.joining("_"));
    }
}

@Service
public class ProductService {
    
    @Cacheable(value = "products", keyGenerator = "customCacheKeyGenerator")
    public Product findProduct(Long id, String category) {
        return productRepository.findByIdAndCategory(id, category);
    }
}

// Cache warming
@Component
public class CacheWarmer implements ApplicationRunner {
    
    private final UserService userService;
    private final CacheManager cacheManager;
    
    @Override
    public void run(ApplicationArguments args) {
        warmUpUserCache();
    }
    
    private void warmUpUserCache() {
        List<User> popularUsers = userRepository.findPopularUsers();
        popularUsers.forEach(user -> {
            Cache cache = cacheManager.getCache("users");
            if (cache != null) {
                cache.put(user.getId(), user);
            }
        });
    }
}

// Cache statistics
@RestController
@RequestMapping("/api/cache")
public class CacheController {
    
    private final CacheManager cacheManager;
    
    @GetMapping("/stats")
    public Map<String, CacheStats> getCacheStats() {
        Map<String, CacheStats> stats = new HashMap<>();
        
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) cache;
                com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = 
                    caffeineCache.getNativeCache();
                stats.put(cacheName, nativeCache.stats());
            }
        });
        
        return stats;
    }
    
    @DeleteMapping("/{cacheName}")
    public void evictCache(@PathVariable String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
```

### 4. How do you optimize JVM performance for Spring Boot applications?

**Answer:**

```bash
# JVM tuning parameters

# Heap size configuration
java -Xms2g -Xmx4g \
     -XX:MetaspaceSize=256m \
     -XX:MaxMetaspaceSize=512m \
     -jar application.jar

# Garbage collection tuning (G1GC - recommended for most cases)
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:G1HeapRegionSize=16m \
     -XX:InitiatingHeapOccupancyPercent=45 \
     -XX:G1ReservePercent=10 \
     -jar application.jar

# ZGC for low-latency applications (Java 15+)
java -Xms4g -Xmx4g \
     -XX:+UseZGC \
     -XX:ZCollectionInterval=5 \
     -jar application.jar

# GC logging
java -Xms2g -Xmx4g \
     -Xlog:gc*:file=gc.log:time,uptime,level,tags \
     -XX:+UseG1GC \
     -jar application.jar

# Performance monitoring
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -XX:+PrintGCTimeStamps \
     -XX:+PrintGCApplicationStoppedTime \
     -XX:+PrintTenuringDistribution \
     -Xloggc:gc.log \
     -jar application.jar

# JMX monitoring
java -Xms2g -Xmx4g \
     -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar application.jar

# String deduplication (reduces memory for duplicate strings)
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:+UseStringDeduplication \
     -jar application.jar

# Compressed OOPs (Ordinary Object Pointers) - enabled by default for heap < 32GB
java -Xms2g -Xmx4g \
     -XX:+UseCompressedOops \
     -jar application.jar

# Production-ready JVM settings
java -server \
     -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     -XX:+ParallelRefProcEnabled \
     -XX:+AlwaysPreTouch \
     -XX:+DisableExplicitGC \
     -Xlog:gc*:file=gc.log:time,uptime,level,tags \
     -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar application.jar
```

```yaml
# application.yml JVM-related settings
spring:
  application:
    name: myapp
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,heapdump,threaddump
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
  
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
```

```java
// Memory-efficient coding practices
@Service
public class MemoryOptimizedService {
    
    // Use primitive types when possible
    public long calculateSum(List<Long> numbers) {
        long sum = 0; // primitive instead of Long
        for (long num : numbers) {
            sum += num;
        }
        return sum;
    }
    
    // Use StringBuilder for string concatenation
    public String buildMessage(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(part);
        }
        return sb.toString();
    }
    
    // Close resources properly
    public void processFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    // Use lazy loading
    @OneToMany(fetch = FetchType.LAZY)
    private List<Order> orders;
    
    // Avoid memory leaks with weak references
    private final Map<String, WeakReference<User>> userCache = new WeakHashMap<>();
    
    public User getCachedUser(String id) {
        WeakReference<User> ref = userCache.get(id);
        if (ref != null) {
            User user = ref.get();
            if (user != null) {
                return user;
            }
        }
        
        User user = loadUser(id);
        userCache.put(id, new WeakReference<>(user));
        return user;
    }
}
```

### 5. How do you implement and optimize asynchronous processing?

**Answer:**

```java
// Async configuration
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }
    
    // Multiple thread pools for different tasks
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
    
    @Bean(name = "reportExecutor")
    public Executor reportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("report-");
        executor.initialize();
        return executor;
    }
}

// Async service
@Service
public class AsyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncService.class);
    
    // Simple async method
    @Async
    public void processAsync(String data) {
        logger.info("Processing data asynchronously: {}", data);
        // Long-running operation
    }
    
    // Async method with return value
    @Async
    public CompletableFuture<String> processWithResult(String data) {
        try {
            Thread.sleep(1000);
            String result = "Processed: " + data;
            return CompletableFuture.completedFuture(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // Using specific executor
    @Async("emailExecutor")
    public void sendEmailAsync(String to, String subject, String body) {
        logger.info("Sending email to: {}", to);
        // Email sending logic
    }
    
    // Combining multiple async operations
    public CompletableFuture<OrderSummary> processOrder(Long orderId) {
        CompletableFuture<Order> orderFuture = getOrderAsync(orderId);
        CompletableFuture<Customer> customerFuture = getCustomerAsync(orderId);
        CompletableFuture<List<Product>> productsFuture = getProductsAsync(orderId);
        
        return CompletableFuture.allOf(orderFuture, customerFuture, productsFuture)
            .thenApply(v -> {
                Order order = orderFuture.join();
                Customer customer = customerFuture.join();
                List<Product> products = productsFuture.join();
                return new OrderSummary(order, customer, products);
            });
    }
    
    @Async
    public CompletableFuture<Order> getOrderAsync(Long orderId) {
        return CompletableFuture.completedFuture(orderRepository.findById(orderId).orElse(null));
    }
    
    @Async
    public CompletableFuture<Customer> getCustomerAsync(Long orderId) {
        return CompletableFuture.completedFuture(customerRepository.findByOrderId(orderId));
    }
    
    @Async
    public CompletableFuture<List<Product>> getProductsAsync(Long orderId) {
        return CompletableFuture.completedFuture(productRepository.findByOrderId(orderId));
    }
}

// Custom exception handler
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomAsyncExceptionHandler.class);
    
    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        logger.error("Async method {} threw exception: {}", 
                    method.getName(), ex.getMessage(), ex);
        // Send alert, log to monitoring system, etc.
    }
}

// Controller using async processing
@RestController
@RequestMapping("/api/async")
public class AsyncController {
    
    private final AsyncService asyncService;
    
    @PostMapping("/process")
    public DeferredResult<String> processAsync(@RequestBody String data) {
        DeferredResult<String> deferredResult = new DeferredResult<>(5000L);
        
        asyncService.processWithResult(data)
            .thenAccept(deferredResult::setResult)
            .exceptionally(ex -> {
                deferredResult.setErrorResult(ex);
                return null;
            });
        
        deferredResult.onTimeout(() -> 
            deferredResult.setErrorResult("Request timeout"));
        
        return deferredResult;
    }
    
    @GetMapping("/order/{id}")
    public CompletableFuture<OrderSummary> getOrderSummary(@PathVariable Long id) {
        return asyncService.processOrder(id);
    }
}

// Reactive approach with WebFlux
@Service
public class ReactiveService {
    
    private final WebClient webClient;
    
    public Mono<User> getUserReactive(Long id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .bodyToMono(User.class)
            .timeout(Duration.ofSeconds(5))
            .retry(3);
    }
    
    public Flux<User> getAllUsersReactive() {
        return webClient.get()
            .uri("/users")
            .retrieve()
            .bodyToFlux(User.class);
    }
    
    // Parallel processing with Flux
    public Flux<ProcessedData> processInParallel(List<String> data) {
        return Flux.fromIterable(data)
            .parallel()
            .runOn(Schedulers.parallel())
            .map(this::processData)
            .sequential();
    }
}
```

### 6. How do you monitor and profile Spring Boot applications?

**Answer:**

```java
// Actuator configuration
@Configuration
public class ActuatorConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "myapp")
            .commonTags("environment", environment);
    }
}

// Custom metrics
@Service
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    private final Counter orderCounter;
    private final Timer orderProcessingTimer;
    private final Gauge activeUsersGauge;
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Counter
        this.orderCounter = Counter.builder("orders.created")
            .description("Total orders created")
            .tag("type", "online")
            .register(meterRegistry);
        
        // Timer
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("Order processing time")
            .register(meterRegistry);
        
        // Gauge
        this.activeUsersGauge = Gauge.builder("users.active", this, MetricsService::getActiveUserCount)
            .description("Active users count")
            .register(meterRegistry);
    }
    
    public void recordOrderCreated() {
        orderCounter.increment();
    }
    
    public void recordOrderProcessing(Runnable task) {
        orderProcessingTimer.record(task);
    }
    
    public <T> T recordOrderProcessingWithResult(Supplier<T> task) {
        return orderProcessingTimer.record(task);
    }
    
    private double getActiveUserCount() {
        // Logic to get active user count
        return 0.0;
    }
    
    // Distribution summary for percentiles
    public void recordOrderAmount(double amount) {
        DistributionSummary.builder("orders.amount")
            .description("Order amounts")
            .baseUnit("USD")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(amount);
    }
}

// Custom health indicator
@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    private final ExternalService externalService;
    
    @Override
    public Health health() {
        try {
            boolean isHealthy = externalService.ping();
            if (isHealthy) {
                return Health.up()
                    .withDetail("service", "External Service")
                    .withDetail("status", "Available")
                    .build();
            } else {
                return Health.down()
                    .withDetail("service", "External Service")
                    .withDetail("status", "Unavailable")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

// Performance monitoring aspect
@Aspect
@Component
public class PerformanceMonitoringAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringAspect.class);
    private final MeterRegistry meterRegistry;
    
    @Around("@annotation(com.example.annotation.Monitored)")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Timer timer = Timer.builder("method.execution.time")
            .tag("method", methodName)
            .register(meterRegistry);
        
        return timer.record(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }
}

// Custom annotation for monitoring
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Monitored {
}

// Service with monitoring
@Service
public class OrderService {
    
    private final MetricsService metricsService;
    
    @Monitored
    public Order createOrder(OrderRequest request) {
        return metricsService.recordOrderProcessingWithResult(() -> {
            Order order = processOrder(request);
            metricsService.recordOrderCreated();
            metricsService.recordOrderAmount(order.getTotalAmount());
            return order;
        });
    }
}

// Prometheus configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      sla:
        http.server.requests: 50ms,100ms,200ms,500ms,1s

// Logging configuration for performance
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.springframework.web: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

```bash
# Profiling with JProfiler/YourKit
java -agentpath:/path/to/jprofiler/bin/linux-x64/libjprofilerti.so=port=8849 \
     -jar application.jar

# Profiling with async-profiler
java -agentpath:/path/to/async-profiler/build/libasyncProfiler.so=start,event=cpu,file=profile.html \
     -jar application.jar

# Flight Recorder
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
     -jar application.jar

# Heap dump on OutOfMemoryError
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/log/heapdump.hprof \
     -jar application.jar
```

### 7. How do you optimize API response times?

**Answer:**

```java
// Response compression
@Configuration
public class CompressionConfig {
    
    @Bean
    public FilterRegistrationBean<GzipFilter> gzipFilter() {
        FilterRegistrationBean<GzipFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new GzipFilter());
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}

// Pagination for large datasets
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private final UserService userService;
    
    @GetMapping
    public Page<UserDTO> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return userService.findAll(pageable);
    }
    
    // Cursor-based pagination for better performance
    @GetMapping("/cursor")
    public CursorPage<UserDTO> getUsersCursor(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        
        return userService.findAllByCursor(cursor, size);
    }
}

// DTO projection to reduce payload size
@Service
public class UserService {
    
    // Return only required fields
    public List<UserSummaryDTO> getUserSummaries() {
        return userRepository.findAll().stream()
            .map(user -> new UserSummaryDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail()
            ))
            .collect(Collectors.toList());
    }
    
    // Use projections
    public List<UserProjection> getUserProjections() {
        return userRepository.findAllProjectedBy();
    }
}

// Lazy loading and field filtering
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @GetMapping("/{id}")
    public ProductDTO getProduct(
            @PathVariable Long id,
            @RequestParam(required = false) String fields) {
        
        Product product = productService.findById(id);
        
        if (fields != null) {
            return filterFields(product, fields.split(","));
        }
        
        return new ProductDTO(product);
    }
    
    private ProductDTO filterFields(Product product, String[] fields) {
        // Return only requested fields
        ProductDTO dto = new ProductDTO();
        for (String field : fields) {
            switch (field) {
                case "id" -> dto.setId(product.getId());
                case "name" -> dto.setName(product.getName());
                case "price" -> dto.setPrice(product.getPrice());
            }
        }
        return dto;
    }
}

// HTTP/2 support
server:
  http2:
    enabled: true
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain
    min-response-size: 1024

// Async controller for non-blocking responses
@RestController
@RequestMapping("/api/async")
public class AsyncApiController {
    
    @GetMapping("/users/{id}")
    public CompletableFuture<UserDTO> getUserAsync(@PathVariable Long id) {
        return userService.findByIdAsync(id);
    }
    
    @GetMapping("/stream")
    public ResponseBodyEmitter streamData() {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        
        executor.execute(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    emitter.send("Data " + i + "\n");
                    Thread.sleep(100);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    @GetMapping("/sse")
    public SseEmitter streamEvents() {
        SseEmitter emitter = new SseEmitter();
        
        executor.execute(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    emitter.send(SseEmitter.event()
                        .name("message")
                        .data("Event " + i));
                    Thread.sleep(1000);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
}

// Response caching headers
@RestController
@RequestMapping("/api/public")
public class PublicApiController {
    
    @GetMapping("/config")
    public ResponseEntity<ConfigDTO> getConfig() {
        ConfigDTO config = configService.getConfig();
        
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
            .eTag(String.valueOf(config.hashCode()))
            .body(config);
    }
    
    @GetMapping("/data")
    public ResponseEntity<DataDTO> getData(@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        DataDTO data = dataService.getData();
        String etag = String.valueOf(data.hashCode());
        
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        
        return ResponseEntity.ok()
            .eTag(etag)
            .body(data);
    }
}

// GraphQL for flexible queries
@Controller
public class GraphQLController {
    
    @QueryMapping
    public User user(@Argument Long id, DataFetchingEnvironment environment) {
        // Only fetch requested fields
        DataFetchingFieldSelectionSet selectionSet = environment.getSelectionSet();
        return userService.findById(id, selectionSet.getFields());
    }
}
```

### 8. How do you handle connection pooling and optimize database connections?

**Answer:**

```java
// HikariCP configuration (recommended)
@Configuration
public class DatabaseConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // Pool sizing
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        
        // Connection management
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // Connection testing
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(3000);
        
        return config;
    }
    
    @Bean
    public DataSource dataSource(HikariConfig hikariConfig) {
        return new HikariDataSource(hikariConfig);
    }
}

// Multiple datasource configuration
@Configuration
public class MultipleDataSourceConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @Primary
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
    
    @Bean
    public JdbcTemplate secondaryJdbcTemplate(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

// Read-write splitting
@Configuration
public class ReadWriteDataSourceConfig {
    
    @Bean
    public DataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource) {
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.WRITE, writeDataSource);
        targetDataSources.put(DataSourceType.READ, readDataSource);
        
        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        
        return routingDataSource;
    }
}

public class RoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSourceType();
    }
}

public class DataSourceContextHolder {
    
    private static final ThreadLocal<DataSourceType> contextHolder = new ThreadLocal<>();
    
    public static void setDataSourceType(DataSourceType type) {
        contextHolder.set(type);
    }
    
    public static DataSourceType getDataSourceType() {
        return contextHolder.get();
    }
    
    public static void clearDataSourceType() {
        contextHolder.remove();
    }
}

// Transaction management with read-write splitting
@Service
public class UserService {
    
    @Transactional
    public User createUser(User user) {
        DataSourceContextHolder.setDataSourceType(DataSourceType.WRITE);
        try {
            return userRepository.save(user);
        } finally {
            DataSourceContextHolder.clearDataSourceType();
        }
    }
    
    @Transactional(readOnly = true)
    public User findById(Long id) {
        DataSourceContextHolder.setDataSourceType(DataSourceType.READ);
        try {
            return userRepository.findById(id).orElseThrow();
        } finally {
            DataSourceContextHolder.clearDataSourceType();
        }
    }
}

// Connection pool monitoring
@Component
public class DataSourceMonitor {
    
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    
    @PostConstruct
    public void init() {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
            
            Gauge.builder("hikari.connections.active", poolMXBean, HikariPoolMXBean::getActiveConnections)
                .register(meterRegistry);
            
            Gauge.builder("hikari.connections.idle", poolMXBean, HikariPoolMXBean::getIdleConnections)
                .register(meterRegistry);
            
            Gauge.builder("hikari.connections.total", poolMXBean, HikariPoolMXBean::getTotalConnections)
                .register(meterRegistry);
            
            Gauge.builder("hikari.connections.pending", poolMXBean, HikariPoolMXBean::getThreadsAwaitingConnection)
                .register(meterRegistry);
        }
    }
}

// application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      pool-name: MyHikariPool
      
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
```

## Summary

Successfully created 4 comprehensive interview Q&A documents:

1. **`@/Users/bala/code/cursor/CrickeScore/docs/spring-interview-qa.md`** - 12 Spring Framework questions covering DI, bean lifecycle, scopes, AOP, Spring Boot, transactions, WebFlux, profiles, and Spring Cloud

2. **`@/Users/bala/code/cursor/CrickeScore/docs/security-interview-qa.md`** - 12 Security questions covering Spring Security architecture, authentication, JWT, OAuth2, CSRF, password encoding, Remember-Me, session management, RBAC, and REST API security

3. **`@/Users/bala/code/cursor/CrickeScore/docs/testing-interview-qa.md`** - 12 Testing questions covering unit testing, Mockito, Spring Boot testing, JPA testing, MockMvc, Security testing, async testing, exception handling, test coverage, and Testcontainers

4. **`@/Users/bala/code/cursor/CrickeScore/docs/performance-tuning-qa.md`** - 8 Performance Tuning questions covering application optimization, database queries, caching, JVM tuning, async processing, monitoring, API optimization, and connection pooling

All files include detailed code examples, best practices, and production-ready configurations.
