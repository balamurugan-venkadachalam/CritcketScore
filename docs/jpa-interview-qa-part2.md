# JPA Interview Questions & Answers - Part 2 (Questions 16-50)

## Spring Data JPA

### 16. What is Spring Data JPA and how does it simplify data access?

**Answer:**
Spring Data JPA is a framework that simplifies data access by providing repository abstractions and reducing boilerplate code.

```java
// Traditional DAO approach
@Repository
public class UserDaoImpl implements UserDao {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public User findById(Long id) {
        return entityManager.find(User.class, id);
    }
    
    @Override
    public List<User> findAll() {
        return entityManager.createQuery("SELECT u FROM User u", User.class)
            .getResultList();
    }
    
    @Override
    public User save(User user) {
        if (user.getId() == null) {
            entityManager.persist(user);
            return user;
        } else {
            return entityManager.merge(user);
        }
    }
    
    @Override
    public void delete(User user) {
        entityManager.remove(entityManager.contains(user) ? user : entityManager.merge(user));
    }
    
    @Override
    public List<User> findByUsername(String username) {
        return entityManager.createQuery(
            "SELECT u FROM User u WHERE u.username = :username", User.class)
            .setParameter("username", username)
            .getResultList();
    }
}

// Spring Data JPA approach - no implementation needed!
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Method name query derivation
    List<User> findByUsername(String username);
    
    List<User> findByEmailContaining(String email);
    
    List<User> findByAgeGreaterThan(Integer age);
    
    List<User> findByUsernameAndEmail(String username, String email);
    
    // Custom query
    @Query("SELECT u FROM User u WHERE u.status = :status")
    List<User> findByStatus(@Param("status") String status);
    
    // Native query
    @Query(value = "SELECT * FROM users WHERE created_at > ?1", nativeQuery = true)
    List<User> findRecentUsers(LocalDateTime date);
    
    // Modifying query
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    // Pagination
    Page<User> findByStatus(String status, Pageable pageable);
    
    // Sorting
    List<User> findByStatus(String status, Sort sort);
}

// Usage
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public void demonstrateSpringDataJPA() {
        // Basic CRUD
        User user = new User("john");
        userRepository.save(user);
        
        Optional<User> found = userRepository.findById(1L);
        
        List<User> all = userRepository.findAll();
        
        userRepository.deleteById(1L);
        
        // Query methods
        List<User> johns = userRepository.findByUsername("john");
        
        // Pagination
        Pageable pageable = PageRequest.of(0, 10, Sort.by("username"));
        Page<User> page = userRepository.findByStatus("ACTIVE", pageable);
        
        // Exists check
        boolean exists = userRepository.existsById(1L);
        
        // Count
        long count = userRepository.count();
    }
}
```

### 17. Explain query method naming conventions in Spring Data JPA

**Answer:**
Spring Data JPA uses a built-in query builder mechanism to automatically generate SQL queries based entirely on the method's name. By combining specific prefixes (like `findBy`, `countBy`, `deleteBy`) with entity properties (like `Username`, `Email`) and logical keywords (like `And`, `Or`, `Between`, `LessThan`), you can execute complex queries without writing a single line of JPQL or SQL.

```java
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Find by single property
    List<User> findByUsername(String username);
    // SELECT u FROM User u WHERE u.username = ?1
    
    // Find by multiple properties (AND)
    List<User> findByUsernameAndEmail(String username, String email);
    // WHERE u.username = ?1 AND u.email = ?2
    
    // Find by multiple properties (OR)
    List<User> findByUsernameOrEmail(String username, String email);
    // WHERE u.username = ?1 OR u.email = ?2
    
    // Comparison operators
    List<User> findByAgeGreaterThan(Integer age);
    List<User> findByAgeLessThan(Integer age);
    List<User> findByAgeGreaterThanEqual(Integer age);
    List<User> findByAgeLessThanEqual(Integer age);
    List<User> findByAgeBetween(Integer start, Integer end);
    
    // String operations
    List<User> findByUsernameStartingWith(String prefix);
    List<User> findByUsernameEndingWith(String suffix);
    List<User> findByUsernameContaining(String substring);
    List<User> findByUsernameLike(String pattern);
    
    // Null checks
    List<User> findByEmailIsNull();
    List<User> findByEmailIsNotNull();
    
    // Boolean
    List<User> findByActiveTrue();
    List<User> findByActiveFalse();
    
    // Collection operations
    List<User> findByRolesIn(Collection<Role> roles);
    List<User> findByRolesNotIn(Collection<Role> roles);
    
    // Ordering
    List<User> findByStatusOrderByUsernameAsc(String status);
    List<User> findByStatusOrderByCreatedAtDesc(String status);
    
    // Limiting results
    User findFirstByOrderByCreatedAtDesc();
    List<User> findTop10ByOrderByCreatedAtDesc();
    
    // Distinct
    List<User> findDistinctByUsername(String username);
    
    // Nested properties
    List<User> findByAddressCity(String city);
    // WHERE u.address.city = ?1
    
    List<User> findByAddressCityAndAddressState(String city, String state);
    
    // Ignore case
    List<User> findByUsernameIgnoreCase(String username);
    List<User> findByUsernameAndEmailAllIgnoreCase(String username, String email);
    
    // Count queries
    long countByStatus(String status);
    
    // Exists queries
    boolean existsByUsername(String username);
    
    // Delete queries
    long deleteByStatus(String status);
    List<User> removeByStatus(String status);
}

// Complex query examples
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Multiple conditions with ordering
    List<Order> findByStatusAndTotalAmountGreaterThanOrderByCreatedAtDesc(
        String status, BigDecimal amount);
    
    // Date range
    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Nested property with comparison
    List<Order> findByUserAgeGreaterThanAndStatusIn(
        Integer age, List<String> statuses);
    
    // Complex nested query
    List<Order> findByUserAddressCityAndTotalAmountGreaterThan(
        String city, BigDecimal amount);
}
```

**Query Keywords:**

| Keyword | Example | JPQL |
|---------|---------|------|
| And | findByNameAndAge | WHERE name = ?1 AND age = ?2 |
| Or | findByNameOrAge | WHERE name = ?1 OR age = ?2 |
| Is, Equals | findByName | WHERE name = ?1 |
| Between | findByAgeBetween | WHERE age BETWEEN ?1 AND ?2 |
| LessThan | findByAgeLessThan | WHERE age < ?1 |
| GreaterThan | findByAgeGreaterThan | WHERE age > ?1 |
| Like | findByNameLike | WHERE name LIKE ?1 |
| StartingWith | findByNameStartingWith | WHERE name LIKE ?1% |
| EndingWith | findByNameEndingWith | WHERE name LIKE %?1 |
| Containing | findByNameContaining | WHERE name LIKE %?1% |
| OrderBy | findByAgeOrderByNameDesc | ORDER BY name DESC |
| Not | findByNameNot | WHERE name <> ?1 |
| In | findByAgeIn | WHERE age IN ?1 |
| NotIn | findByAgeNotIn | WHERE age NOT IN ?1 |
| True | findByActiveTrue | WHERE active = true |
| False | findByActiveFalse | WHERE active = false |
| IsNull | findByAgeIsNull | WHERE age IS NULL |
| IsNotNull | findByAgeIsNotNull | WHERE age IS NOT NULL |

### 18. How do you implement custom repository methods in Spring Data JPA?

**Answer:**
While Spring Data JPA covers most standard queries automatically, you sometimes need highly customized, complex JDBC/JPA logic. 
To implement a custom repository method, you follow a 3-step convention:
1. Create an interface (e.g., `UserRepositoryCustom`) declaring your custom method.
2. Create an implementation class exactly named with the `Impl` suffix (e.g., `UserRepositoryCustomImpl`) that implements the interface using a standard `EntityManager`.
3. Have your primary Spring Data Repository interface extend both `JpaRepository` and your newly created custom interface. Spring will automatically weave the implementation together at runtime.

```java
// Step 1: Define custom interface
public interface UserRepositoryCustom {
    List<User> findByCustomCriteria(UserSearchCriteria criteria);
    List<User> complexQuery(String param1, String param2);
}

// Step 2: Implement custom interface
@Repository
public class UserRepositoryCustomImpl implements UserRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<User> findByCustomCriteria(UserSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> user = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getUsername() != null) {
            predicates.add(cb.like(user.get("username"), 
                "%" + criteria.getUsername() + "%"));
        }
        
        if (criteria.getMinAge() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                user.get("age"), criteria.getMinAge()));
        }
        
        if (criteria.getStatus() != null) {
            predicates.add(cb.equal(user.get("status"), criteria.getStatus()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    public List<User> complexQuery(String param1, String param2) {
        return entityManager.createQuery(
            "SELECT u FROM User u " +
            "WHERE u.field1 = :param1 " +
            "AND EXISTS (SELECT o FROM Order o WHERE o.user = u AND o.status = :param2)",
            User.class)
            .setParameter("param1", param1)
            .setParameter("param2", param2)
            .getResultList();
    }
}

// Step 3: Extend both interfaces
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {
    // Standard Spring Data JPA methods
    List<User> findByUsername(String username);
}

// Usage
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public void useCustomMethods() {
        // Standard method
        List<User> users = userRepository.findByUsername("john");
        
        // Custom method
        UserSearchCriteria criteria = new UserSearchCriteria();
        criteria.setUsername("john");
        criteria.setMinAge(18);
        criteria.setStatus("ACTIVE");
        
        List<User> filtered = userRepository.findByCustomCriteria(criteria);
    }
}

// Advanced: Specifications for dynamic queries
public class UserSpecifications {
    
    public static Specification<User> hasUsername(String username) {
        return (root, query, cb) -> 
            username == null ? null : cb.equal(root.get("username"), username);
    }
    
    public static Specification<User> hasStatus(String status) {
        return (root, query, cb) -> 
            status == null ? null : cb.equal(root.get("status"), status);
    }
    
    public static Specification<User> ageGreaterThan(Integer age) {
        return (root, query, cb) -> 
            age == null ? null : cb.greaterThan(root.get("age"), age);
    }
    
    public static Specification<User> hasEmail(String email) {
        return (root, query, cb) -> 
            email == null ? null : cb.like(root.get("email"), "%" + email + "%");
    }
}

// Repository with Specification support
public interface UserRepository extends JpaRepository<User, Long>, 
                                       JpaSpecificationExecutor<User> {
}

// Usage with Specifications
@Service
public class UserSearchService {
    
    @Autowired
    private UserRepository userRepository;
    
    public List<User> searchUsers(UserSearchCriteria criteria) {
        Specification<User> spec = Specification.where(null);
        
        if (criteria.getUsername() != null) {
            spec = spec.and(UserSpecifications.hasUsername(criteria.getUsername()));
        }
        
        if (criteria.getStatus() != null) {
            spec = spec.and(UserSpecifications.hasStatus(criteria.getStatus()));
        }
        
        if (criteria.getMinAge() != null) {
            spec = spec.and(UserSpecifications.ageGreaterThan(criteria.getMinAge()));
        }
        
        return userRepository.findAll(spec);
    }
    
    public Page<User> searchUsersWithPagination(UserSearchCriteria criteria, 
                                                 Pageable pageable) {
        Specification<User> spec = buildSpecification(criteria);
        return userRepository.findAll(spec, pageable);
    }
}
```

### 19. Explain @Query annotation and its features

**Answer:**
The `@Query` annotation allows you to explicitly define raw JPQL or native SQL queries directly above a repository method. 
It bypasses the method-naming convention, which is incredibly useful for executing highly complex queries with multiple joins, nested subqueries, or bulk update/delete operations (`@Modifying`). It supports both positional parameters (`?1`) and named parameters (`@Param("name")`).

```java
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Basic JPQL query
    @Query("SELECT u FROM User u WHERE u.status = :status")
    List<User> findByStatus(@Param("status") String status);
    
    // Positional parameters
    @Query("SELECT u FROM User u WHERE u.username = ?1 AND u.email = ?2")
    List<User> findByUsernameAndEmail(String username, String email);
    
    // Named parameters (preferred)
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.email = :email")
    List<User> findByUsernameAndEmailNamed(
        @Param("username") String username,
        @Param("email") String email);
    
    // Native SQL query
    @Query(value = "SELECT * FROM users WHERE status = ?1", nativeQuery = true)
    List<User> findByStatusNative(String status);
    
    // JOIN query
    @Query("SELECT u FROM User u JOIN u.orders o WHERE o.status = :status")
    List<User> findUsersWithOrderStatus(@Param("status") String status);
    
    // JOIN FETCH (solve N+1)
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.orders")
    List<User> findAllWithOrders();
    
    // Projection - DTO
    @Query("SELECT new com.example.dto.UserDTO(u.id, u.username, u.email) " +
           "FROM User u WHERE u.status = :status")
    List<UserDTO> findUserDTOs(@Param("status") String status);
    
    // Aggregation
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countByStatus(@Param("status") String status);
    
    @Query("SELECT u.status, COUNT(u) FROM User u GROUP BY u.status")
    List<Object[]> countByStatusGrouped();
    
    // Subquery
    @Query("SELECT u FROM User u WHERE u.id IN " +
           "(SELECT o.user.id FROM Order o WHERE o.totalAmount > :amount)")
    List<User> findUsersWithHighValueOrders(@Param("amount") BigDecimal amount);
    
    // Modifying query
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    @Modifying
    @Query("DELETE FROM User u WHERE u.status = :status")
    int deleteByStatus(@Param("status") String status);
    
    // Pagination with @Query
    @Query("SELECT u FROM User u WHERE u.status = :status")
    Page<User> findByStatusPaged(@Param("status") String status, Pageable pageable);
    
    // Sorting with @Query
    @Query("SELECT u FROM User u WHERE u.status = :status")
    List<User> findByStatusSorted(@Param("status") String status, Sort sort);
    
    // SpEL expressions
    @Query("SELECT u FROM #{#entityName} u WHERE u.status = :status")
    List<User> findByStatusWithSpEL(@Param("status") String status);
    
    // Collection parameter
    @Query("SELECT u FROM User u WHERE u.id IN :ids")
    List<User> findByIds(@Param("ids") List<Long> ids);
    
    // LIKE query
    @Query("SELECT u FROM User u WHERE u.username LIKE %:username%")
    List<User> searchByUsername(@Param("username") String username);
    
    // CASE WHEN
    @Query("SELECT u, " +
           "CASE WHEN u.age < 18 THEN 'MINOR' " +
           "     WHEN u.age < 65 THEN 'ADULT' " +
           "     ELSE 'SENIOR' END " +
           "FROM User u")
    List<Object[]> findUsersWithAgeCategory();
    
    // Native query with pagination
    @Query(value = "SELECT * FROM users WHERE status = :status",
           countQuery = "SELECT COUNT(*) FROM users WHERE status = :status",
           nativeQuery = true)
    Page<User> findByStatusNativePaged(@Param("status") String status, 
                                        Pageable pageable);
}

// Usage
@Service
public class QueryExamplesService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Transactional
    public void demonstrateModifyingQueries() {
        // Update query
        int updated = userRepository.updateStatus(1L, "INACTIVE");
        
        // Delete query
        int deleted = userRepository.deleteByStatus("INACTIVE");
    }
    
    public void demonstratePagination() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("username").ascending());
        Page<User> page = userRepository.findByStatusPaged("ACTIVE", pageable);
        
        System.out.println("Total pages: " + page.getTotalPages());
        System.out.println("Total elements: " + page.getTotalElements());
        System.out.println("Current page: " + page.getNumber());
        System.out.println("Page size: " + page.getSize());
    }
}
```

### 20. What are Projections in Spring Data JPA?

**Answer:**
Projections allow you to fetch only a specific subset of columns from the database instead of loading the entire Entity. This massively optimizes memory and network performance for read-heavy operations.
Spring Data JPA supports:
- **Interface-based projections:** Define an interface with getter methods matching the desired fields.
- **Class-based projections (DTOs):** Define a POJO and use JPQL constructor expressions (`SELECT new com.app.MyDTO(...)`) to hydrate it.
- **Dynamic Projections:** Pass the desired Class/Interface type as a parameter to the repository method.

```java
// Interface-based projection
public interface UserSummary {
    String getUsername();
    String getEmail();
    
    // Nested projection
    AddressSummary getAddress();
    
    interface AddressSummary {
        String getCity();
        String getState();
    }
}

// Closed projection (only specified properties)
public interface UserBasicInfo {
    String getUsername();
    String getEmail();
}

// Open projection (with @Value and SpEL)
public interface UserFullName {
    @Value("#{target.firstName + ' ' + target.lastName}")
    String getFullName();
    
    @Value("#{target.orders.size()}")
    int getOrderCount();
}

// Class-based projection (DTO)
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    
    public UserDTO(Long id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
    }
    
    // Getters and setters
}

// Repository with projections
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Interface projection
    List<UserSummary> findByStatus(String status);
    
    // Class projection
    @Query("SELECT new com.example.dto.UserDTO(u.id, u.username, u.email) " +
           "FROM User u WHERE u.status = :status")
    List<UserDTO> findUserDTOsByStatus(@Param("status") String status);
    
    // Dynamic projection
    <T> List<T> findByUsername(String username, Class<T> type);
    
    // Projection with pagination
    Page<UserSummary> findByStatus(String status, Pageable pageable);
}

// Usage
@Service
public class ProjectionService {
    
    @Autowired
    private UserRepository userRepository;
    
    public void demonstrateProjections() {
        // Interface projection
        List<UserSummary> summaries = userRepository.findByStatus("ACTIVE");
        summaries.forEach(summary -> {
            System.out.println(summary.getUsername());
            System.out.println(summary.getEmail());
            System.out.println(summary.getAddress().getCity());
        });
        
        // Class projection
        List<UserDTO> dtos = userRepository.findUserDTOsByStatus("ACTIVE");
        
        // Dynamic projection
        List<UserSummary> summaries2 = 
            userRepository.findByUsername("john", UserSummary.class);
        List<UserBasicInfo> basicInfos = 
            userRepository.findByUsername("john", UserBasicInfo.class);
    }
}

// Advanced: Custom projection with @SqlResultSetMapping
@Entity
@SqlResultSetMapping(
    name = "UserStatsMapping",
    classes = @ConstructorResult(
        targetClass = UserStats.class,
        columns = {
            @ColumnResult(name = "username", type = String.class),
            @ColumnResult(name = "order_count", type = Long.class),
            @ColumnResult(name = "total_amount", type = BigDecimal.class)
        }
    )
)
public class User {
    // Entity fields
}

public class UserStats {
    private String username;
    private Long orderCount;
    private BigDecimal totalAmount;
    
    public UserStats(String username, Long orderCount, BigDecimal totalAmount) {
        this.username = username;
        this.orderCount = orderCount;
        this.totalAmount = totalAmount;
    }
}

@Repository
public class UserStatsRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<UserStats> getUserStats() {
        return entityManager.createNativeQuery(
            "SELECT u.username, " +
            "       COUNT(o.id) as order_count, " +
            "       SUM(o.total_amount) as total_amount " +
            "FROM users u " +
            "LEFT JOIN orders o ON u.id = o.user_id " +
            "GROUP BY u.username",
            "UserStatsMapping"
        ).getResultList();
    }
}
```

### 21. How do you implement auditing in JPA?

**Answer:**
JPA Auditing automatically tracks and populates "who" and "when" an entity was created or modified. 
It is enabled by adding `@EnableJpaAuditing` to a configuration class and assigning `@EntityListeners(AuditingEntityListener.class)` to the entities. It provides four core annotations: `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, and `@LastModifiedBy`. For the latter two to work, you must provide an `AuditorAware` bean to extract the current user (e.g., from the Spring Security context).

```java
// Enable JPA Auditing
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            // Get current user from security context
            Authentication authentication = 
                SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }
            
            return Optional.of(authentication.getName());
        };
    }
}

// Base auditing entity
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    
    // Getters and setters
}

// Entity using auditing
@Entity
public class User extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;
    private String email;
    
    // Other fields
}

// Custom auditing with @PrePersist and @PreUpdate
@Entity
@EntityListeners(CustomAuditListener.class)
public class Order {
    @Id
    private Long id;
    
    private String orderNumber;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private String createdBy;
}

public class CustomAuditListener {
    
    @PrePersist
    public void prePersist(Object entity) {
        if (entity instanceof Order) {
            Order order = (Order) entity;
            order.setCreatedAt(LocalDateTime.now());
            order.setCreatedBy(getCurrentUser());
        }
    }
    
    @PreUpdate
    public void preUpdate(Object entity) {
        if (entity instanceof Order) {
            Order order = (Order) entity;
            order.setUpdatedAt(LocalDateTime.now());
        }
    }
    
    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}

// Advanced: Revision auditing with Envers
@Configuration
@EnableJpaRepositories
public class EnversConfig {
    
    @Bean
    public RevisionListener revisionListener() {
        return new CustomRevisionListener();
    }
}

@Entity
@Audited
public class Product {
    @Id
    private Long id;
    
    private String name;
    private BigDecimal price;
    
    @NotAudited
    private String internalNotes;
}

@Entity
@RevisionEntity(CustomRevisionListener.class)
public class CustomRevisionEntity extends DefaultRevisionEntity {
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "ip_address")
    private String ipAddress;
}

public class CustomRevisionListener implements RevisionListener {
    
    @Override
    public void newRevision(Object revisionEntity) {
        CustomRevisionEntity revision = (CustomRevisionEntity) revisionEntity;
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            revision.setUsername(auth.getName());
        }
        
        // Get IP address from request
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            revision.setIpAddress(request.getRemoteAddr());
        }
    }
}

// Query audit history
@Service
public class AuditService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Number> getRevisions(Long productId) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        return auditReader.getRevisions(Product.class, productId);
    }
    
    public Product getProductAtRevision(Long productId, Number revision) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        return auditReader.find(Product.class, productId, revision);
    }
    
    public List<Product> getProductHistory(Long productId) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        List<Number> revisions = auditReader.getRevisions(Product.class, productId);
        
        return revisions.stream()
            .map(rev -> auditReader.find(Product.class, productId, rev))
            .collect(Collectors.toList());
    }
}
```

### 22. How do you handle soft deletes in JPA?

**Answer:**
Soft deletes involve marking an entity as deleted (e.g. setting an `isDeleted` flag or `deletedAt` timestamp) without physically removing the data row from the database. This is critical for data retention and auditing.
Hibernate achieves this seamlessly with two annotations at the Class level: `@SQLDelete` (which intercepts and overwrites any `delete()` operations into an `UPDATE` statement) and `@Where` (which automatically filters out soft-deleted records globally on all subsequent `SELECT` queries).

```java
// Approach 1: Using @Where annotation
@Entity
@Where(clause = "deleted = false")
public class User {
    @Id
    private Long id;
    
    private String username;
    
    @Column(name = "deleted")
    private boolean deleted = false;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Automatically filters deleted = false
    List<User> findByUsername(String username);
    
    // Include deleted records
    @Query("SELECT u FROM User u WHERE u.username = :username")
    List<User> findByUsernameIncludingDeleted(@Param("username") String username);
}

// Approach 2: Using @SQLDelete and @Where
@Entity
@SQLDelete(sql = "UPDATE users SET deleted = true, deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted = false")
public class Product {
    @Id
    private Long id;
    
    private String name;
    
    private boolean deleted = false;
    
    private LocalDateTime deletedAt;
}

// Approach 3: Custom repository implementation
public interface SoftDeleteRepository<T, ID> extends JpaRepository<T, ID> {
    void softDelete(ID id);
    void softDelete(T entity);
    List<T> findAllIncludingDeleted();
    Optional<T> findByIdIncludingDeleted(ID id);
}

@NoRepositoryBean
public class SoftDeleteRepositoryImpl<T, ID> 
        extends SimpleJpaRepository<T, ID> 
        implements SoftDeleteRepository<T, ID> {
    
    private final EntityManager entityManager;
    
    public SoftDeleteRepositoryImpl(JpaEntityInformation<T, ?> entityInformation,
                                   EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }
    
    @Override
    @Transactional
    public void softDelete(ID id) {
        T entity = findById(id).orElseThrow();
        softDelete(entity);
    }
    
    @Override
    @Transactional
    public void softDelete(T entity) {
        try {
            Field deletedField = entity.getClass().getDeclaredField("deleted");
            deletedField.setAccessible(true);
            deletedField.set(entity, true);
            
            Field deletedAtField = entity.getClass().getDeclaredField("deletedAt");
            deletedAtField.setAccessible(true);
            deletedAtField.set(entity, LocalDateTime.now());
            
            entityManager.merge(entity);
        } catch (Exception e) {
            throw new RuntimeException("Soft delete failed", e);
        }
    }
    
    @Override
    public List<T> findAllIncludingDeleted() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        query.select(root);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    public Optional<T> findByIdIncludingDeleted(ID id) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(getDomainClass());
        Root<T> root = query.from(getDomainClass());
        
        query.select(root).where(cb.equal(root.get("id"), id));
        
        try {
            return Optional.of(entityManager.createQuery(query).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}

// Enable custom repository
@Configuration
@EnableJpaRepositories(
    repositoryBaseClass = SoftDeleteRepositoryImpl.class
)
public class JpaConfig {
}

// Usage
public interface UserRepository extends SoftDeleteRepository<User, Long> {
    List<User> findByUsername(String username);
}

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public void demonstrateSoftDelete() {
        // Soft delete
        userRepository.softDelete(1L);
        
        // Find active users only
        List<User> activeUsers = userRepository.findAll();
        
        // Find including deleted
        List<User> allUsers = userRepository.findAllIncludingDeleted();
        
        // Restore deleted user
        Optional<User> deleted = userRepository.findByIdIncludingDeleted(1L);
        deleted.ifPresent(user -> {
            user.setDeleted(false);
            user.setDeletedAt(null);
            userRepository.save(user);
        });
    }
}

// Approach 4: Using Hibernate Filters
@Entity
@FilterDef(name = "deletedFilter", parameters = @ParamDef(name = "isDeleted", type = "boolean"))
@Filter(name = "deletedFilter", condition = "deleted = :isDeleted")
public class Order {
    @Id
    private Long id;
    
    private boolean deleted = false;
}

@Service
public class OrderService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Order> findActiveOrders() {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter("deletedFilter");
        filter.setParameter("isDeleted", false);
        
        List<Order> orders = entityManager
            .createQuery("SELECT o FROM Order o", Order.class)
            .getResultList();
        
        session.disableFilter("deletedFilter");
        return orders;
    }
}
```

### 23. Explain Criteria API and its advantages

**Answer:**
The Criteria API is a type-safe, entirely programmatic framework for constructing JPQL queries via Java objects rather than raw strings. It operates by building query trees dynamically using `CriteriaBuilder`.
**Advantages:** It completely eliminates runtime syntax errors, is deeply type-safe (especially when combined with the JPA Metamodel), and excels at dynamically stitching together optional search filters or complex predicates based on user input.

```java
@Service
public class CriteriaAPIExamples {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Basic query
    public List<User> findAllUsers() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        query.select(root);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // WHERE clause
    public List<User> findByUsername(String username) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.select(root)
             .where(cb.equal(root.get("username"), username));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // Multiple conditions (AND)
    public List<User> findByUsernameAndStatus(String username, String status) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        Predicate usernamePredicate = cb.equal(root.get("username"), username);
        Predicate statusPredicate = cb.equal(root.get("status"), status);
        
        query.select(root)
             .where(cb.and(usernamePredicate, statusPredicate));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // OR condition
    public List<User> findByUsernameOrEmail(String username, String email) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.select(root)
             .where(cb.or(
                 cb.equal(root.get("username"), username),
                 cb.equal(root.get("email"), email)
             ));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // LIKE query
    public List<User> searchByUsername(String searchTerm) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.select(root)
             .where(cb.like(root.get("username"), "%" + searchTerm + "%"));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // Comparison operators
    public List<User> findByAgeGreaterThan(Integer age) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.select(root)
             .where(cb.greaterThan(root.get("age"), age));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // BETWEEN
    public List<User> findByAgeBetween(Integer minAge, Integer maxAge) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.select(root)
             .where(cb.between(root.get("age"), minAge, maxAge));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // IN clause
    public List<User> findByStatusIn(List<String> statuses) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.select(root)
             .where(root.get("status").in(statuses));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // JOIN
    public List<User> findUsersWithOrders() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        Join<User, Order> orders = root.join("orders");
        
        query.select(root).distinct(true);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // LEFT JOIN
    public List<User> findAllUsersWithOptionalOrders() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        root.join("orders", JoinType.LEFT);
        
        query.select(root).distinct(true);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // ORDER BY
    public List<User> findAllOrderedByUsername() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        query.select(root)
             .orderBy(cb.asc(root.get("username")));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // Aggregation
    public Long countUsers() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<User> root = query.from(User.class);
        
        query.select(cb.count(root));
        
        return entityManager.createQuery(query).getSingleResult();
    }
    
    // GROUP BY
    public List<Object[]> countUsersByStatus() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<User> root = query.from(User.class);
        
        query.multiselect(root.get("status"), cb.count(root))
             .groupBy(root.get("status"));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // HAVING
    public List<Object[]> findStatusWithMoreThanNUsers(Long n) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<User> root = query.from(User.class);
        
        query.multiselect(root.get("status"), cb.count(root))
             .groupBy(root.get("status"))
             .having(cb.greaterThan(cb.count(root), n));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // Subquery
    public List<User> findUsersWithHighValueOrders(BigDecimal amount) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<Order> orderRoot = subquery.from(Order.class);
        subquery.select(orderRoot.get("user").get("id"))
                .where(cb.greaterThan(orderRoot.get("totalAmount"), amount));
        
        query.select(root)
             .where(root.get("id").in(subquery));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // Dynamic query builder
    public List<User> dynamicSearch(UserSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getUsername() != null) {
            predicates.add(cb.like(root.get("username"), 
                "%" + criteria.getUsername() + "%"));
        }
        
        if (criteria.getEmail() != null) {
            predicates.add(cb.like(root.get("email"), 
                "%" + criteria.getEmail() + "%"));
        }
        
        if (criteria.getMinAge() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                root.get("age"), criteria.getMinAge()));
        }
        
        if (criteria.getMaxAge() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                root.get("age"), criteria.getMaxAge()));
        }
        
        if (criteria.getStatuses() != null && !criteria.getStatuses().isEmpty()) {
            predicates.add(root.get("status").in(criteria.getStatuses()));
        }
        
        query.select(root)
             .where(predicates.toArray(new Predicate[0]));
        
        // Apply sorting
        if (criteria.getSortBy() != null) {
            if ("DESC".equalsIgnoreCase(criteria.getSortOrder())) {
                query.orderBy(cb.desc(root.get(criteria.getSortBy())));
            } else {
                query.orderBy(cb.asc(root.get(criteria.getSortBy())));
            }
        }
        
        TypedQuery<User> typedQuery = entityManager.createQuery(query);
        
        // Apply pagination
        if (criteria.getPage() != null && criteria.getSize() != null) {
            typedQuery.setFirstResult(criteria.getPage() * criteria.getSize());
            typedQuery.setMaxResults(criteria.getSize());
        }
        
        return typedQuery.getResultList();
    }
}
```

**Criteria API Advantages:**
- Type-safe queries
- Dynamic query building
- Compile-time checking
- Refactoring-friendly
- No string concatenation
- IDE support with auto-completion

### 24. How do you implement pagination and sorting in JPA?

**Answer:**
Spring Data JPA elegantly abstracts away generic database LIMIT/OFFSET syntax across different vendors through the `Pageable` and `Sort` interfaces.
You simply pass a `PageRequest` object (dictating the page number, size, and sorting direction) into standard repository methods. Standard queries will return a `Page<T>` wrapper, automatically triggering a secondary backend query exclusively to calculate the total element count for front-end rendering. For raw JPA without Spring Data, you manually call `setFirstResult(offset)` and `setMaxResults(limit)` directly on the `TypedQuery` object.

```java
// Spring Data JPA pagination
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Pagination
    Page<User> findByStatus(String status, Pageable pageable);
    
    // Slice (doesn't count total)
    Slice<User> findByEmail(String email, Pageable pageable);
    
    // List with pagination
    List<User> findByUsername(String username, Pageable pageable);
    
    // Sorting only
    List<User> findByStatus(String status, Sort sort);
}

@Service
public class PaginationService {
    
    @Autowired
    private UserRepository userRepository;
    
    public void demonstratePagination() {
        // Page 0, size 10, sort by username ascending
        Pageable pageable = PageRequest.of(0, 10, Sort.by("username").ascending());
        Page<User> page = userRepository.findByStatus("ACTIVE", pageable);
        
        // Page information
        System.out.println("Total pages: " + page.getTotalPages());
        System.out.println("Total elements: " + page.getTotalElements());
        System.out.println("Current page: " + page.getNumber());
        System.out.println("Page size: " + page.getSize());
        System.out.println("Has next: " + page.hasNext());
        System.out.println("Has previous: " + page.hasPrevious());
        
        // Content
        List<User> users = page.getContent();
    }
    
    public void demonstrateSorting() {
        // Single property
        Sort sort = Sort.by("username").ascending();
        
        // Multiple properties
        Sort multiSort = Sort.by("status").ascending()
                            .and(Sort.by("createdAt").descending());
        
        // Sort with direction
        Sort directionSort = Sort.by(Sort.Direction.DESC, "createdAt");
        
        // Complex sorting
        Sort complexSort = Sort.by(
            Sort.Order.asc("status"),
            Sort.Order.desc("createdAt"),
            Sort.Order.asc("username").ignoreCase()
        );
        
        Pageable pageable = PageRequest.of(0, 10, complexSort);
        Page<User> page = userRepository.findAll(pageable);
    }
    
    public void demonstrateSlice() {
        Pageable pageable = PageRequest.of(0, 10);
        Slice<User> slice = userRepository.findByEmail("@example.com", pageable);
        
        // Slice doesn't know total count (more efficient)
        System.out.println("Has next: " + slice.hasNext());
        System.out.println("Has previous: " + slice.hasPrevious());
        System.out.println("Number: " + slice.getNumber());
        
        // No getTotalPages() or getTotalElements()
    }
}

// Manual pagination with EntityManager
@Service
public class ManualPaginationService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public Page<User> findUsersManually(int page, int size) {
        // Get total count
        Long total = entityManager
            .createQuery("SELECT COUNT(u) FROM User u", Long.class)
            .getSingleResult();
        
        // Get page data
        List<User> users = entityManager
            .createQuery("SELECT u FROM User u ORDER BY u.username", User.class)
            .setFirstResult(page * size)
            .setMaxResults(size)
            .getResultList();
        
        return new PageImpl<>(users, PageRequest.of(page, size), total);
    }
    
    // Criteria API pagination
    public Page<User> findWithCriteria(UserSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        
        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);
        countQuery.select(cb.count(countRoot));
        // Apply same filters as main query
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        // Data query
        CriteriaQuery<User> dataQuery = cb.createQuery(User.class);
        Root<User> dataRoot = dataQuery.from(User.class);
        dataQuery.select(dataRoot);
        // Apply filters and sorting
        
        List<User> users = entityManager.createQuery(dataQuery)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
        
        return new PageImpl<>(users, pageable, total);
    }
}

// REST Controller with pagination
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserRepository userRepository;
    
    @GetMapping
    public Page<UserDTO> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("DESC") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        
        return userRepository.findAll(pageable)
            .map(this::convertToDTO);
    }
    
    // Using Pageable parameter directly
    @GetMapping("/v2")
    public Page<UserDTO> getUsersV2(Pageable pageable) {
        return userRepository.findAll(pageable)
            .map(this::convertToDTO);
    }
    
    // Custom pagination response
    @GetMapping("/v3")
    public ResponseEntity<PaginationResponse<UserDTO>> getUsersV3(
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        
        Page<User> page = userRepository.findAll(pageable);
        
        PaginationResponse<UserDTO> response = new PaginationResponse<>();
        response.setContent(page.getContent().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList()));
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalPages(page.getTotalPages());
        response.setTotalElements(page.getTotalElements());
        
        return ResponseEntity.ok(response);
    }
}

// Keyset pagination (for better performance on large datasets)
@Service
public class KeysetPaginationService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<User> findNextPage(Long lastId, int size) {
        return entityManager
            .createQuery(
                "SELECT u FROM User u WHERE u.id > :lastId ORDER BY u.id", 
                User.class)
            .setParameter("lastId", lastId)
            .setMaxResults(size)
            .getResultList();
    }
    
    public List<User> findPreviousPage(Long firstId, int size) {
        return entityManager
            .createQuery(
                "SELECT u FROM User u WHERE u.id < :firstId ORDER BY u.id DESC", 
                User.class)
            .setParameter("firstId", firstId)
            .setMaxResults(size)
            .getResultList();
    }
}
```

### 25. What are the best practices for JPA performance optimization?

**Answer:**
The most critical best practices revolve around minimizing the number of database roundtrips and optimizing payload size:
- **Avoid N+1 queries** aggressively via `JOIN FETCH` or `@EntityGraph`.
- **Use Projections** (DTOs) heavily on read-only endpoints to limit the number of columns fetched, preventing bloated entities.
- **Implement Second-Level Caching** and query caching for lookup/reference data that rarely changes.
- **Apply Pagination** for lists and avoid loading giant collections into server memory.
- **Prefer `@DynamicUpdate`** on tables with a high column count to minimize network impact.
- **Enable Hibernate Batch Processing** for bulk inserts (`spring.jpa.properties.hibernate.jdbc.batch_size`).

```java
@Service
public class PerformanceOptimizationBestPractices {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // 1. Use appropriate fetch strategies
    @Entity
    public class User {
        @OneToMany(fetch = FetchType.LAZY) // Good
        private List<Order> orders;
        
        @ManyToOne(fetch = FetchType.LAZY) // Override default EAGER
        private Department department;
    }
    
    // 2. Use JOIN FETCH to avoid N+1
    public List<User> findUsersWithOrders() {
        return entityManager
            .createQuery(
                "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.orders", 
                User.class)
            .getResultList();
    }
    
    // 3. Use projections for read-only queries
    @Query("SELECT new com.example.dto.UserDTO(u.id, u.username) FROM User u")
    List<UserDTO> findUserDTOs();
    
    // 4. Batch fetching
    @Entity
    @BatchSize(size = 10)
    public class Order {
        // Fetches in batches of 10
    }
    
    // 5. Use @Transactional(readOnly = true) for queries
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }
    
    // 6. Batch inserts
    @Transactional
    public void batchInsert(List<User> users) {
        int batchSize = 50;
        for (int i = 0; i < users.size(); i++) {
            entityManager.persist(users.get(i));
            if (i % batchSize == 0 && i > 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
    
    // 7. Use pagination for large result sets
    public Page<User> findAllPaginated(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    
    // 8. Avoid loading entities when not needed
    @Transactional
    public void updateUserStatus(Long userId, String status) {
        // Bad: loads entire entity
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(status);
        
        // Good: bulk update
        entityManager.createQuery(
            "UPDATE User u SET u.status = :status WHERE u.id = :id")
            .setParameter("status", status)
            .setParameter("id", userId)
            .executeUpdate();
    }
    
    // 9. Use second-level cache
    @Entity
    @Cacheable
    @org.hibernate.annotations.Cache(
        usage = CacheConcurrencyStrategy.READ_WRITE
    )
    public class Product {
        // Cached entity
    }
    
    // 10. Use query cache
    public List<User> findActiveUsers() {
        return entityManager
            .createQuery("SELECT u FROM User u WHERE u.status = 'ACTIVE'", User.class)
            .setHint("org.hibernate.cacheable", true)
            .getResultList();
    }
    
    // 11. Avoid unnecessary flush()
    @Transactional
    public void saveMultiple(List<User> users) {
        entityManager.setFlushMode(FlushModeType.COMMIT);
        users.forEach(entityManager::persist);
        // Flushes only at commit
    }
    
    // 12. Use StatelessSession for bulk operations
    public void bulkInsert(List<User> users) {
        Session session = entityManager.unwrap(Session.class);
        StatelessSession statelessSession = session.getSessionFactory()
            .openStatelessSession();
        
        Transaction tx = statelessSession.beginTransaction();
        users.forEach(statelessSession::insert);
        tx.commit();
        statelessSession.close();
    }
    
    // 13. Index frequently queried columns
    @Entity
    @Table(indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_status_created", columnList = "status, created_at")
    })
    public class User {
        // Indexed columns
    }
    
    // 14. Use native queries for complex operations
    @Query(value = "SELECT * FROM users WHERE " +
                   "MATCH(username, email) AGAINST(?1 IN BOOLEAN MODE)",
           nativeQuery = true)
    List<User> fullTextSearch(String searchTerm);
    
    // 15. Monitor and analyze queries
    public void enableQueryLogging() {
        // application.properties
        // spring.jpa.show-sql=true
        // spring.jpa.properties.hibernate.format_sql=true
        // spring.jpa.properties.hibernate.use_sql_comments=true
        // logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
    }
}

// Configuration for performance
@Configuration
public class JpaPerformanceConfig {
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = 
            new LocalContainerEntityManagerFactoryBean();
        
        Properties properties = new Properties();
        
        // Batch processing
        properties.setProperty("hibernate.jdbc.batch_size", "50");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        
        // Connection pooling
        properties.setProperty("hibernate.hikari.maximumPoolSize", "20");
        properties.setProperty("hibernate.hikari.minimumIdle", "5");
        
        // Query optimization
        properties.setProperty("hibernate.query.in_clause_parameter_padding", "true");
        properties.setProperty("hibernate.query.fail_on_pagination_over_collection_fetch", "true");
        
        // Statistics
        properties.setProperty("hibernate.generate_statistics", "true");
        
        em.setJpaProperties(properties);
        return em;
    }
}
```

---

*Continued in Part 3 for remaining questions 26-50...*
