# API Design & OpenAPI Interview Questions & Answers

## Table of Contents
1. [RESTful API Design Principles](#restful-api-design-principles)
2. [OpenAPI Specification](#openapi-specification)
3. [API Versioning](#api-versioning)
4. [Security & Authentication](#security--authentication)
5. [Best Practices & Real-World Scenarios](#best-practices--real-world-scenarios)

---

## RESTful API Design Principles

### 1. What are the key principles of RESTful API design?

**Answer:**
REST (Representational State Transfer) is an architectural style with six key constraints:

**Six REST Constraints:**
1. **Client-Server Architecture**: Separation of concerns
2. **Stateless**: Each request contains all necessary information
3. **Cacheable**: Responses must define themselves as cacheable or not
4. **Uniform Interface**: Consistent resource identification
5. **Layered System**: Client cannot tell if connected directly to end server
6. **Code on Demand** (optional): Server can extend client functionality

**RESTful API Design Principles:**

```yaml
# Resource-Based URLs (not action-based)
Good:
  GET    /api/users              # Get all users
  GET    /api/users/123          # Get specific user
  POST   /api/users              # Create user
  PUT    /api/users/123          # Update user (full)
  PATCH  /api/users/123          # Update user (partial)
  DELETE /api/users/123          # Delete user

Bad:
  GET    /api/getUsers
  POST   /api/createUser
  POST   /api/updateUser
  POST   /api/deleteUser

# Use HTTP Methods Correctly
GET     - Retrieve resources (idempotent, safe)
POST    - Create new resources
PUT     - Update/replace entire resource (idempotent)
PATCH   - Partial update (not necessarily idempotent)
DELETE  - Remove resources (idempotent)

# Use Plural Nouns for Collections
Good:   /api/users, /api/orders, /api/products
Bad:    /api/user, /api/order, /api/product

# Nested Resources for Relationships
GET    /api/users/123/orders           # Get orders for user 123
POST   /api/users/123/orders           # Create order for user 123
GET    /api/orders/456/items           # Get items in order 456

# Use Query Parameters for Filtering, Sorting, Pagination
GET /api/users?status=active&role=admin
GET /api/products?category=electronics&sort=price&order=desc
GET /api/orders?page=2&size=20

# HTTP Status Codes
200 OK                  - Successful GET, PUT, PATCH
201 Created             - Successful POST
204 No Content          - Successful DELETE
400 Bad Request         - Invalid request
401 Unauthorized        - Authentication required
403 Forbidden           - Insufficient permissions
404 Not Found           - Resource doesn't exist
409 Conflict            - Conflict with current state
422 Unprocessable Entity - Validation errors
500 Internal Server Error - Server error
```

**Example Implementation:**

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    // GET /api/users?status=active&page=0&size=20
    @GetMapping
    public ResponseEntity<Page<UserDTO>> getUsers(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<UserDTO> users = userService.findUsers(status, pageable);
        return ResponseEntity.ok(users);
    }
    
    // GET /api/users/123
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    // POST /api/users
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDTO created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(created);
    }
    
    // PUT /api/users/123
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    // PATCH /api/users/123
    @PatchMapping("/{id}")
    public ResponseEntity<UserDTO> partialUpdateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        return userService.partialUpdate(id, updates)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    // DELETE /api/users/123
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (userService.deleteUser(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    
    // Nested resource: GET /api/users/123/orders
    @GetMapping("/{userId}/orders")
    public ResponseEntity<List<OrderDTO>> getUserOrders(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.findByUserId(userId));
    }
}
```

### 2. How do you design API responses with proper error handling?

**Answer:**

```java
// Standard API Response Wrapper
@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private LocalDateTime timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, LocalDateTime.now());
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, LocalDateTime.now());
    }
}

// Error Response Structure
@Data
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private List<ValidationError> errors;
    private String traceId;
}

@Data
@AllArgsConstructor
public class ValidationError {
    private String field;
    private String message;
    private Object rejectedValue;
}

// Global Exception Handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.NOT_FOUND.value())
            .error("Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        List<ValidationError> validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ValidationError(
                error.getField(),
                error.getDefaultMessage(),
                error.getRejectedValue()
            ))
            .collect(Collectors.toList());
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid request parameters")
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .errors(validationErrors)
            .traceId(MDC.get("traceId"))
            .build();
        
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.CONFLICT.value())
            .error("Conflict")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Unauthorized")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred")
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .traceId(MDC.get("traceId"))
            .build();
        
        // Log the full exception
        log.error("Unexpected error", ex);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

// Example Error Responses
/*
404 Not Found:
{
  "status": 404,
  "error": "Not Found",
  "message": "User with id 123 not found",
  "path": "/api/users/123",
  "timestamp": "2024-01-15T10:30:00",
  "traceId": "abc123xyz"
}

400 Bad Request (Validation):
{
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid request parameters",
  "path": "/api/users",
  "timestamp": "2024-01-15T10:30:00",
  "errors": [
    {
      "field": "email",
      "message": "must be a valid email address",
      "rejectedValue": "invalid-email"
    },
    {
      "field": "age",
      "message": "must be greater than or equal to 18",
      "rejectedValue": 15
    }
  ],
  "traceId": "abc123xyz"
}

409 Conflict:
{
  "status": 409,
  "error": "Conflict",
  "message": "User with email john@example.com already exists",
  "path": "/api/users",
  "timestamp": "2024-01-15T10:30:00",
  "traceId": "abc123xyz"
}
*/
```

### 3. How do you implement pagination, filtering, and sorting in REST APIs?

**Answer:**

```java
// Pagination Request Parameters
@Data
public class PaginationRequest {
    @Min(0)
    private int page = 0;
    
    @Min(1)
    @Max(100)
    private int size = 20;
    
    private String sortBy = "id";
    private String sortDirection = "ASC";
}

// Paginated Response
@Data
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean hasNext;
    private boolean hasPrevious;
}

// Controller with Pagination, Filtering, Sorting
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    /**
     * GET /api/products?page=0&size=20&sortBy=price&sortDirection=DESC
     *                   &category=electronics&minPrice=100&maxPrice=1000
     *                   &search=laptop
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ProductDTO>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean inStock) {
        
        ProductFilterCriteria criteria = ProductFilterCriteria.builder()
            .category(category)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .search(search)
            .inStock(inStock)
            .build();
        
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<ProductDTO> productPage = productService.findProducts(criteria, pageable);
        
        PagedResponse<ProductDTO> response = new PagedResponse<>(
            productPage.getContent(),
            productPage.getNumber(),
            productPage.getSize(),
            productPage.getTotalElements(),
            productPage.getTotalPages(),
            productPage.isFirst(),
            productPage.isLast(),
            productPage.hasNext(),
            productPage.hasPrevious()
        );
        
        return ResponseEntity.ok(response);
    }
}

// Service with Dynamic Filtering
@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    public Page<ProductDTO> findProducts(ProductFilterCriteria criteria, Pageable pageable) {
        Specification<Product> spec = ProductSpecifications.withCriteria(criteria);
        Page<Product> products = productRepository.findAll(spec, pageable);
        return products.map(this::convertToDTO);
    }
}

// Specifications for Dynamic Filtering
public class ProductSpecifications {
    
    public static Specification<Product> withCriteria(ProductFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (criteria.getCategory() != null) {
                predicates.add(cb.equal(root.get("category"), criteria.getCategory()));
            }
            
            if (criteria.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("price"), criteria.getMinPrice()));
            }
            
            if (criteria.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("price"), criteria.getMaxPrice()));
            }
            
            if (criteria.getSearch() != null && !criteria.getSearch().isEmpty()) {
                String searchPattern = "%" + criteria.getSearch().toLowerCase() + "%";
                Predicate namePredicate = cb.like(
                    cb.lower(root.get("name")), searchPattern);
                Predicate descPredicate = cb.like(
                    cb.lower(root.get("description")), searchPattern);
                predicates.add(cb.or(namePredicate, descPredicate));
            }
            
            if (criteria.getInStock() != null && criteria.getInStock()) {
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

// Cursor-Based Pagination (for better performance on large datasets)
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    /**
     * GET /api/orders?cursor=2024-01-15T10:30:00&limit=20
     */
    @GetMapping
    public ResponseEntity<CursorPagedResponse<OrderDTO>> getOrders(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        
        LocalDateTime cursorTime = cursor != null 
            ? LocalDateTime.parse(cursor) 
            : LocalDateTime.now();
        
        List<Order> orders = orderRepository.findOrdersAfterCursor(cursorTime, limit + 1);
        
        boolean hasNext = orders.size() > limit;
        if (hasNext) {
            orders = orders.subList(0, limit);
        }
        
        String nextCursor = hasNext && !orders.isEmpty()
            ? orders.get(orders.size() - 1).getCreatedAt().toString()
            : null;
        
        List<OrderDTO> dtos = orders.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
        
        CursorPagedResponse<OrderDTO> response = new CursorPagedResponse<>(
            dtos, nextCursor, hasNext);
        
        return ResponseEntity.ok(response);
    }
}

@Data
@AllArgsConstructor
public class CursorPagedResponse<T> {
    private List<T> data;
    private String nextCursor;
    private boolean hasMore;
}
```

### 4. What is HATEOAS and how do you implement it?

**Answer:**
HATEOAS (Hypermedia as the Engine of Application State) is a REST constraint where responses include links to related resources.

```java
// Using Spring HATEOAS
@RestController
@RequestMapping("/api/users")
public class UserHATEOASController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<UserDTO>> getUser(@PathVariable Long id) {
        UserDTO user = userService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        EntityModel<UserDTO> resource = EntityModel.of(user);
        
        // Add self link
        resource.add(linkTo(methodOn(UserHATEOASController.class)
            .getUser(id)).withSelfRel());
        
        // Add related links
        resource.add(linkTo(methodOn(UserHATEOASController.class)
            .getUserOrders(id)).withRel("orders"));
        
        resource.add(linkTo(methodOn(UserHATEOASController.class)
            .getUserProfile(id)).withRel("profile"));
        
        // Conditional links based on state
        if (user.getStatus().equals("ACTIVE")) {
            resource.add(linkTo(methodOn(UserHATEOASController.class)
                .deactivateUser(id)).withRel("deactivate"));
        } else {
            resource.add(linkTo(methodOn(UserHATEOASController.class)
                .activateUser(id)).withRel("activate"));
        }
        
        return ResponseEntity.ok(resource);
    }
    
    @GetMapping
    public ResponseEntity<CollectionModel<EntityModel<UserDTO>>> getAllUsers() {
        List<EntityModel<UserDTO>> users = userService.findAll().stream()
            .map(user -> {
                EntityModel<UserDTO> resource = EntityModel.of(user);
                resource.add(linkTo(methodOn(UserHATEOASController.class)
                    .getUser(user.getId())).withSelfRel());
                return resource;
            })
            .collect(Collectors.toList());
        
        CollectionModel<EntityModel<UserDTO>> collection = CollectionModel.of(users);
        collection.add(linkTo(methodOn(UserHATEOASController.class)
            .getAllUsers()).withSelfRel());
        
        return ResponseEntity.ok(collection);
    }
}

// Example HATEOAS Response
/*
GET /api/users/123

{
  "id": 123,
  "username": "john_doe",
  "email": "john@example.com",
  "status": "ACTIVE",
  "_links": {
    "self": {
      "href": "http://api.example.com/api/users/123"
    },
    "orders": {
      "href": "http://api.example.com/api/users/123/orders"
    },
    "profile": {
      "href": "http://api.example.com/api/users/123/profile"
    },
    "deactivate": {
      "href": "http://api.example.com/api/users/123/deactivate",
      "method": "POST"
    }
  }
}
*/

// Custom HATEOAS Implementation
@Data
public class ResourceWithLinks<T> {
    private T data;
    private Map<String, Link> links = new HashMap<>();
    
    public void addLink(String rel, String href) {
        links.put(rel, new Link(href));
    }
    
    public void addLink(String rel, String href, String method) {
        links.put(rel, new Link(href, method));
    }
}

@Data
@AllArgsConstructor
public class Link {
    private String href;
    private String method = "GET";
    
    public Link(String href) {
        this.href = href;
    }
}

@RestController
@RequestMapping("/api/orders")
public class OrderHATEOASController {
    
    @GetMapping("/{id}")
    public ResponseEntity<ResourceWithLinks<OrderDTO>> getOrder(@PathVariable Long id) {
        OrderDTO order = orderService.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        ResourceWithLinks<OrderDTO> resource = new ResourceWithLinks<>();
        resource.setData(order);
        
        String baseUrl = "http://api.example.com/api/orders/" + id;
        
        resource.addLink("self", baseUrl);
        resource.addLink("items", baseUrl + "/items");
        resource.addLink("customer", "http://api.example.com/api/users/" + order.getUserId());
        
        // State-based links
        if (order.getStatus().equals("PENDING")) {
            resource.addLink("cancel", baseUrl + "/cancel", "POST");
            resource.addLink("confirm", baseUrl + "/confirm", "POST");
        } else if (order.getStatus().equals("CONFIRMED")) {
            resource.addLink("ship", baseUrl + "/ship", "POST");
        } else if (order.getStatus().equals("SHIPPED")) {
            resource.addLink("deliver", baseUrl + "/deliver", "POST");
        }
        
        return ResponseEntity.ok(resource);
    }
}
```

### 5. How do you design APIs for idempotency?

**Answer:**

```java
// Idempotency Key Header
public class IdempotencyKeyInterceptor implements HandlerInterceptor {
    
    private final IdempotencyService idempotencyService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        String method = request.getMethod();
        
        // Only check for non-idempotent methods
        if ("POST".equals(method) || "PATCH".equals(method)) {
            String idempotencyKey = request.getHeader("Idempotency-Key");
            
            if (idempotencyKey == null || idempotencyKey.isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.getWriter().write("Idempotency-Key header is required");
                return false;
            }
            
            // Check if request was already processed
            Optional<IdempotencyRecord> existing = 
                idempotencyService.findByKey(idempotencyKey);
            
            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                
                if (record.getStatus() == ProcessingStatus.COMPLETED) {
                    // Return cached response
                    response.setStatus(record.getStatusCode());
                    response.setContentType("application/json");
                    response.getWriter().write(record.getResponseBody());
                    return false;
                } else if (record.getStatus() == ProcessingStatus.PROCESSING) {
                    // Request is still being processed
                    response.setStatus(HttpStatus.CONFLICT.value());
                    response.getWriter().write("Request is already being processed");
                    return false;
                }
            }
            
            // Create new idempotency record
            idempotencyService.createRecord(idempotencyKey, request);
            request.setAttribute("idempotencyKey", idempotencyKey);
        }
        
        return true;
    }
}

// Idempotency Service
@Service
public class IdempotencyService {
    
    @Autowired
    private IdempotencyRepository repository;
    
    @Transactional
    public void createRecord(String key, HttpServletRequest request) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(key);
        record.setRequestMethod(request.getMethod());
        record.setRequestUri(request.getRequestURI());
        record.setStatus(ProcessingStatus.PROCESSING);
        record.setCreatedAt(LocalDateTime.now());
        
        repository.save(record);
    }
    
    @Transactional
    public void saveResponse(String key, int statusCode, String responseBody) {
        repository.findByIdempotencyKey(key).ifPresent(record -> {
            record.setStatus(ProcessingStatus.COMPLETED);
            record.setStatusCode(statusCode);
            record.setResponseBody(responseBody);
            record.setCompletedAt(LocalDateTime.now());
            repository.save(record);
        });
    }
    
    public Optional<IdempotencyRecord> findByKey(String key) {
        return repository.findByIdempotencyKey(key);
    }
    
    // Clean up old records (run periodically)
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void cleanupOldRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        repository.deleteByCreatedAtBefore(cutoff);
    }
}

// Controller with Idempotency
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private IdempotencyService idempotencyService;
    
    /**
     * POST /api/payments
     * Headers:
     *   Idempotency-Key: unique-key-123
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestBody @Valid PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        
        try {
            PaymentResponse response = paymentService.processPayment(request);
            
            // Save response for future idempotent requests
            String responseBody = objectMapper.writeValueAsString(response);
            idempotencyService.saveResponse(
                idempotencyKey, 
                HttpStatus.CREATED.value(), 
                responseBody
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            // Mark as failed
            idempotencyService.saveResponse(
                idempotencyKey,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "{\"error\": \"" + e.getMessage() + "\"}"
            );
            throw e;
        }
    }
}

// Idempotency Entity
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String idempotencyKey;
    
    private String requestMethod;
    private String requestUri;
    
    @Enumerated(EnumType.STRING)
    private ProcessingStatus status;
    
    private Integer statusCode;
    
    @Column(columnDefinition = "TEXT")
    private String responseBody;
    
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

public enum ProcessingStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

// Naturally Idempotent Operations
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    // GET is naturally idempotent
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        // Multiple calls return same result
        return ResponseEntity.ok(userService.findById(id));
    }
    
    // PUT is naturally idempotent (full replacement)
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @RequestBody UserDTO user) {
        // Multiple calls with same data produce same result
        return ResponseEntity.ok(userService.update(id, user));
    }
    
    // DELETE is naturally idempotent
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        // First call deletes, subsequent calls are no-op
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## OpenAPI Specification

### 6. What is OpenAPI Specification and how do you document APIs with it?

**Answer:**
OpenAPI Specification (formerly Swagger) is a standard for describing REST APIs.

```yaml
# openapi.yaml
openapi: 3.0.3
info:
  title: User Management API
  description: API for managing users and their profiles
  version: 1.0.0
  contact:
    name: API Support
    email: support@example.com
    url: https://example.com/support
  license:
    name: Apache 2.0
    url: https://www.apache.org/licenses/LICENSE-2.0.html

servers:
  - url: https://api.example.com/v1
    description: Production server
  - url: https://staging-api.example.com/v1
    description: Staging server
  - url: http://localhost:8080/v1
    description: Development server

tags:
  - name: users
    description: User management operations
  - name: orders
    description: Order management operations

paths:
  /users:
    get:
      tags:
        - users
      summary: Get all users
      description: Retrieve a paginated list of users with optional filtering
      operationId: getUsers
      parameters:
        - name: page
          in: query
          description: Page number (0-indexed)
          required: false
          schema:
            type: integer
            minimum: 0
            default: 0
        - name: size
          in: query
          description: Number of items per page
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
        - name: status
          in: query
          description: Filter by user status
          required: false
          schema:
            type: string
            enum: [ACTIVE, INACTIVE, SUSPENDED]
        - name: sortBy
          in: query
          description: Field to sort by
          required: false
          schema:
            type: string
            enum: [id, username, email, createdAt]
            default: id
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PagedUserResponse'
              examples:
                success:
                  value:
                    content:
                      - id: 1
                        username: john_doe
                        email: john@example.com
                        status: ACTIVE
                    page: 0
                    size: 20
                    totalElements: 100
                    totalPages: 5
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '500':
          $ref: '#/components/responses/InternalServerError'
      security:
        - bearerAuth: []
    
    post:
      tags:
        - users
      summary: Create a new user
      description: Create a new user account
      operationId: createUser
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateUserRequest'
            examples:
              example1:
                value:
                  username: john_doe
                  email: john@example.com
                  password: SecurePass123!
                  firstName: John
                  lastName: Doe
      responses:
        '201':
          description: User created successfully
          headers:
            Location:
              description: URL of the created user
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '409':
          description: User already exists
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
      security:
        - bearerAuth: []
  
  /users/{userId}:
    parameters:
      - name: userId
        in: path
        required: true
        description: User ID
        schema:
          type: integer
          format: int64
    
    get:
      tags:
        - users
      summary: Get user by ID
      description: Retrieve a specific user by their ID
      operationId: getUserById
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '404':
          $ref: '#/components/responses/NotFound'
      security:
        - bearerAuth: []
    
    put:
      tags:
        - users
      summary: Update user
      description: Update an existing user (full replacement)
      operationId: updateUser
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateUserRequest'
      responses:
        '200':
          description: User updated successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserResponse'
        '404':
          $ref: '#/components/responses/NotFound'
      security:
        - bearerAuth: []
    
    delete:
      tags:
        - users
      summary: Delete user
      description: Delete a user account
      operationId: deleteUser
      responses:
        '204':
          description: User deleted successfully
        '404':
          $ref: '#/components/responses/NotFound'
      security:
        - bearerAuth: []

components:
  schemas:
    UserResponse:
      type: object
      required:
        - id
        - username
        - email
        - status
      properties:
        id:
          type: integer
          format: int64
          example: 123
        username:
          type: string
          example: john_doe
        email:
          type: string
          format: email
          example: john@example.com
        firstName:
          type: string
          example: John
        lastName:
          type: string
          example: Doe
        status:
          type: string
          enum: [ACTIVE, INACTIVE, SUSPENDED]
          example: ACTIVE
        createdAt:
          type: string
          format: date-time
          example: '2024-01-15T10:30:00Z'
        updatedAt:
          type: string
          format: date-time
          example: '2024-01-15T10:30:00Z'
    
    CreateUserRequest:
      type: object
      required:
        - username
        - email
        - password
      properties:
        username:
          type: string
          minLength: 3
          maxLength: 50
          pattern: '^[a-zA-Z0-9_]+$'
          example: john_doe
        email:
          type: string
          format: email
          example: john@example.com
        password:
          type: string
          format: password
          minLength: 8
          example: SecurePass123!
        firstName:
          type: string
          maxLength: 100
          example: John
        lastName:
          type: string
          maxLength: 100
          example: Doe
    
    UpdateUserRequest:
      type: object
      properties:
        email:
          type: string
          format: email
        firstName:
          type: string
        lastName:
          type: string
        status:
          type: string
          enum: [ACTIVE, INACTIVE, SUSPENDED]
    
    PagedUserResponse:
      type: object
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/UserResponse'
        page:
          type: integer
        size:
          type: integer
        totalElements:
          type: integer
          format: int64
        totalPages:
          type: integer
        first:
          type: boolean
        last:
          type: boolean
    
    ErrorResponse:
      type: object
      required:
        - status
        - error
        - message
        - timestamp
      properties:
        status:
          type: integer
          example: 400
        error:
          type: string
          example: Bad Request
        message:
          type: string
          example: Invalid request parameters
        path:
          type: string
          example: /api/users
        timestamp:
          type: string
          format: date-time
        errors:
          type: array
          items:
            $ref: '#/components/schemas/ValidationError'
        traceId:
          type: string
          example: abc123xyz
    
    ValidationError:
      type: object
      properties:
        field:
          type: string
          example: email
        message:
          type: string
          example: must be a valid email address
        rejectedValue:
          type: object
          example: invalid-email
  
  responses:
    BadRequest:
      description: Bad Request
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
    
    Unauthorized:
      description: Unauthorized
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
    
    NotFound:
      description: Resource not found
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
    
    InternalServerError:
      description: Internal Server Error
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
  
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: JWT token authentication
    
    apiKey:
      type: apiKey
      in: header
      name: X-API-Key
      description: API key authentication
    
    oauth2:
      type: oauth2
      flows:
        authorizationCode:
          authorizationUrl: https://auth.example.com/oauth/authorize
          tokenUrl: https://auth.example.com/oauth/token
          scopes:
            read:users: Read user information
            write:users: Modify user information
            delete:users: Delete users

security:
  - bearerAuth: []
```

### 7. How do you generate OpenAPI documentation from code using SpringDoc?

**Answer:**

```java
// Maven dependency
/*
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
*/

// OpenAPI Configuration
@Configuration
public class OpenAPIConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("User Management API")
                .version("1.0.0")
                .description("API for managing users and their profiles")
                .contact(new Contact()
                    .name("API Support")
                    .email("support@example.com")
                    .url("https://example.com/support"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(Arrays.asList(
                new Server()
                    .url("https://api.example.com/v1")
                    .description("Production server"),
                new Server()
                    .url("http://localhost:8080/v1")
                    .description("Development server")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token authentication")));
    }
    
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/**")
            .build();
    }
    
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/admin/**")
            .build();
    }
}

// Controller with OpenAPI Annotations
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @Operation(
        summary = "Get all users",
        description = "Retrieve a paginated list of users with optional filtering"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successful response",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PagedUserResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping
    public ResponseEntity<PagedUserResponse> getUsers(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "Number of items per page", example = "20")
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "Filter by user status")
            @RequestParam(required = false) 
            @Schema(allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"})
            String status) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<UserDTO> users = userService.findUsers(status, pageable);
        return ResponseEntity.ok(convertToPagedResponse(users));
    }
    
    @Operation(
        summary = "Get user by ID",
        description = "Retrieve a specific user by their ID"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "User found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserDTO.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUser(
            @Parameter(description = "User ID", required = true, example = "123")
            @PathVariable Long userId) {
        
        return userService.findById(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @Operation(
        summary = "Create a new user",
        description = "Create a new user account"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserDTO.class)
            ),
            headers = @Header(
                name = "Location",
                description = "URL of the created user",
                schema = @Schema(type = "string")
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "User already exists",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    @PostMapping
    public ResponseEntity<UserDTO> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "User creation request",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreateUserRequest.class),
                    examples = @ExampleObject(
                        name = "example1",
                        value = """
                            {
                              "username": "john_doe",
                              "email": "john@example.com",
                              "password": "SecurePass123!",
                              "firstName": "John",
                              "lastName": "Doe"
                            }
                            """
                    )
                )
            )
            @Valid @RequestBody CreateUserRequest request) {
        
        UserDTO created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        
        return ResponseEntity.created(location).body(created);
    }
}

// DTO with Schema Annotations
@Data
@Schema(description = "User response object")
public class UserDTO {
    
    @Schema(description = "User ID", example = "123", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    
    @Schema(description = "Username", example = "john_doe", required = true)
    private String username;
    
    @Schema(description = "Email address", example = "john@example.com", required = true)
    private String email;
    
    @Schema(description = "First name", example = "John")
    private String firstName;
    
    @Schema(description = "Last name", example = "Doe")
    private String lastName;
    
    @Schema(description = "User status", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"})
    private String status;
    
    @Schema(description = "Creation timestamp", example = "2024-01-15T10:30:00Z", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;
}

@Data
@Schema(description = "User creation request")
public class CreateUserRequest {
    
    @Schema(description = "Username", example = "john_doe", required = true, minLength = 3, maxLength = 50)
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;
    
    @Schema(description = "Email address", example = "john@example.com", required = true)
    @NotBlank
    @Email
    private String email;
    
    @Schema(description = "Password", example = "SecurePass123!", required = true, minLength = 8, format = "password")
    @NotBlank
    @Size(min = 8)
    private String password;
    
    @Schema(description = "First name", example = "John")
    private String firstName;
    
    @Schema(description = "Last name", example = "Doe")
    private String lastName;
}

// application.properties
/*
# SpringDoc configuration
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.tryItOutEnabled=true
springdoc.show-actuator=false
*/

// Access Swagger UI at: http://localhost:8080/swagger-ui.html
// Access OpenAPI JSON at: http://localhost:8080/api-docs
```

---

*Continued in next response with questions 8-25...*
