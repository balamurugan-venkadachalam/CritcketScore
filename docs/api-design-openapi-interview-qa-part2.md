# API Design & OpenAPI Interview Questions & Answers - Part 2 (Questions 8-25)

## API Versioning

### 8. What are the different API versioning strategies and their pros/cons?

**Answer:**

```java
// 1. URI Versioning (Most Common)
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {
    @GetMapping("/{id}")
    public ResponseEntity<UserV1DTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findByIdV1(id));
    }
}

@RestController
@RequestMapping("/api/v2/users")
public class UserV2Controller {
    @GetMapping("/{id}")
    public ResponseEntity<UserV2DTO> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findByIdV2(id));
    }
}

// 2. Header Versioning
@RestController
@RequestMapping("/api/users")
public class UserHeaderVersionController {
    
    @GetMapping(value = "/{id}", headers = "API-Version=1")
    public ResponseEntity<UserV1DTO> getUserV1(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findByIdV1(id));
    }
    
    @GetMapping(value = "/{id}", headers = "API-Version=2")
    public ResponseEntity<UserV2DTO> getUserV2(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findByIdV2(id));
    }
}

// 3. Accept Header Versioning (Content Negotiation)
@RestController
@RequestMapping("/api/users")
public class UserContentNegotiationController {
    
    @GetMapping(value = "/{id}", produces = "application/vnd.company.v1+json")
    public ResponseEntity<UserV1DTO> getUserV1(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findByIdV1(id));
    }
    
    @GetMapping(value = "/{id}", produces = "application/vnd.company.v2+json")
    public ResponseEntity<UserV2DTO> getUserV2(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findByIdV2(id));
    }
}

// 4. Query Parameter Versioning
@RestController
@RequestMapping("/api/users")
public class UserQueryVersionController {
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int version) {
        
        if (version == 2) {
            return ResponseEntity.ok(userService.findByIdV2(id));
        }
        return ResponseEntity.ok(userService.findByIdV1(id));
    }
}

// Version-agnostic service layer
@Service
public class UserService {
    
    public UserV1DTO findByIdV1(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        return mapToV1DTO(user);
    }
    
    public UserV2DTO findByIdV2(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        return mapToV2DTO(user);
    }
    
    private UserV1DTO mapToV1DTO(User user) {
        // V1 format: separate firstName and lastName
        return UserV1DTO.builder()
            .id(user.getId())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .email(user.getEmail())
            .build();
    }
    
    private UserV2DTO mapToV2DTO(User user) {
        // V2 format: combined fullName
        return UserV2DTO.builder()
            .id(user.getId())
            .fullName(user.getFirstName() + " " + user.getLastName())
            .email(user.getEmail())
            .phoneNumber(user.getPhoneNumber()) // New field in V2
            .build();
    }
}
```

**Comparison:**

| Strategy | Pros | Cons | Use Case |
|----------|------|------|----------|
| **URI Versioning** | Clear, easy to understand, cacheable | URL pollution, not RESTful | Public APIs, major changes |
| **Header Versioning** | Clean URLs, RESTful | Not visible in browser, harder to test | Internal APIs |
| **Accept Header** | Most RESTful, clean URLs | Complex, harder to test | APIs with many content types |
| **Query Parameter** | Easy to test, optional | Not RESTful, can be ignored | Minor versions, feature flags |

**Best Practices:**

```java
// Deprecation strategy
@RestController
@RequestMapping("/api/v1/users")
@Deprecated
public class UserV1Controller {
    
    @GetMapping("/{id}")
    public ResponseEntity<UserV1DTO> getUser(@PathVariable Long id, HttpServletResponse response) {
        // Add deprecation headers
        response.setHeader("X-API-Deprecated", "true");
        response.setHeader("X-API-Deprecation-Date", "2024-12-31");
        response.setHeader("X-API-Sunset-Date", "2025-06-30");
        response.setHeader("Link", "</api/v2/users>; rel=\"successor-version\"");
        
        return ResponseEntity.ok(userService.findByIdV1(id));
    }
}

// Version negotiation
@Component
public class ApiVersionInterceptor implements HandlerInterceptor {
    
    private static final String DEFAULT_VERSION = "1";
    private static final String LATEST_VERSION = "2";
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        
        String version = extractVersion(request);
        request.setAttribute("apiVersion", version);
        
        // Add version info to response headers
        response.setHeader("X-API-Version", version);
        response.setHeader("X-API-Latest-Version", LATEST_VERSION);
        
        return true;
    }
    
    private String extractVersion(HttpServletRequest request) {
        // Try header first
        String headerVersion = request.getHeader("API-Version");
        if (headerVersion != null) {
            return headerVersion;
        }
        
        // Try Accept header
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("vnd.company.v")) {
            return extractVersionFromAccept(accept);
        }
        
        // Try query parameter
        String queryVersion = request.getParameter("version");
        if (queryVersion != null) {
            return queryVersion;
        }
        
        return DEFAULT_VERSION;
    }
}
```

### 9. How do you handle breaking changes in APIs?

**Answer:**

```java
// Strategy 1: Parallel Versions
@RestController
public class ParallelVersionController {
    
    // V1: Original structure
    @GetMapping("/api/v1/orders/{id}")
    public ResponseEntity<OrderV1Response> getOrderV1(@PathVariable Long id) {
        Order order = orderService.findById(id);
        return ResponseEntity.ok(OrderV1Response.from(order));
    }
    
    // V2: Breaking change - different structure
    @GetMapping("/api/v2/orders/{id}")
    public ResponseEntity<OrderV2Response> getOrderV2(@PathVariable Long id) {
        Order order = orderService.findById(id);
        return ResponseEntity.ok(OrderV2Response.from(order));
    }
}

// V1 Response: Flat structure
@Data
public class OrderV1Response {
    private Long id;
    private String customerName;
    private String customerEmail;
    private BigDecimal totalAmount;
    private String status;
    
    public static OrderV1Response from(Order order) {
        OrderV1Response response = new OrderV1Response();
        response.setId(order.getId());
        response.setCustomerName(order.getCustomer().getName());
        response.setCustomerEmail(order.getCustomer().getEmail());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        return response;
    }
}

// V2 Response: Nested structure (breaking change)
@Data
public class OrderV2Response {
    private Long id;
    private CustomerInfo customer;
    private OrderDetails details;
    
    @Data
    public static class CustomerInfo {
        private Long id;
        private String name;
        private String email;
    }
    
    @Data
    public static class OrderDetails {
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createdAt;
        private List<OrderItem> items;
    }
    
    public static OrderV2Response from(Order order) {
        OrderV2Response response = new OrderV2Response();
        response.setId(order.getId());
        
        CustomerInfo customer = new CustomerInfo();
        customer.setId(order.getCustomer().getId());
        customer.setName(order.getCustomer().getName());
        customer.setEmail(order.getCustomer().getEmail());
        response.setCustomer(customer);
        
        OrderDetails details = new OrderDetails();
        details.setTotalAmount(order.getTotalAmount());
        details.setStatus(order.getStatus());
        details.setCreatedAt(order.getCreatedAt());
        details.setItems(order.getItems());
        response.setDetails(details);
        
        return response;
    }
}

// Strategy 2: Feature Flags
@RestController
@RequestMapping("/api/orders")
public class FeatureFlagController {
    
    @Autowired
    private FeatureFlagService featureFlagService;
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        
        Order order = orderService.findById(id);
        
        // Check if user has access to new format
        if (featureFlagService.isEnabled("new-order-format", userId)) {
            return ResponseEntity.ok(OrderV2Response.from(order));
        }
        
        return ResponseEntity.ok(OrderV1Response.from(order));
    }
}

// Strategy 3: Gradual Migration with Adapter Pattern
@Service
public class OrderResponseAdapter {
    
    public Object adaptResponse(Order order, String version, String userSegment) {
        // Gradual rollout: 10% of users get V2
        if ("2".equals(version) || shouldUseV2(userSegment)) {
            return OrderV2Response.from(order);
        }
        return OrderV1Response.from(order);
    }
    
    private boolean shouldUseV2(String userSegment) {
        // Gradual rollout logic
        if (userSegment == null) return false;
        
        int hash = Math.abs(userSegment.hashCode());
        return (hash % 100) < 10; // 10% rollout
    }
}

// Strategy 4: Sunset Policy
@RestController
@RequestMapping("/api/v1/products")
public class SunsetController {
    
    private static final LocalDate SUNSET_DATE = LocalDate.of(2025, 6, 30);
    
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable Long id) {
        // Check if API is past sunset date
        if (LocalDate.now().isAfter(SUNSET_DATE)) {
            return ResponseEntity
                .status(HttpStatus.GONE)
                .header("X-API-Sunset", "This API version has been sunset")
                .header("Link", "</api/v2/products>; rel=\"successor-version\"")
                .build();
        }
        
        // Add sunset warnings
        long daysUntilSunset = ChronoUnit.DAYS.between(LocalDate.now(), SUNSET_DATE);
        
        ProductDTO product = productService.findById(id);
        
        return ResponseEntity.ok()
            .header("X-API-Deprecated", "true")
            .header("X-API-Sunset-Date", SUNSET_DATE.toString())
            .header("X-API-Days-Until-Sunset", String.valueOf(daysUntilSunset))
            .header("Link", "</api/v2/products>; rel=\"successor-version\"")
            .body(product);
    }
}

// Migration guide endpoint
@RestController
@RequestMapping("/api/migration")
public class MigrationGuideController {
    
    @GetMapping("/v1-to-v2")
    public ResponseEntity<MigrationGuide> getMigrationGuide() {
        MigrationGuide guide = MigrationGuide.builder()
            .fromVersion("v1")
            .toVersion("v2")
            .breakingChanges(Arrays.asList(
                "Order response structure changed from flat to nested",
                "Customer information moved to 'customer' object",
                "Order details moved to 'details' object"
            ))
            .migrationSteps(Arrays.asList(
                "Update client to handle nested 'customer' object",
                "Update client to handle nested 'details' object",
                "Test with both v1 and v2 endpoints",
                "Switch to v2 endpoint"
            ))
            .examples(Map.of(
                "v1", "{ \"customerName\": \"John\", \"totalAmount\": 100 }",
                "v2", "{ \"customer\": { \"name\": \"John\" }, \"details\": { \"totalAmount\": 100 } }"
            ))
            .build();
        
        return ResponseEntity.ok(guide);
    }
}
```

---

## Security & Authentication

### 10. How do you implement API authentication and authorization?

**Answer:**

```java
// 1. JWT Authentication
@Configuration
@EnableWebSecurity
public class JwtSecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) 
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private UserDetailsService userDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String token = extractToken(request);
        
        if (token != null && tokenProvider.validateToken(token)) {
            String username = tokenProvider.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

// 2. API Key Authentication
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    
    @Autowired
    private ApiKeyService apiKeyService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String apiKey = request.getHeader("X-API-Key");
        
        if (apiKey != null) {
            Optional<ApiKeyDetails> details = apiKeyService.validateApiKey(apiKey);
            
            if (details.isPresent()) {
                ApiKeyDetails keyDetails = details.get();
                
                // Check rate limits
                if (!rateLimitService.checkLimit(apiKey)) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    return;
                }
                
                // Set authentication
                ApiKeyAuthenticationToken authentication = 
                    new ApiKeyAuthenticationToken(keyDetails);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        
        filterChain.doFilter(request, response);
    }
}

// 3. OAuth2 Resource Server
@Configuration
@EnableWebSecurity
public class OAuth2ResourceServerConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        
        return http.build();
    }
    
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = 
            new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        
        JwtAuthenticationConverter jwtAuthenticationConverter = 
            new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
            grantedAuthoritiesConverter);
        
        return jwtAuthenticationConverter;
    }
}

// 4. Method-level Security
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public ResponseEntity<List<DocumentDTO>> getAllDocuments() {
        return ResponseEntity.ok(documentService.findAll());
    }
    
    @PreAuthorize("hasRole('ADMIN') or @documentSecurity.isOwner(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.findById(id));
    }
    
    @PreAuthorize("hasAuthority('WRITE_DOCUMENTS')")
    @PostMapping
    public ResponseEntity<DocumentDTO> createDocument(@RequestBody DocumentDTO document) {
        return ResponseEntity.ok(documentService.create(document));
    }
    
    @PreAuthorize("@documentSecurity.canDelete(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

@Component("documentSecurity")
public class DocumentSecurityService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    public boolean isOwner(Long documentId, Authentication authentication) {
        if (authentication == null) return false;
        
        String username = authentication.getName();
        return documentRepository.findById(documentId)
            .map(doc -> doc.getOwner().equals(username))
            .orElse(false);
    }
    
    public boolean canDelete(Long documentId, Authentication authentication) {
        if (authentication == null) return false;
        
        // Admins can delete anything
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        
        // Owners can delete their own documents
        return isOwner(documentId, authentication);
    }
}

// 5. Rate Limiting
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) throws Exception {
        
        String clientId = getClientId(request);
        String endpoint = request.getRequestURI();
        
        RateLimitResult result = rateLimitService.checkLimit(clientId, endpoint);
        
        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime()));
        
        if (!result.isAllowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfter()));
            response.getWriter().write("Rate limit exceeded");
            return false;
        }
        
        return true;
    }
    
    private String getClientId(HttpServletRequest request) {
        // Try API key first
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) return apiKey;
        
        // Try JWT token
        String token = request.getHeader("Authorization");
        if (token != null) return extractUserFromToken(token);
        
        // Fall back to IP address
        return request.getRemoteAddr();
    }
}

@Service
public class RateLimitService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private static final int DEFAULT_LIMIT = 100; // requests per minute
    
    public RateLimitResult checkLimit(String clientId, String endpoint) {
        String key = "rate_limit:" + clientId + ":" + endpoint;
        String minuteKey = key + ":" + getCurrentMinute();
        
        Long currentCount = redisTemplate.opsForValue().increment(minuteKey);
        
        if (currentCount == 1) {
            redisTemplate.expire(minuteKey, Duration.ofMinutes(1));
        }
        
        boolean allowed = currentCount <= DEFAULT_LIMIT;
        long remaining = Math.max(0, DEFAULT_LIMIT - currentCount);
        long resetTime = getNextMinuteTimestamp();
        long retryAfter = allowed ? 0 : (resetTime - System.currentTimeMillis()) / 1000;
        
        return new RateLimitResult(allowed, DEFAULT_LIMIT, remaining, resetTime, retryAfter);
    }
    
    private String getCurrentMinute() {
        return String.valueOf(System.currentTimeMillis() / 60000);
    }
    
    private long getNextMinuteTimestamp() {
        return ((System.currentTimeMillis() / 60000) + 1) * 60000;
    }
}
```

### 11. How do you implement API rate limiting and throttling?

**Answer:**

```java
// Token Bucket Algorithm
@Service
public class TokenBucketRateLimiter {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private static final int BUCKET_CAPACITY = 100;
    private static final int REFILL_RATE = 10; // tokens per second
    
    public boolean allowRequest(String clientId) {
        String key = "token_bucket:" + clientId;
        
        // Get current bucket state
        Map<Object, Object> bucket = redisTemplate.opsForHash().entries(key);
        
        long now = System.currentTimeMillis();
        long tokens = bucket.containsKey("tokens") 
            ? Long.parseLong((String) bucket.get("tokens")) 
            : BUCKET_CAPACITY;
        long lastRefill = bucket.containsKey("lastRefill")
            ? Long.parseLong((String) bucket.get("lastRefill"))
            : now;
        
        // Calculate tokens to add
        long elapsedSeconds = (now - lastRefill) / 1000;
        long tokensToAdd = elapsedSeconds * REFILL_RATE;
        tokens = Math.min(BUCKET_CAPACITY, tokens + tokensToAdd);
        
        if (tokens < 1) {
            return false;
        }
        
        // Consume one token
        tokens--;
        
        // Update bucket state
        redisTemplate.opsForHash().put(key, "tokens", String.valueOf(tokens));
        redisTemplate.opsForHash().put(key, "lastRefill", String.valueOf(now));
        redisTemplate.expire(key, Duration.ofMinutes(5));
        
        return true;
    }
}

// Sliding Window Rate Limiter
@Service
public class SlidingWindowRateLimiter {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private static final int WINDOW_SIZE_SECONDS = 60;
    private static final int MAX_REQUESTS = 100;
    
    public boolean allowRequest(String clientId) {
        String key = "sliding_window:" + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - (WINDOW_SIZE_SECONDS * 1000);
        
        // Remove old entries
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        
        // Count requests in current window
        Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
        
        if (count != null && count >= MAX_REQUESTS) {
            return false;
        }
        
        // Add current request
        redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
        redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SIZE_SECONDS));
        
        return true;
    }
}

// Tiered Rate Limiting
@Service
public class TieredRateLimitService {
    
    public RateLimitConfig getRateLimitForUser(String userId) {
        UserTier tier = userService.getUserTier(userId);
        
        return switch (tier) {
            case FREE -> new RateLimitConfig(100, 60); // 100 req/min
            case BASIC -> new RateLimitConfig(1000, 60); // 1000 req/min
            case PREMIUM -> new RateLimitConfig(10000, 60); // 10000 req/min
            case ENTERPRISE -> new RateLimitConfig(100000, 60); // 100000 req/min
        };
    }
}

// Rate Limit Annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int value() default 100; // requests per minute
    TimeUnit timeUnit() default TimeUnit.MINUTES;
}

@Aspect
@Component
public class RateLimitAspect {
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) 
            throws Throwable {
        
        HttpServletRequest request = getCurrentRequest();
        String clientId = getClientId(request);
        
        if (!rateLimitService.checkLimit(clientId, rateLimit.value(), rateLimit.timeUnit())) {
            throw new RateLimitExceededException("Rate limit exceeded");
        }
        
        return joinPoint.proceed();
    }
}

// Usage
@RestController
@RequestMapping("/api/search")
public class SearchController {
    
    @RateLimit(value = 10, timeUnit = TimeUnit.SECONDS)
    @GetMapping
    public ResponseEntity<List<SearchResult>> search(@RequestParam String query) {
        return ResponseEntity.ok(searchService.search(query));
    }
}
```

### 12. How do you design APIs for webhooks?

**Answer:**

```java
// Webhook Configuration
@Entity
public class WebhookSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String userId;
    private String url;
    private String secret;
    
    @ElementCollection
    private Set<String> events; // order.created, order.updated, etc.
    
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastTriggeredAt;
}

// Webhook Registration API
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    
    @Autowired
    private WebhookService webhookService;
    
    @PostMapping
    public ResponseEntity<WebhookSubscription> createWebhook(
            @RequestBody @Valid WebhookRequest request,
            @AuthenticationPrincipal UserDetails user) {
        
        WebhookSubscription subscription = webhookService.create(
            user.getUsername(),
            request.getUrl(),
            request.getEvents()
        );
        
        return ResponseEntity.created(
            URI.create("/api/webhooks/" + subscription.getId())
        ).body(subscription);
    }
    
    @GetMapping
    public ResponseEntity<List<WebhookSubscription>> listWebhooks(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(webhookService.findByUser(user.getUsername()));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long id) {
        webhookService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/test")
    public ResponseEntity<WebhookTestResult> testWebhook(@PathVariable Long id) {
        WebhookTestResult result = webhookService.test(id);
        return ResponseEntity.ok(result);
    }
}

// Webhook Delivery Service
@Service
public class WebhookDeliveryService {
    
    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Async
    public void deliverWebhook(String eventType, Object payload) {
        List<WebhookSubscription> subscriptions = subscriptionRepository
            .findByEventsContainingAndActiveTrue(eventType);
        
        for (WebhookSubscription subscription : subscriptions) {
            deliverToSubscription(subscription, eventType, payload);
        }
    }
    
    private void deliverToSubscription(WebhookSubscription subscription,
                                      String eventType,
                                      Object payload) {
        try {
            WebhookPayload webhookPayload = new WebhookPayload();
            webhookPayload.setEvent(eventType);
            webhookPayload.setData(payload);
            webhookPayload.setTimestamp(LocalDateTime.now());
            webhookPayload.setWebhookId(subscription.getId());
            
            // Generate signature
            String signature = generateSignature(webhookPayload, subscription.getSecret());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Signature", signature);
            headers.set("X-Webhook-Event", eventType);
            headers.set("X-Webhook-ID", subscription.getId().toString());
            
            HttpEntity<WebhookPayload> request = new HttpEntity<>(webhookPayload, headers);
            
            // Retry logic
            int maxRetries = 3;
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(
                        subscription.getUrl(),
                        request,
                        String.class
                    );
                    
                    if (response.getStatusCode().is2xxSuccessful()) {
                        logSuccess(subscription, eventType);
                        return;
                    }
                } catch (Exception e) {
                    if (attempt == maxRetries - 1) {
                        logFailure(subscription, eventType, e);
                    } else {
                        Thread.sleep((long) Math.pow(2, attempt) * 1000); // Exponential backoff
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to deliver webhook", e);
        }
    }
    
    private String generateSignature(WebhookPayload payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            byte[] hash = mac.doFinal(payloadJson.getBytes());
            
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
}

// Webhook Verification (for receiving webhooks)
@RestController
@RequestMapping("/webhooks")
public class WebhookReceiverController {
    
    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        
        if (!verifyStripeSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Process webhook
        processStripeWebhook(payload);
        
        return ResponseEntity.ok().build();
    }
    
    private boolean verifyStripeSignature(String payload, String signature) {
        // Verify signature using Stripe's algorithm
        return true;
    }
}

// Event Publishing
@Service
public class OrderService {
    
    @Autowired
    private WebhookDeliveryService webhookService;
    
    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));
        
        // Trigger webhook
        webhookService.deliverWebhook("order.created", order);
        
        return order;
    }
    
    @Transactional
    public Order updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(status);
        orderRepository.save(order);
        
        // Trigger webhook
        webhookService.deliverWebhook("order.status_changed", order);
        
        return order;
    }
}
```

---

*Continued with remaining questions 13-25...*
