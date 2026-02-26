# Testing Interview Questions & Answers (Unit & Integration Testing)

## Unit Testing Fundamentals

### 1. What is unit testing and why is it important?

**Answer:**
Unit testing involves testing individual components or methods in isolation to verify they work correctly.

**Benefits:**
- Early bug detection
- Facilitates refactoring
- Living documentation
- Faster feedback loop
- Reduces debugging time
- Improves code quality

```java
// Simple unit test example
@ExtendWith(MockitoExtension.class)
class CalculatorTest {
    
    private Calculator calculator;
    
    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }
    
    @Test
    @DisplayName("Should add two positive numbers correctly")
    void testAddition() {
        // Arrange
        int a = 5;
        int b = 3;
        
        // Act
        int result = calculator.add(a, b);
        
        // Assert
        assertEquals(8, result);
    }
    
    @Test
    @DisplayName("Should throw exception when dividing by zero")
    void testDivisionByZero() {
        assertThrows(ArithmeticException.class, () -> {
            calculator.divide(10, 0);
        });
    }
    
    @ParameterizedTest
    @CsvSource({
        "1, 1, 2",
        "2, 3, 5",
        "10, 20, 30"
    })
    void testAdditionWithMultipleInputs(int a, int b, int expected) {
        assertEquals(expected, calculator.add(a, b));
    }
}
```

### 2. Explain Mockito and how to use it for unit testing

**Answer:**
Mockito is a mocking framework for creating test doubles in unit tests.

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @InjectMocks
    private UserService userService;
    
    @Captor
    private ArgumentCaptor<User> userCaptor;
    
    @Test
    void testCreateUser() {
        // Arrange
        User user = new User("john", "john@example.com");
        when(userRepository.save(any(User.class))).thenReturn(user);
        
        // Act
        User result = userService.createUser(user);
        
        // Assert
        assertNotNull(result);
        assertEquals("john", result.getUsername());
        verify(userRepository).save(user);
        verify(emailService).sendWelcomeEmail(user.getEmail());
    }
    
    @Test
    void testFindUserById() {
        // Arrange
        Long userId = 1L;
        User user = new User("john", "john@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        // Act
        User result = userService.findById(userId);
        
        // Assert
        assertNotNull(result);
        assertEquals("john", result.getUsername());
        verify(userRepository, times(1)).findById(userId);
    }
    
    @Test
    void testFindUserById_NotFound() {
        // Arrange
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> {
            userService.findById(userId);
        });
    }
    
    @Test
    void testUpdateUser() {
        // Arrange
        User existingUser = new User("john", "john@example.com");
        User updatedUser = new User("john_updated", "john@example.com");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        
        // Act
        userService.updateUser(1L, updatedUser);
        
        // Assert
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals("john_updated", capturedUser.getUsername());
    }
    
    @Test
    void testDeleteUser() {
        // Arrange
        Long userId = 1L;
        doNothing().when(userRepository).deleteById(userId);
        
        // Act
        userService.deleteUser(userId);
        
        // Assert
        verify(userRepository).deleteById(userId);
    }
    
    @Test
    void testMethodWithMultipleCalls() {
        // Arrange
        when(userRepository.count())
            .thenReturn(10L)
            .thenReturn(11L);
        
        // Act & Assert
        assertEquals(10L, userRepository.count());
        assertEquals(11L, userRepository.count());
    }
    
    @Test
    void testVoidMethodWithException() {
        // Arrange
        doThrow(new RuntimeException("Database error"))
            .when(emailService).sendEmail(anyString());
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            emailService.sendEmail("test@example.com");
        });
    }
}

// Spy example - partial mocking
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Spy
    private OrderService orderService;
    
    @Test
    void testSpyExample() {
        // Real method is called unless stubbed
        doReturn(100.0).when(orderService).calculateDiscount(anyDouble());
        
        double total = orderService.calculateTotal(1000.0);
        
        verify(orderService).calculateDiscount(1000.0);
    }
}
```

### 3. What is the difference between @Mock, @Spy, and @InjectMocks?

**Answer:**

```java
@ExtendWith(MockitoExtension.class)
class AnnotationDifferenceTest {
    
    // @Mock - Creates a mock instance (all methods return default values)
    @Mock
    private UserRepository userRepository;
    
    // @Spy - Creates a spy instance (real methods are called unless stubbed)
    @Spy
    private EmailValidator emailValidator = new EmailValidator();
    
    // @InjectMocks - Creates instance and injects mocks/spies into it
    @InjectMocks
    private UserService userService;
    
    @Test
    void demonstrateMock() {
        // Mock returns null by default
        User user = userRepository.findById(1L).orElse(null);
        assertNull(user);
        
        // Stub the mock
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        assertNotNull(userRepository.findById(1L).orElse(null));
    }
    
    @Test
    void demonstrateSpy() {
        // Spy calls real method
        assertTrue(emailValidator.isValid("test@example.com"));
        
        // Can stub spy methods
        when(emailValidator.isValid(anyString())).thenReturn(false);
        assertFalse(emailValidator.isValid("test@example.com"));
    }
}

// Manual creation alternative
class ManualMockCreationTest {
    
    private UserRepository userRepository;
    private UserService userService;
    
    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }
}
```

### 4. How do you test Spring Boot services with JUnit 5?

**Answer:**

```java
// Service to test
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    
    @Transactional
    public Order createOrder(OrderRequest request) {
        // Validate inventory
        if (!inventoryService.checkAvailability(request.getProductId(), request.getQuantity())) {
            throw new InsufficientInventoryException("Product not available");
        }
        
        // Create order
        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus(OrderStatus.PENDING);
        
        Order savedOrder = orderRepository.save(order);
        
        // Process payment
        paymentService.processPayment(savedOrder.getId(), request.getPaymentDetails());
        
        // Update inventory
        inventoryService.reduceStock(request.getProductId(), request.getQuantity());
        
        savedOrder.setStatus(OrderStatus.CONFIRMED);
        return orderRepository.save(savedOrder);
    }
}

// Unit test
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private InventoryService inventoryService;
    
    @Mock
    private PaymentService paymentService;
    
    @InjectMocks
    private OrderService orderService;
    
    private OrderRequest orderRequest;
    private Order order;
    
    @BeforeEach
    void setUp() {
        orderRequest = new OrderRequest();
        orderRequest.setProductId(1L);
        orderRequest.setQuantity(2);
        orderRequest.setPaymentDetails(new PaymentDetails());
        
        order = new Order();
        order.setId(1L);
        order.setProductId(1L);
        order.setQuantity(2);
    }
    
    @Test
    @DisplayName("Should create order successfully when inventory is available")
    void testCreateOrder_Success() {
        // Arrange
        when(inventoryService.checkAvailability(1L, 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doNothing().when(paymentService).processPayment(anyLong(), any());
        doNothing().when(inventoryService).reduceStock(anyLong(), anyInt());
        
        // Act
        Order result = orderService.createOrder(orderRequest);
        
        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        
        // Verify interactions
        verify(inventoryService).checkAvailability(1L, 2);
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(paymentService).processPayment(1L, orderRequest.getPaymentDetails());
        verify(inventoryService).reduceStock(1L, 2);
    }
    
    @Test
    @DisplayName("Should throw exception when inventory is insufficient")
    void testCreateOrder_InsufficientInventory() {
        // Arrange
        when(inventoryService.checkAvailability(1L, 2)).thenReturn(false);
        
        // Act & Assert
        assertThrows(InsufficientInventoryException.class, () -> {
            orderService.createOrder(orderRequest);
        });
        
        verify(inventoryService).checkAvailability(1L, 2);
        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentService, never()).processPayment(anyLong(), any());
    }
    
    @Test
    @DisplayName("Should handle payment failure")
    void testCreateOrder_PaymentFailure() {
        // Arrange
        when(inventoryService.checkAvailability(1L, 2)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doThrow(new PaymentException("Payment failed"))
            .when(paymentService).processPayment(anyLong(), any());
        
        // Act & Assert
        assertThrows(PaymentException.class, () -> {
            orderService.createOrder(orderRequest);
        });
        
        verify(inventoryService, never()).reduceStock(anyLong(), anyInt());
    }
}

// Nested tests for better organization
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Tests")
class OrderServiceNestedTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @InjectMocks
    private OrderService orderService;
    
    @Nested
    @DisplayName("Create Order Tests")
    class CreateOrderTests {
        
        @Test
        @DisplayName("Should create order with valid data")
        void testValidOrder() {
            // Test implementation
        }
        
        @Test
        @DisplayName("Should reject order with invalid data")
        void testInvalidOrder() {
            // Test implementation
        }
    }
    
    @Nested
    @DisplayName("Update Order Tests")
    class UpdateOrderTests {
        
        @Test
        @DisplayName("Should update order status")
        void testUpdateStatus() {
            // Test implementation
        }
    }
}
```

### 5. How do you write integration tests in Spring Boot?

**Answer:**

```java
// Integration test with @SpringBootTest
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class UserControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserRepository userRepository;
    
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }
    
    @Test
    void testCreateUser() throws Exception {
        // Arrange
        UserRequest request = new UserRequest("john", "john@example.com");
        
        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("john"))
            .andExpect(jsonPath("$.email").value("john@example.com"))
            .andExpect(jsonPath("$.id").exists());
        
        // Verify database
        assertEquals(1, userRepository.count());
    }
    
    @Test
    void testGetUser() throws Exception {
        // Arrange
        User user = new User("john", "john@example.com");
        user = userRepository.save(user);
        
        // Act & Assert
        mockMvc.perform(get("/api/users/" + user.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("john"))
            .andExpect(jsonPath("$.email").value("john@example.com"));
    }
    
    @Test
    void testGetUser_NotFound() throws Exception {
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testUpdateUser() throws Exception {
        // Arrange
        User user = userRepository.save(new User("john", "john@example.com"));
        UserRequest updateRequest = new UserRequest("john_updated", "john@example.com");
        
        // Act & Assert
        mockMvc.perform(put("/api/users/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("john_updated"));
        
        // Verify database
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("john_updated", updatedUser.getUsername());
    }
    
    @Test
    void testDeleteUser() throws Exception {
        // Arrange
        User user = userRepository.save(new User("john", "john@example.com"));
        
        // Act & Assert
        mockMvc.perform(delete("/api/users/" + user.getId()))
            .andExpect(status().isNoContent());
        
        // Verify database
        assertFalse(userRepository.existsById(user.getId()));
    }
}

// Integration test with TestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerRestTemplateTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    private String baseUrl;
    
    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/users";
        userRepository.deleteAll();
    }
    
    @Test
    void testCreateUser() {
        // Arrange
        UserRequest request = new UserRequest("john", "john@example.com");
        
        // Act
        ResponseEntity<User> response = restTemplate.postForEntity(
            baseUrl, request, User.class);
        
        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("john", response.getBody().getUsername());
    }
    
    @Test
    void testGetAllUsers() {
        // Arrange
        userRepository.save(new User("john", "john@example.com"));
        userRepository.save(new User("jane", "jane@example.com"));
        
        // Act
        ResponseEntity<User[]> response = restTemplate.getForEntity(
            baseUrl, User[].class);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().length);
    }
}

// WebFlux integration test
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class UserControllerWebFluxTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testCreateUser() {
        UserRequest request = new UserRequest("john", "john@example.com");
        
        webTestClient.post()
            .uri("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.username").isEqualTo("john")
            .jsonPath("$.email").isEqualTo("john@example.com");
    }
}
```

### 6. How do you test Spring Data JPA repositories?

**Answer:**

```java
// Repository test with @DataJpaTest
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    void testFindByEmail() {
        // Arrange
        User user = new User("john", "john@example.com");
        entityManager.persist(user);
        entityManager.flush();
        
        // Act
        User found = userRepository.findByEmail("john@example.com");
        
        // Assert
        assertNotNull(found);
        assertEquals("john", found.getUsername());
    }
    
    @Test
    void testFindByUsername() {
        // Arrange
        User user = new User("john", "john@example.com");
        entityManager.persist(user);
        entityManager.flush();
        
        // Act
        Optional<User> found = userRepository.findByUsername("john");
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals("john@example.com", found.get().getEmail());
    }
    
    @Test
    void testFindByUsernameContaining() {
        // Arrange
        entityManager.persist(new User("john_doe", "john@example.com"));
        entityManager.persist(new User("jane_doe", "jane@example.com"));
        entityManager.persist(new User("bob_smith", "bob@example.com"));
        entityManager.flush();
        
        // Act
        List<User> users = userRepository.findByUsernameContaining("doe");
        
        // Assert
        assertEquals(2, users.size());
    }
    
    @Test
    void testCustomQuery() {
        // Arrange
        User user1 = new User("john", "john@example.com");
        user1.setActive(true);
        User user2 = new User("jane", "jane@example.com");
        user2.setActive(false);
        
        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.flush();
        
        // Act
        List<User> activeUsers = userRepository.findActiveUsers();
        
        // Assert
        assertEquals(1, activeUsers.size());
        assertEquals("john", activeUsers.get(0).getUsername());
    }
    
    @Test
    void testPaginationAndSorting() {
        // Arrange
        for (int i = 1; i <= 10; i++) {
            entityManager.persist(new User("user" + i, "user" + i + "@example.com"));
        }
        entityManager.flush();
        
        // Act
        Pageable pageable = PageRequest.of(0, 5, Sort.by("username").descending());
        Page<User> page = userRepository.findAll(pageable);
        
        // Assert
        assertEquals(5, page.getContent().size());
        assertEquals(10, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals("user9", page.getContent().get(0).getUsername());
    }
}

// Testing with embedded database
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserRepositoryEmbeddedTest {
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testWithH2Database() {
        User user = new User("john", "john@example.com");
        User saved = userRepository.save(user);
        
        assertNotNull(saved.getId());
        assertEquals("john", saved.getUsername());
    }
}

// Testing with Testcontainers
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTestcontainersTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testWithPostgres() {
        User user = new User("john", "john@example.com");
        User saved = userRepository.save(user);
        
        assertNotNull(saved.getId());
        
        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());
    }
}
```

### 7. How do you test REST controllers with MockMvc?

**Answer:**

```java
@WebMvcTest(UserController.class)
class UserControllerMockMvcTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testGetAllUsers() throws Exception {
        // Arrange
        List<User> users = Arrays.asList(
            new User(1L, "john", "john@example.com"),
            new User(2L, "jane", "jane@example.com")
        );
        when(userService.findAll()).thenReturn(users);
        
        // Act & Assert
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].username").value("john"))
            .andExpect(jsonPath("$[1].username").value("jane"))
            .andDo(print());
    }
    
    @Test
    void testGetUserById() throws Exception {
        // Arrange
        User user = new User(1L, "john", "john@example.com");
        when(userService.findById(1L)).thenReturn(user);
        
        // Act & Assert
        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("john"))
            .andExpect(jsonPath("$.email").value("john@example.com"));
    }
    
    @Test
    void testCreateUser() throws Exception {
        // Arrange
        UserRequest request = new UserRequest("john", "john@example.com");
        User savedUser = new User(1L, "john", "john@example.com");
        when(userService.createUser(any(UserRequest.class))).thenReturn(savedUser);
        
        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.username").value("john"));
        
        verify(userService).createUser(any(UserRequest.class));
    }
    
    @Test
    void testCreateUser_ValidationError() throws Exception {
        // Arrange - invalid request (empty username)
        UserRequest request = new UserRequest("", "john@example.com");
        
        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists());
        
        verify(userService, never()).createUser(any());
    }
    
    @Test
    void testUpdateUser() throws Exception {
        // Arrange
        UserRequest request = new UserRequest("john_updated", "john@example.com");
        User updatedUser = new User(1L, "john_updated", "john@example.com");
        when(userService.updateUser(eq(1L), any(UserRequest.class))).thenReturn(updatedUser);
        
        // Act & Assert
        mockMvc.perform(put("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("john_updated"));
    }
    
    @Test
    void testDeleteUser() throws Exception {
        // Arrange
        doNothing().when(userService).deleteUser(1L);
        
        // Act & Assert
        mockMvc.perform(delete("/api/users/1"))
            .andExpect(status().isNoContent());
        
        verify(userService).deleteUser(1L);
    }
    
    @Test
    void testGetUser_NotFound() throws Exception {
        // Arrange
        when(userService.findById(999L))
            .thenThrow(new UserNotFoundException("User not found"));
        
        // Act & Assert
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testSearchUsers() throws Exception {
        // Arrange
        List<User> users = Arrays.asList(new User(1L, "john", "john@example.com"));
        when(userService.searchUsers("john")).thenReturn(users);
        
        // Act & Assert
        mockMvc.perform(get("/api/users/search")
                .param("query", "john"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].username").value("john"));
    }
}

// Testing with custom matchers
class UserControllerCustomMatchersTest {
    
    @Test
    void testWithCustomMatcher() throws Exception {
        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(result -> {
                String content = result.getResponse().getContentAsString();
                User user = objectMapper.readValue(content, User.class);
                assertTrue(user.getEmail().contains("@"));
            });
    }
}
```

### 8. How do you test Spring Security configurations?

**Answer:**

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testPublicEndpoint_NoAuthentication() throws Exception {
        mockMvc.perform(get("/api/public/health"))
            .andExpect(status().isOk());
    }
    
    @Test
    void testProtectedEndpoint_NoAuthentication() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testProtectedEndpoint_WithUser() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminEndpoint_WithAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(username = "user", roles = "USER")
    void testAdminEndpoint_WithUser() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(username = "user")
    void testMethodSecurity() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
            .andExpect(status().isForbidden());
    }
    
    @Test
    void testBasicAuthentication() throws Exception {
        mockMvc.perform(get("/api/users")
                .with(httpBasic("user", "password")))
            .andExpect(status().isOk());
    }
    
    @Test
    void testCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden()); // CSRF token missing
    }
    
    @Test
    @WithMockUser
    void testCsrfProtection_WithToken() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest()); // Passes CSRF, fails validation
    }
}

// Custom security annotation
@Retention(RetentionPolicy.RUNTIME)
@WithMockUser(username = "admin", roles = {"ADMIN", "USER"})
public @interface WithMockAdmin {
}

@SpringBootTest
@AutoConfigureMockMvc
class CustomSecurityAnnotationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockAdmin
    void testWithCustomAnnotation() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk());
    }
}

// Custom UserDetails for testing
@WithUserDetails("john@example.com")
@SpringBootTest
@AutoConfigureMockMvc
class UserDetailsTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        public UserDetailsService userDetailsService() {
            UserDetails user = User.builder()
                .username("john@example.com")
                .password("password")
                .roles("USER")
                .build();
            return new InMemoryUserDetailsManager(user);
        }
    }
    
    @Test
    void testWithUserDetails() throws Exception {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isOk());
    }
}
```

### 9. How do you test asynchronous methods?

**Answer:**

```java
@Service
public class AsyncService {
    
    @Async
    public CompletableFuture<String> asyncMethod() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return CompletableFuture.completedFuture("Result");
    }
    
    @Async
    public void asyncVoidMethod() {
        // Async processing
    }
}

// Testing async methods
@SpringBootTest
@EnableAsync
class AsyncServiceTest {
    
    @Autowired
    private AsyncService asyncService;
    
    @Test
    void testAsyncMethod() throws Exception {
        // Act
        CompletableFuture<String> future = asyncService.asyncMethod();
        
        // Wait for completion
        String result = future.get(2, TimeUnit.SECONDS);
        
        // Assert
        assertEquals("Result", result);
    }
    
    @Test
    void testAsyncMethodWithAwait() throws Exception {
        CompletableFuture<String> future = asyncService.asyncMethod();
        
        // Using Awaitility
        await().atMost(2, TimeUnit.SECONDS)
            .until(future::isDone);
        
        assertEquals("Result", future.get());
    }
    
    @Test
    void testMultipleAsyncCalls() throws Exception {
        CompletableFuture<String> future1 = asyncService.asyncMethod();
        CompletableFuture<String> future2 = asyncService.asyncMethod();
        CompletableFuture<String> future3 = asyncService.asyncMethod();
        
        CompletableFuture.allOf(future1, future2, future3).get(5, TimeUnit.SECONDS);
        
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future3.isDone());
    }
}

// Testing with Awaitility
@ExtendWith(MockitoExtension.class)
class AwaitilityTest {
    
    @Mock
    private MessageQueue messageQueue;
    
    @InjectMocks
    private MessageProcessor processor;
    
    @Test
    void testAsyncProcessing() {
        // Arrange
        Message message = new Message("test");
        when(messageQueue.poll()).thenReturn(null, null, message);
        
        // Act
        processor.startProcessing();
        
        // Assert - wait until message is processed
        await().atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(messageQueue, atLeast(3)).poll());
    }
    
    @Test
    void testPolling() {
        AtomicInteger counter = new AtomicInteger(0);
        
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                counter.set(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        await().atMost(2, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> counter.get() == 10);
    }
}
```

### 10. How do you test exception handling?

**Answer:**

```java
// Global exception handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An error occurred",
            LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

// Testing exception handling
@WebMvcTest(UserController.class)
class ExceptionHandlingTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    void testUserNotFoundException() throws Exception {
        // Arrange
        when(userService.findById(999L))
            .thenThrow(new UserNotFoundException("User not found with id: 999"));
        
        // Act & Assert
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("User not found with id: 999"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
    
    @Test
    void testValidationException() throws Exception {
        // Arrange
        UserRequest request = new UserRequest("", "invalid-email");
        
        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[*].field").exists())
            .andExpect(jsonPath("$.errors[*].message").exists());
    }
    
    @Test
    void testMethodArgumentNotValidException() throws Exception {
        UserRequest request = new UserRequest();
        
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").exists());
    }
}

// Unit test for exception scenarios
@ExtendWith(MockitoExtension.class)
class UserServiceExceptionTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void testFindById_ThrowsException() {
        // Arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> {
            userService.findById(999L);
        });
    }
    
    @Test
    void testCreateUser_DuplicateEmail() {
        // Arrange
        User user = new User("john", "john@example.com");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);
        
        // Act & Assert
        DuplicateEmailException exception = assertThrows(
            DuplicateEmailException.class,
            () -> userService.createUser(user)
        );
        
        assertEquals("Email already exists: john@example.com", exception.getMessage());
    }
    
    @Test
    void testExceptionMessage() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        
        Exception exception = assertThrows(UserNotFoundException.class, () -> {
            userService.findById(1L);
        });
        
        assertTrue(exception.getMessage().contains("User not found"));
    }
}
```

### 11. How do you achieve high test coverage and measure it?

**Answer:**

```java
// Service with comprehensive test coverage
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    
    public Order createOrder(OrderRequest request) {
        validateRequest(request);
        
        Order order = buildOrder(request);
        Order savedOrder = orderRepository.save(order);
        
        try {
            paymentService.processPayment(savedOrder.getId(), request.getPaymentDetails());
            savedOrder.setStatus(OrderStatus.PAID);
        } catch (PaymentException e) {
            savedOrder.setStatus(OrderStatus.PAYMENT_FAILED);
            throw e;
        } finally {
            orderRepository.save(savedOrder);
            notificationService.sendOrderNotification(savedOrder);
        }
        
        return savedOrder;
    }
    
    private void validateRequest(OrderRequest request) {
        if (request.getItems().isEmpty()) {
            throw new ValidationException("Order must have at least one item");
        }
    }
    
    private Order buildOrder(OrderRequest request) {
        Order order = new Order();
        order.setItems(request.getItems());
        order.setStatus(OrderStatus.PENDING);
        return order;
    }
}

// Comprehensive test suite
@ExtendWith(MockitoExtension.class)
class OrderServiceComprehensiveTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentService paymentService;
    
    @Mock
    private NotificationService notificationService;
    
    @InjectMocks
    private OrderService orderService;
    
    private OrderRequest validRequest;
    private Order order;
    
    @BeforeEach
    void setUp() {
        validRequest = new OrderRequest();
        validRequest.setItems(Arrays.asList(new OrderItem()));
        validRequest.setPaymentDetails(new PaymentDetails());
        
        order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PENDING);
    }
    
    @Test
    @DisplayName("Should create order successfully with valid request")
    void testCreateOrder_Success() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doNothing().when(paymentService).processPayment(anyLong(), any());
        doNothing().when(notificationService).sendOrderNotification(any());
        
        // Act
        Order result = orderService.createOrder(validRequest);
        
        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(paymentService).processPayment(1L, validRequest.getPaymentDetails());
        verify(notificationService).sendOrderNotification(any(Order.class));
    }
    
    @Test
    @DisplayName("Should throw ValidationException when items are empty")
    void testCreateOrder_EmptyItems() {
        // Arrange
        validRequest.setItems(Collections.emptyList());
        
        // Act & Assert
        assertThrows(ValidationException.class, () -> {
            orderService.createOrder(validRequest);
        });
        
        verify(orderRepository, never()).save(any());
    }
    
    @Test
    @DisplayName("Should handle payment failure and update order status")
    void testCreateOrder_PaymentFailure() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doThrow(new PaymentException("Payment failed"))
            .when(paymentService).processPayment(anyLong(), any());
        
        // Act & Assert
        assertThrows(PaymentException.class, () -> {
            orderService.createOrder(validRequest);
        });
        
        // Verify order status was updated and notification sent
        verify(orderRepository, times(2)).save(argThat(o -> 
            o.getStatus() == OrderStatus.PAYMENT_FAILED
        ));
        verify(notificationService).sendOrderNotification(any());
    }
    
    @Test
    @DisplayName("Should send notification even when payment fails")
    void testCreateOrder_NotificationSentOnFailure() {
        // Arrange
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        doThrow(new PaymentException("Payment failed"))
            .when(paymentService).processPayment(anyLong(), any());
        
        // Act
        try {
            orderService.createOrder(validRequest);
        } catch (PaymentException e) {
            // Expected
        }
        
        // Assert
        verify(notificationService).sendOrderNotification(any(Order.class));
    }
}

// JaCoCo configuration in pom.xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.10</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>

// Generate coverage report
// mvn clean test jacoco:report

// Coverage report location: target/site/jacoco/index.html
```

### 12. How do you use Testcontainers for integration testing?

**Answer:**

```java
// Testcontainers with PostgreSQL
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTestcontainersTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withInitScript("schema.sql");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testSaveAndFind() {
        User user = new User("john", "john@example.com");
        User saved = userRepository.save(user);
        
        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("john", found.get().getUsername());
    }
}

// Testcontainers with MySQL
@SpringBootTest
@Testcontainers
class MySQLTestcontainersTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}

// Testcontainers with MongoDB
@SpringBootTest
@Testcontainers
class MongoDBTestcontainersTest {
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:6.0")
        .withExposedPorts(27017);
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
    }
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Test
    void testMongoOperations() {
        Document doc = new Document("name", "John");
        mongoTemplate.save(doc, "users");
        
        Document found = mongoTemplate.findById(doc.getObjectId("_id"), Document.class, "users");
        assertNotNull(found);
        assertEquals("John", found.getString("name"));
    }
}

// Testcontainers with Redis
@SpringBootTest
@Testcontainers
class RedisTestcontainersTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Test
    void testRedisOperations() {
        redisTemplate.opsForValue().set("key", "value");
        String value = redisTemplate.opsForValue().get("key");
        assertEquals("value", value);
    }
}

// Testcontainers with Kafka
@SpringBootTest
@Testcontainers
class KafkaTestcontainersTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
    );
    
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Test
    void testKafkaProducer() throws Exception {
        String topic = "test-topic";
        String message = "Hello Kafka";
        
        kafkaTemplate.send(topic, message).get();
        
        // Verify message was sent
        // Add consumer verification logic
    }
}

// Reusable container configuration
@TestConfiguration
public class TestcontainersConfiguration {
    
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:15-alpine");
    }
    
    @Bean
    @ServiceConnection
    public MongoDBContainer mongoContainer() {
        return new MongoDBContainer("mongo:6.0");
    }
}
```
