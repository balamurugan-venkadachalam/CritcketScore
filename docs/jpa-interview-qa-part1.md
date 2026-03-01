# JPA Interview Questions & Answers - Part 1 (Questions 1-25)

## Fundamentals & Core Concepts

### 1. What is JPA and how does it differ from Hibernate?

**Answer:**
JPA (Java Persistence API) is a specification for object-relational mapping (ORM) in Java, while Hibernate is an implementation of the JPA specification.

**Key Differences:**

| Aspect | JPA | Hibernate |
|--------|-----|-----------|
| Type | Specification/Interface | Implementation/Framework |
| Annotations | `javax.persistence.*` | `org.hibernate.annotations.*` |
| Features | Standard features only | Additional features beyond JPA |
| Portability | Vendor-independent | Hibernate-specific |
| Query Language | JPQL | JPQL + HQL |

```java
// JPA standard annotations
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username", nullable = false)
    private String username;
}

// Hibernate-specific annotations
@Entity
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@org.hibernate.annotations.DynamicUpdate
public class Product {
    @Id
    private Long id;
    
    @org.hibernate.annotations.Formula("price * 0.9")
    private Double discountedPrice;
}
```

**When to use Hibernate-specific features:**
- Advanced caching strategies (`@Cache`)
- Partial/conditional updates (`@DynamicUpdate`)
- Custom types
- Performance optimizations (`@Formula`, `@BatchSize`)
- Legacy database support

---

#### Deep Dive: @Cache and @DynamicUpdate

##### 1. @Cache (Second-Level Cache Strategies)
```java
// READ_ONLY — fastest, safest
// Use when: data NEVER changes (country codes, enums, config)
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)

// READ_WRITE — moderate, consistent
// Use when: data changes occasionally (user profiles, products)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
// Uses soft locks — during update, cache entry locked
// Other sessions get fresh DB data while locked

// NONSTRICT_READ_WRITE — faster, slight inconsistency risk
// Use when: stale data briefly acceptable (analytics, counts)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
// No locking — tiny window where stale data possible

// TRANSACTIONAL — strongest, slowest
// Use when: full transactional consistency required
@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
// Requires JTA transaction manager
```

**READ_WRITE internal flow**
```text
Thread1: UPDATE User(1)         L2 Cache            Thread2: findById(1)
    │                               │                    │
    │  acquire soft lock            │                    │
    │──────────────────────────────>│                    │
    │                               │ 🔒 LOCKED          │
    │                               │                    │ findById(1)
    │                               │<───────────────────│
    │                               │ locked! go to DB   │
    │                               │───────────────────>│
    │                               │   DB returns       │
    │                               │   fresh value      │
    │                               │<───────────────────│
    │  commit + update cache        │                    │
    │──────────────────────────────>│                    │
    │                               │ 🔓 UNLOCKED        │
    │                               │ updated value      │
```

##### 2. @DynamicUpdate
By default Hibernate includes ALL columns in every `UPDATE` — even ones that didn't change.

**Without @DynamicUpdate — full update every time**
```java
// Entity has: id, name, email, address, phone, avatar, lastLogin, createdAt

user.setName("John");   // only name changed
session.update(user);

// SQL generated:
UPDATE users SET
    name = 'John',
    email = 'old@mail.com',      // ← unchanged, but included
    address = '123 Street',      // ← unchanged, but included
    phone = '555-1234',          // ← unchanged, but included
    avatar = '...',              // ← unchanged, but included
    last_login = '2024-01-01',   // ← unchanged, but included
    created_at = '2023-01-01'    // ← unchanged, but included
WHERE id = 1;
```

**With @DynamicUpdate — only changed columns**
```java
user.setName("John");   // only name changed
session.update(user);

// SQL generated:
UPDATE users SET
    name = 'John'        // ✅ only the changed column
WHERE id = 1;
```

**When it really matters**
```text
Without @DynamicUpdate:
───────────────────────
Table: 50 columns
Change: 1 column
SQL: UPDATE with all 50 columns
Network: sends all 50 values
DB: rewrites all 50 column values
Indexes: ALL indexed columns re-evaluated 🔴

With @DynamicUpdate:
────────────────────
Table: 50 columns
Change: 1 column
SQL: UPDATE with 1 column
Network: sends 1 value
DB: rewrites 1 column value
Indexes: ONLY affected index re-evaluated ✅
```

**Optimistic locking interaction**
```java
@Entity
@DynamicUpdate
public class BankAccount {

    @Id
    private Long id;

    private BigDecimal balance;
    private String ownerName;

    @Version                    // optimistic lock version column
    private Long version;
}

// Only balance changed:

// WITHOUT @DynamicUpdate:
// UPDATE bank_account SET balance=500, owner_name='Alice', version=2 WHERE id=1 AND version=1;

// WITH @DynamicUpdate:
// UPDATE bank_account SET balance=500, version=2 WHERE id=1 AND version=1;
//                                                            ↑ version always included for lock
```

**Together on an entity**
```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)  // cache across sessions
@DynamicUpdate                                        // only update changed columns
public class Product {

    @Id
    private Long id;
    private String name;
    private BigDecimal price;
    private int stock;
    private String description;   // large text — expensive to send every update
}

// Flow:
// 1. findById(1)  → L2 cache hit         🟢 no DB read
// 2. price change → UPDATE price only    🟢 minimal DB write
// 3. findById(1)  → L2 updated + hit     🟢 no DB read
```

**When to use each**

| Annotation | Use When | Avoid When |
|------------|----------|------------|
| `@Cache(READ_WRITE)` | Frequently read, rarely updated | High-write entities, financial data |
| `@Cache(READ_ONLY)` | Static reference data | Any mutable entity |
| `@DynamicUpdate` | Wide tables, partial updates | Small tables (overhead not worth it) |

> **Cheat Sheet:**
> **`@Cache`** — share entity data across sessions so DB isn't hit every time
> **`@DynamicUpdate`** — only send changed columns in UPDATE, not the entire row

### 2. Explain the JPA Entity Lifecycle

**Answer:**
JPA entities go through four states in their lifecycle:

```
New/Transient → Managed/Persistent → Detached → Removed
```

**States:**

1. **Transient (New):** Object created but not associated with persistence context
2. **Managed (Persistent):** Object associated with persistence context and tracked
3. **Detached:** Object was managed but persistence context is closed
4. **Removed:** Object marked for deletion

```java
@Service
public class EntityLifecycleDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstrateLifecycle() {
        // 1. TRANSIENT - new object, not tracked
        User user = new User();
        user.setUsername("john");
        
        // 2. MANAGED - persist makes it managed
        entityManager.persist(user);
        // Changes are automatically tracked
        user.setEmail("john@example.com"); // Will be saved
        
        // 3. DETACHED - after transaction/clear
        entityManager.detach(user);
        user.setPhone("123456"); // Won't be saved
        
        // Merge to make it managed again
        User managedUser = entityManager.merge(user);
        
        // 4. REMOVED - marked for deletion
        entityManager.remove(managedUser);
        // Will be deleted when transaction commits
    }
    
    @Transactional
    public void stateTransitions() {
        User user = new User("john");
        
        // Transient → Managed
        entityManager.persist(user);
        assertTrue(entityManager.contains(user)); // true
        
        // Managed → Detached
        entityManager.detach(user);
        assertFalse(entityManager.contains(user)); // false
        
        // Detached → Managed
        user = entityManager.merge(user);
        assertTrue(entityManager.contains(user)); // true
        
        // Managed → Removed
        entityManager.remove(user);
        
        // Flush to database
        entityManager.flush();
    }
}
```

### 3. What is the difference between persist() and merge()?

**Answer:**
**`persist()`** is used to add a new entity instance to the persistence context (making it Managed). It will fail if an entity with the same identity already exists.
**`merge()`** is used to update an existing entity or attach a detached entity back to the persistence context. It copies the state of the given object onto the persistent object with the same identifier and returns the newly managed instance.

```java
@Service
public class PersistVsMergeDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstratePersist() {
        // persist() - for NEW entities
        User user = new User();
        user.setUsername("john");
        
        entityManager.persist(user);
        // user is now MANAGED
        // ID is generated (if auto-generated)
        // Returns void
        // Throws exception if entity already exists
        
        user.setEmail("john@example.com");
        // Changes tracked automatically
    }
    
    @Transactional
    public void demonstrateMerge() {
        // merge() - for DETACHED entities
        User detachedUser = new User();
        detachedUser.setId(1L);
        detachedUser.setUsername("john_updated");
        
        User managedUser = entityManager.merge(detachedUser);
        // Returns a MANAGED copy
        // detachedUser remains detached
        // managedUser is the tracked instance
        
        // This won't be saved (detached)
        detachedUser.setEmail("old@example.com");
        
        // This will be saved (managed)
        managedUser.setEmail("new@example.com");
    }
    
    @Transactional
    public void keyDifferences() {
        User user = new User();
        
        // persist() requirements
        // - Entity must be NEW (no ID)
        // - Throws EntityExistsException if already exists
        entityManager.persist(user);
        
        // merge() behavior
        // - Works with both NEW and DETACHED
        // - Creates new managed instance
        // - Original remains detached
        User detached = new User();
        detached.setId(1L);
        User managed = entityManager.merge(detached);
        
        assertNotSame(detached, managed);
    }
}
```

**Decision Matrix:**

| Scenario | Use |
|----------|-----|
| New entity, no ID | `persist()` |
| Detached entity with ID | `merge()` |
| Update from external source | `merge()` |
| Bulk insert | `persist()` |
| Uncertain state | `merge()` (safer) |

### 4. Explain the difference between FetchType.LAZY and FetchType.EAGER

**Answer:**
- **`FetchType.EAGER`** means the related entities are fetched immediately along with the parent entity using a JOIN query. It is the default for `@ManyToOne` and `@OneToOne`.
- **`FetchType.LAZY`** means the related entities are fetched strictly on-demand, only when their getter methods are accessed for the first time. This saves memory and initial load time but can lead to the N+1 query problem. It is the default for `@OneToMany` and `@ManyToMany`.

```java
@Entity
public class User {
    @Id
    private Long id;
    
    // LAZY - loaded on demand (default for collections)
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders;
    
    // EAGER - loaded immediately (default for @ManyToOne, @OneToOne)
    @ManyToOne(fetch = FetchType.EAGER)
    private Department department;
}

@Service
public class FetchTypeDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstrateLazy() {
        User user = entityManager.find(User.class, 1L);
        // SQL: SELECT * FROM users WHERE id = 1
        
        // Orders NOT loaded yet
        System.out.println(user.getUsername()); // No additional query
        
        // Accessing orders triggers lazy loading
        List<Order> orders = user.getOrders();
        // SQL: SELECT * FROM orders WHERE user_id = 1
        
        // LazyInitializationException if accessed outside transaction
    }
    
    @Transactional
    public void demonstrateEager() {
        User user = entityManager.find(User.class, 1L);
        // SQL: SELECT u.*, d.* FROM users u 
        //      LEFT JOIN departments d ON u.department_id = d.id
        //      WHERE u.id = 1
        
        // Department already loaded
        System.out.println(user.getDepartment().getName()); // No additional query
    }
    
    // N+1 Problem with LAZY
    @Transactional
    public void nPlusOneProblem() {
        List<User> users = entityManager
            .createQuery("SELECT u FROM User u", User.class)
            .getResultList();
        // 1 query to fetch users
        
        for (User user : users) {
            System.out.println(user.getOrders().size());
            // N queries (one per user) - BAD!
        }
    }
    
    // Solution: JOIN FETCH
    @Transactional
    public void solveNPlusOne() {
        List<User> users = entityManager
            .createQuery("SELECT u FROM User u LEFT JOIN FETCH u.orders", User.class)
            .getResultList();
        // Single query with JOIN
        
        for (User user : users) {
            System.out.println(user.getOrders().size());
            // No additional queries
        }
    }
}
```

**Best Practices:**

| Scenario | Recommendation |
|----------|----------------|
| Large collections | LAZY |
| Small, frequently used | EAGER |
| Optional relationships | LAZY |
| Required relationships | EAGER or JOIN FETCH |
| Performance critical | LAZY + explicit fetching |

### 5. What is the N+1 query problem and how do you solve it?

**Answer:**
The N+1 query problem occurs when an application executes 1 query to retrieve a list of parent entities, and then N additional queries to fetch their lazily loaded child entities one by one during iteration. This severely degrades database performance by flooding it with individual queries.
It is typically solved by using `JOIN FETCH` in JPQL, defining an `@EntityGraph`, or using Hibernate-specific tools like `@BatchSize` or `@Fetch(FetchMode.SUBSELECT)`.

```java
// Problem demonstration
@Entity
public class Author {
    @Id
    private Long id;
    private String name;
    
    @OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
    private List<Book> books;
}

@Entity
public class Book {
    @Id
    private Long id;
    private String title;
    
    @ManyToOne
    private Author author;
}

@Service
public class NPlusOneProblem {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // BAD: N+1 queries
    @Transactional
    public void demonstrateProblem() {
        List<Author> authors = entityManager
            .createQuery("SELECT a FROM Author a", Author.class)
            .getResultList();
        // Query 1: SELECT * FROM authors
        
        for (Author author : authors) {
            System.out.println(author.getName() + " wrote " + 
                             author.getBooks().size() + " books");
            // Query 2..N: SELECT * FROM books WHERE author_id = ?
        }
        // Total: 1 + N queries (if 100 authors = 101 queries!)
    }
    
    // SOLUTION 1: JOIN FETCH
    @Transactional
    public void solutionJoinFetch() {
        List<Author> authors = entityManager
            .createQuery("SELECT DISTINCT a FROM Author a " +
                        "LEFT JOIN FETCH a.books", Author.class)
            .getResultList();
        // Single query with JOIN
        
        for (Author author : authors) {
            System.out.println(author.getName() + " wrote " + 
                             author.getBooks().size() + " books");
            // No additional queries
        }
    }
    
    // SOLUTION 2: Entity Graph
    @Transactional
    public void solutionEntityGraph() {
        EntityGraph<Author> graph = entityManager.createEntityGraph(Author.class);
        graph.addAttributeNodes("books");
        
        List<Author> authors = entityManager
            .createQuery("SELECT a FROM Author a", Author.class)
            .setHint("javax.persistence.fetchgraph", graph)
            .getResultList();
    }
    
    // SOLUTION 3: Batch Fetching (Hibernate-specific)
    @Entity
    @BatchSize(size = 10)
    public class Book {
        // Fetches books in batches of 10
    }
    
    // SOLUTION 4: @Fetch(FetchMode.SUBSELECT)
    @Entity
    public class Author {
        @OneToMany(mappedBy = "author")
        @Fetch(FetchMode.SUBSELECT)
        private List<Book> books;
        // Uses subquery: SELECT * FROM books 
        //                WHERE author_id IN (SELECT id FROM authors)
    }
}

// Spring Data JPA solution
public interface AuthorRepository extends JpaRepository<Author, Long> {
    
    @Query("SELECT DISTINCT a FROM Author a LEFT JOIN FETCH a.books")
    List<Author> findAllWithBooks();
    
    @EntityGraph(attributePaths = {"books"})
    List<Author> findAll();
}
```

### 6. Explain JPA Caching (First-Level and Second-Level Cache)

**Answer:**
- **First-Level Cache (L1):** Enabled by default and scoped exclusively to the `EntityManager` (Transaction level). It ensures that multiple lookups for the exact same entity within the same session do not hit the database. It cannot be disabled.
- **Second-Level Cache (L2):** Optional and scoped to the `EntityManagerFactory` (Application level). It shares cached entities across multiple sessions/transactions, vastly reducing overall database load. Requires an external caching provider (e.g., Ehcache, Redis).

```java
// First-Level Cache (Session/Persistence Context Cache)
@Service
public class FirstLevelCacheDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstrateFirstLevelCache() {
        // First access - hits database
        User user1 = entityManager.find(User.class, 1L);
        // SQL: SELECT * FROM users WHERE id = 1
        
        // Second access - from cache (no SQL)
        User user2 = entityManager.find(User.class, 1L);
        
        assertSame(user1, user2); // Same instance
        
        // Clear cache
        entityManager.clear();
        
        // Third access - hits database again
        User user3 = entityManager.find(User.class, 1L);
        // SQL: SELECT * FROM users WHERE id = 1
        
        assertNotSame(user1, user3); // Different instance
    }
}

// Second-Level Cache Configuration
@Configuration
@EnableCaching
public class SecondLevelCacheConfig {
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = 
            new LocalContainerEntityManagerFactoryBean();
        
        Properties properties = new Properties();
        // Enable second-level cache
        properties.setProperty("hibernate.cache.use_second_level_cache", "true");
        properties.setProperty("hibernate.cache.use_query_cache", "true");
        properties.setProperty("hibernate.cache.region.factory_class",
            "org.hibernate.cache.jcache.JCacheRegionFactory");
        properties.setProperty("hibernate.javax.cache.provider",
            "org.ehcache.jsr107.EhcacheCachingProvider");
        
        em.setJpaProperties(properties);
        return em;
    }
}

// Entity with second-level cache
@Entity
@Cacheable
@org.hibernate.annotations.Cache(
    usage = CacheConcurrencyStrategy.READ_WRITE,
    region = "userCache"
)
public class User {
    @Id
    private Long id;
    
    private String username;
    
    @OneToMany(mappedBy = "user")
    @org.hibernate.annotations.Cache(
        usage = CacheConcurrencyStrategy.READ_WRITE
    )
    private List<Order> orders;
}

@Service
public class SecondLevelCacheDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstrateSecondLevelCache() {
        // First transaction
        User user1 = entityManager.find(User.class, 1L);
        // SQL: SELECT * FROM users WHERE id = 1
        // Stored in second-level cache
    }
    
    @Transactional
    public void anotherTransaction() {
        // Different transaction/session
        User user2 = entityManager.find(User.class, 1L);
        // Retrieved from second-level cache (no SQL)
    }
    
    // Query cache
    @Transactional
    public void queryCacheExample() {
        List<User> users = entityManager
            .createQuery("SELECT u FROM User u WHERE u.status = :status", User.class)
            .setParameter("status", "ACTIVE")
            .setHint("org.hibernate.cacheable", true)
            .getResultList();
        
        // Second execution - from query cache
        List<User> users2 = entityManager
            .createQuery("SELECT u FROM User u WHERE u.status = :status", User.class)
            .setParameter("status", "ACTIVE")
            .setHint("org.hibernate.cacheable", true)
            .getResultList();
    }
}

// Cache eviction
@Service
public class CacheEvictionService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private CacheManager cacheManager;
    
    public void evictUserCache(Long userId) {
        // Evict from second-level cache
        Cache cache = entityManager.getEntityManagerFactory()
            .getCache();
        cache.evict(User.class, userId);
    }
    
    public void evictAllUserCache() {
        Cache cache = entityManager.getEntityManagerFactory()
            .getCache();
        cache.evict(User.class);
    }
}
```

**Cache Comparison:**

| Feature | First-Level | Second-Level |
|---------|-------------|--------------|
| Scope | Session/Transaction | Application-wide |
| Enabled | Always | Optional |
| Configuration | Automatic | Manual |
| Lifetime | Transaction | Application |
| Thread-safe | No | Yes |

### 7. What are the different types of relationships in JPA?

**Answer:**
JPA supports four standard relational mappings between database tables/entities:
1. **One-to-One (`@OneToOne`):** A single entity maps exactly to another single entity.
2. **One-to-Many (`@OneToMany`):** A single entity acts as a parent to multiple child entities.
3. **Many-to-One (`@ManyToOne`):** Multiple child entities map to a single parent entity.
4. **Many-to-Many (`@ManyToMany`):** Multiple entities map to multiple entities, typically resolved in the database via an intermediate junction/join table.

```java
// 1. ONE-TO-ONE
@Entity
public class User {
    @Id
    private Long id;
    
    // Unidirectional
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "profile_id")
    private UserProfile profile;
}

@Entity
public class UserProfile {
    @Id
    private Long id;
    
    // Bidirectional
    @OneToOne(mappedBy = "profile")
    private User user;
}

// 2. ONE-TO-MANY / MANY-TO-ONE
@Entity
public class Department {
    @Id
    private Long id;
    
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<Employee> employees = new ArrayList<>();
    
    // Helper method
    public void addEmployee(Employee employee) {
        employees.add(employee);
        employee.setDepartment(this);
    }
}

@Entity
public class Employee {
    @Id
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
}

// 3. MANY-TO-MANY
@Entity
public class Student {
    @Id
    private Long id;
    
    @ManyToMany
    @JoinTable(
        name = "student_course",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> courses = new HashSet<>();
}

@Entity
public class Course {
    @Id
    private Long id;
    
    @ManyToMany(mappedBy = "courses")
    private Set<Student> students = new HashSet<>();
}

// 4. MANY-TO-MANY with Extra Columns (Join Entity)
@Entity
public class Student {
    @Id
    private Long id;
    
    @OneToMany(mappedBy = "student")
    private List<Enrollment> enrollments = new ArrayList<>();
}

@Entity
public class Course {
    @Id
    private Long id;
    
    @OneToMany(mappedBy = "course")
    private List<Enrollment> enrollments = new ArrayList<>();
}

@Entity
public class Enrollment {
    @EmbeddedId
    private EnrollmentId id;
    
    @ManyToOne
    @MapsId("studentId")
    private Student student;
    
    @ManyToOne
    @MapsId("courseId")
    private Course course;
    
    private LocalDate enrollmentDate;
    private String grade;
}

@Embeddable
public class EnrollmentId implements Serializable {
    private Long studentId;
    private Long courseId;
}

// Relationship Best Practices
@Service
public class RelationshipBestPractices {
    
    // Always use bidirectional helper methods
    public void addEmployeeToDepartment(Department dept, Employee emp) {
        dept.getEmployees().add(emp);
        emp.setDepartment(dept);
    }
    
    // Use Set for many-to-many to avoid duplicates
    @Entity
    public class Author {
        @ManyToMany
        private Set<Book> books = new HashSet<>(); // Not List
    }
    
    // Avoid EAGER fetching for collections
    @Entity
    public class Order {
        @OneToMany(fetch = FetchType.LAZY) // Good
        private List<OrderItem> items;
    }
}
```

### 8. Explain Cascade Types in JPA

**Answer:**
Cascading allows EntityManager operations to automatically propagate from a parent entity down to its associated child entities, drastically simplifying relationship management.
Common types include `PERSIST` (save parent and children), `MERGE` (update parent and children), `REMOVE` (delete parent and children), and `ALL` (apply all operations). Furthermore, `orphanRemoval = true` is a specific feature used to automatically delete child entities from the database when they are simply removed from the parent's collection.

```java
@Entity
public class CascadeTypeExamples {
    
    // CascadeType.PERSIST
    @OneToMany(cascade = CascadeType.PERSIST)
    private List<Address> addresses;
    // When parent is persisted, children are also persisted
    
    // CascadeType.MERGE
    @OneToMany(cascade = CascadeType.MERGE)
    private List<Phone> phones;
    // When parent is merged, children are also merged
    
    // CascadeType.REMOVE
    @OneToMany(cascade = CascadeType.REMOVE)
    private List<Comment> comments;
    // When parent is removed, children are also removed
    
    // CascadeType.REFRESH
    @OneToMany(cascade = CascadeType.REFRESH)
    private List<Tag> tags;
    // When parent is refreshed, children are also refreshed
    
    // CascadeType.DETACH
    @OneToMany(cascade = CascadeType.DETACH)
    private List<Attachment> attachments;
    // When parent is detached, children are also detached
    
    // CascadeType.ALL (all of the above)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;
}

@Service
public class CascadeDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstratePersist() {
        User user = new User("john");
        Address address = new Address("123 Main St");
        user.addAddress(address);
        
        // With CascadeType.PERSIST
        entityManager.persist(user);
        // Both user and address are persisted
        
        // Without cascade
        entityManager.persist(user);
        entityManager.persist(address); // Must persist explicitly
    }
    
    @Transactional
    public void demonstrateRemove() {
        User user = entityManager.find(User.class, 1L);
        
        // With CascadeType.REMOVE
        entityManager.remove(user);
        // User and all addresses are removed
        
        // With orphanRemoval = true
        user.getAddresses().clear();
        // Addresses are removed even without explicit delete
    }
    
    @Transactional
    public void demonstrateMerge() {
        // Detached user with modified address
        User detachedUser = new User(1L);
        Address detachedAddress = new Address(1L);
        detachedAddress.setStreet("456 Oak Ave");
        detachedUser.addAddress(detachedAddress);
        
        // With CascadeType.MERGE
        User managedUser = entityManager.merge(detachedUser);
        // Both user and address changes are merged
    }
}

// orphanRemoval vs CascadeType.REMOVE
@Entity
public class OrphanRemovalExample {
    
    @OneToMany(
        mappedBy = "parent",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<Child> children;
}

@Service
public class OrphanRemovalDemo {
    
    @Transactional
    public void demonstrateOrphanRemoval() {
        Parent parent = entityManager.find(Parent.class, 1L);
        
        // orphanRemoval = true
        parent.getChildren().remove(0);
        // Child is automatically deleted
        
        // CascadeType.REMOVE
        entityManager.remove(parent);
        // All children are deleted
    }
}
```

**Cascade Type Decision Matrix:**

| Scenario | Cascade Type |
|----------|--------------|
| Parent-child (composition) | ALL + orphanRemoval |
| Independent entities | None or PERSIST |
| Shared entities | MERGE, REFRESH |
| Temporary associations | DETACH |

### 9. What is JPQL and how does it differ from SQL?

**Answer:**

```java
@Service
public class JPQLExamples {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Basic JPQL query
    public List<User> findAllUsers() {
        return entityManager
            .createQuery("SELECT u FROM User u", User.class)
            .getResultList();
        // JPQL: SELECT u FROM User u
        // SQL:  SELECT * FROM users
    }
    
    // WHERE clause
    public List<User> findActiveUsers() {
        return entityManager
            .createQuery("SELECT u FROM User u WHERE u.status = :status", User.class)
            .setParameter("status", "ACTIVE")
            .getResultList();
    }
    
    // JOIN
    public List<User> findUsersWithOrders() {
        return entityManager
            .createQuery(
                "SELECT DISTINCT u FROM User u " +
                "JOIN u.orders o " +
                "WHERE o.status = 'COMPLETED'",
                User.class
            )
            .getResultList();
    }
    
    // JOIN FETCH (solve N+1)
    public List<User> findUsersWithOrdersEager() {
        return entityManager
            .createQuery(
                "SELECT DISTINCT u FROM User u " +
                "LEFT JOIN FETCH u.orders",
                User.class
            )
            .getResultList();
    }
    
    // Aggregation
    public Long countUsers() {
        return entityManager
            .createQuery("SELECT COUNT(u) FROM User u", Long.class)
            .getSingleResult();
    }
    
    // GROUP BY
    public List<Object[]> countOrdersByUser() {
        return entityManager
            .createQuery(
                "SELECT u.username, COUNT(o) " +
                "FROM User u JOIN u.orders o " +
                "GROUP BY u.username " +
                "HAVING COUNT(o) > 5"
            )
            .getResultList();
    }
    
    // Subquery
    public List<User> findUsersWithHighValueOrders() {
        return entityManager
            .createQuery(
                "SELECT u FROM User u " +
                "WHERE u.id IN (" +
                "  SELECT o.user.id FROM Order o " +
                "  WHERE o.totalAmount > 1000" +
                ")",
                User.class
            )
            .getResultList();
    }
    
    // Constructor expression (DTO projection)
    public List<UserDTO> findUserDTOs() {
        return entityManager
            .createQuery(
                "SELECT new com.example.dto.UserDTO(u.id, u.username, u.email) " +
                "FROM User u",
                UserDTO.class
            )
            .getResultList();
    }
    
    // UPDATE query
    @Transactional
    public int updateUserStatus(String oldStatus, String newStatus) {
        return entityManager
            .createQuery(
                "UPDATE User u SET u.status = :newStatus " +
                "WHERE u.status = :oldStatus"
            )
            .setParameter("newStatus", newStatus)
            .setParameter("oldStatus", oldStatus)
            .executeUpdate();
    }
    
    // DELETE query
    @Transactional
    public int deleteInactiveUsers() {
        return entityManager
            .createQuery("DELETE FROM User u WHERE u.status = 'INACTIVE'")
            .executeUpdate();
    }
    
    // Named query
    @Entity
    @NamedQuery(
        name = "User.findByStatus",
        query = "SELECT u FROM User u WHERE u.status = :status"
    )
    public class User {
        // ...
    }
    
    public List<User> findByStatus(String status) {
        return entityManager
            .createNamedQuery("User.findByStatus", User.class)
            .setParameter("status", status)
            .getResultList();
    }
}
```

**JPQL vs SQL:**

| Feature | JPQL | SQL |
|---------|------|-----|
| Target | Entities | Tables |
| Syntax | Object-oriented | Relational |
| Portability | Database-independent | Database-specific |
| Relationships | Navigational | JOIN syntax |
| Case-sensitivity | Entity names: Yes, Keywords: No | Depends on DB |

### 10. Explain the @Transactional annotation and transaction propagation

**Answer:**

```java
@Service
public class TransactionalExamples {
    
    // Basic transaction
    @Transactional
    public void createUser(User user) {
        userRepository.save(user);
        // Commits at method end
        // Rolls back on RuntimeException
    }
    
    // Read-only transaction (optimization)
    @Transactional(readOnly = true)
    public User findUser(Long id) {
        return userRepository.findById(id).orElse(null);
        // No dirty checking
        // Better performance
    }
    
    // Rollback configuration
    @Transactional(rollbackFor = Exception.class)
    public void processOrder(Order order) throws Exception {
        orderRepository.save(order);
        // Rolls back on checked exceptions too
    }
    
    @Transactional(noRollbackFor = ValidationException.class)
    public void validateAndSave(User user) {
        // Won't rollback on ValidationException
    }
    
    // Timeout
    @Transactional(timeout = 30)
    public void longRunningOperation() {
        // Transaction times out after 30 seconds
    }
    
    // Isolation level
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void criticalOperation() {
        // Highest isolation level
    }
}

// Transaction Propagation
@Service
public class PropagationExamples {
    
    @Autowired
    private UserService userService;
    
    // REQUIRED (default) - use existing or create new
    @Transactional(propagation = Propagation.REQUIRED)
    public void requiredExample() {
        userService.saveUser(new User());
        // Uses same transaction
    }
    
    // REQUIRES_NEW - always create new transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewExample() {
        // Creates new transaction
        // Suspends outer transaction
        logService.logAction("User created");
        // Commits independently
    }
    
    // SUPPORTS - use existing if available
    @Transactional(propagation = Propagation.SUPPORTS)
    public void supportsExample() {
        // Runs in transaction if one exists
        // Otherwise runs without transaction
    }
    
    // NOT_SUPPORTED - run without transaction
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedExample() {
        // Suspends current transaction
        // Runs without transaction
    }
    
    // MANDATORY - must have existing transaction
    @Transactional(propagation = Propagation.MANDATORY)
    public void mandatoryExample() {
        // Throws exception if no transaction exists
    }
    
    // NEVER - must not have transaction
    @Transactional(propagation = Propagation.NEVER)
    public void neverExample() {
        // Throws exception if transaction exists
    }
    
    // NESTED - nested transaction (savepoint)
    @Transactional(propagation = Propagation.NESTED)
    public void nestedExample() {
        // Creates nested transaction
        // Can rollback to savepoint
    }
}

// Practical example
@Service
public class OrderService {
    
    @Autowired
    private AuditService auditService;
    
    @Transactional
    public void processOrder(Order order) {
        try {
            orderRepository.save(order);
            paymentService.processPayment(order);
            inventoryService.updateStock(order);
            
            // Log success - independent transaction
            auditService.logSuccess(order.getId());
            
        } catch (Exception e) {
            // Log failure - independent transaction
            auditService.logFailure(order.getId(), e.getMessage());
            throw e;
        }
    }
}

@Service
public class AuditService {
    
    // Always commits, even if parent transaction fails
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(Long orderId) {
        auditRepository.save(new AuditLog(orderId, "SUCCESS"));
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(Long orderId, String error) {
        auditRepository.save(new AuditLog(orderId, "FAILURE", error));
    }
}
```

**Propagation Decision Matrix:**

| Use Case | Propagation |
|----------|-------------|
| Normal business logic | REQUIRED |
| Independent logging | REQUIRES_NEW |
| Optional transaction | SUPPORTS |
| Read-only query | SUPPORTS or NOT_SUPPORTED |
| Must be in transaction | MANDATORY |
| Must not be in transaction | NEVER |
| Partial rollback | NESTED |

---

## Advanced Topics

### 11. What is the difference between @Id and @EmbeddedId?

**Answer:**

```java
// Simple @Id
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;
}

// Composite key with @EmbeddedId
@Embeddable
public class OrderItemId implements Serializable {
    private Long orderId;
    private Long productId;
    
    // equals() and hashCode() required
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItemId)) return false;
        OrderItemId that = (OrderItemId) o;
        return Objects.equals(orderId, that.orderId) &&
               Objects.equals(productId, that.productId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }
}

@Entity
public class OrderItem {
    @EmbeddedId
    private OrderItemId id;
    
    private Integer quantity;
    private BigDecimal price;
    
    // Access composite key fields
    public Long getOrderId() {
        return id.getOrderId();
    }
}

// Alternative: @IdClass
@IdClass(OrderItemId.class)
@Entity
public class OrderItem {
    @Id
    private Long orderId;
    
    @Id
    private Long productId;
    
    private Integer quantity;
}

// Usage
@Service
public class CompositeKeyService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void saveOrderItem() {
        OrderItemId id = new OrderItemId();
        id.setOrderId(1L);
        id.setProductId(100L);
        
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setQuantity(5);
        
        entityManager.persist(item);
    }
    
    public OrderItem findOrderItem(Long orderId, Long productId) {
        OrderItemId id = new OrderItemId();
        id.setOrderId(orderId);
        id.setProductId(productId);
        
        return entityManager.find(OrderItem.class, id);
    }
}
```

### 12. Explain @MapsId and its use cases

**Answer:**

```java
// Parent-child with shared primary key
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;
}

@Entity
public class UserProfile {
    @Id
    private Long id;
    
    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private User user;
    
    private String bio;
    private String avatar;
}

// Many-to-many with extra columns
@Entity
public class Student {
    @Id
    private Long id;
    private String name;
    
    @OneToMany(mappedBy = "student")
    private List<Enrollment> enrollments;
}

@Entity
public class Course {
    @Id
    private Long id;
    private String title;
    
    @OneToMany(mappedBy = "course")
    private List<Enrollment> enrollments;
}

@Entity
public class Enrollment {
    @EmbeddedId
    private EnrollmentId id;
    
    @ManyToOne
    @MapsId("studentId")
    @JoinColumn(name = "student_id")
    private Student student;
    
    @ManyToOne
    @MapsId("courseId")
    @JoinColumn(name = "course_id")
    private Course course;
    
    private LocalDate enrollmentDate;
    private String grade;
}

@Embeddable
public class EnrollmentId implements Serializable {
    private Long studentId;
    private Long courseId;
}

// Usage
@Service
public class MapsIdService {
    
    @Transactional
    public void createUserWithProfile() {
        User user = new User();
        user.setUsername("john");
        
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setBio("Software developer");
        
        user.setProfile(profile);
        
        entityManager.persist(user);
        // Profile gets same ID as user
    }
    
    @Transactional
    public void enrollStudent() {
        Student student = entityManager.find(Student.class, 1L);
        Course course = entityManager.find(Course.class, 100L);
        
        EnrollmentId id = new EnrollmentId();
        id.setStudentId(student.getId());
        id.setCourseId(course.getId());
        
        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setEnrollmentDate(LocalDate.now());
        
        entityManager.persist(enrollment);
    }
}
```

### 13. What are Entity Graphs and when should you use them?

**Answer:**

```java
@Entity
@NamedEntityGraph(
    name = "User.detail",
    attributeNodes = {
        @NamedAttributeNode("profile"),
        @NamedAttributeNode(value = "orders", subgraph = "orders-subgraph")
    },
    subgraphs = {
        @NamedSubgraph(
            name = "orders-subgraph",
            attributeNodes = {
                @NamedAttributeNode("items")
            }
        )
    }
)
public class User {
    @Id
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    private UserProfile profile;
    
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders;
}

@Service
public class EntityGraphService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Using named entity graph
    public User findUserWithDetails(Long id) {
        EntityGraph<?> graph = entityManager
            .getEntityGraph("User.detail");
        
        return entityManager.find(User.class, id,
            Map.of("javax.persistence.fetchgraph", graph));
    }
    
    // Dynamic entity graph
    public User findUserWithOrders(Long id) {
        EntityGraph<User> graph = entityManager
            .createEntityGraph(User.class);
        graph.addAttributeNodes("orders");
        
        return entityManager.find(User.class, id,
            Map.of("javax.persistence.fetchgraph", graph));
    }
    
    // Nested entity graph
    public User findUserWithOrderItems(Long id) {
        EntityGraph<User> graph = entityManager
            .createEntityGraph(User.class);
        Subgraph<Order> orderSubgraph = graph.addSubgraph("orders");
        orderSubgraph.addAttributeNodes("items");
        
        return entityManager.find(User.class, id,
            Map.of("javax.persistence.fetchgraph", graph));
    }
    
    // With JPQL query
    public List<User> findAllUsersWithOrders() {
        EntityGraph<User> graph = entityManager
            .createEntityGraph(User.class);
        graph.addAttributeNodes("orders");
        
        return entityManager
            .createQuery("SELECT u FROM User u", User.class)
            .setHint("javax.persistence.fetchgraph", graph)
            .getResultList();
    }
}

// Spring Data JPA with Entity Graph
public interface UserRepository extends JpaRepository<User, Long> {
    
    @EntityGraph(attributePaths = {"orders", "profile"})
    Optional<User> findById(Long id);
    
    @EntityGraph(value = "User.detail", type = EntityGraph.EntityGraphType.FETCH)
    List<User> findAll();
    
    @EntityGraph(attributePaths = {"orders.items"})
    List<User> findByStatus(String status);
}
```

**Entity Graph vs JOIN FETCH:**

| Feature | Entity Graph | JOIN FETCH |
|---------|--------------|------------|
| Definition | Annotation/API | JPQL |
| Reusability | High | Low |
| Dynamic | Yes | No |
| Type-safe | Yes | No |
| Complexity | Simple | Can be complex |

### 14. How do you handle optimistic and pessimistic locking?

**Answer:**

```java
// Optimistic Locking
@Entity
public class Product {
    @Id
    private Long id;
    
    private String name;
    private BigDecimal price;
    
    @Version
    private Long version;
    // Automatically incremented on update
}

@Service
public class OptimisticLockingService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void updateProduct(Long id, BigDecimal newPrice) {
        Product product = entityManager.find(Product.class, id);
        product.setPrice(newPrice);
        // Version automatically incremented
        // SQL: UPDATE products SET price = ?, version = version + 1
        //      WHERE id = ? AND version = ?
    }
    
    @Transactional
    public void handleOptimisticLockException() {
        try {
            Product product = entityManager.find(Product.class, 1L);
            product.setPrice(new BigDecimal("99.99"));
            entityManager.flush();
        } catch (OptimisticLockException e) {
            // Handle concurrent modification
            // Retry or notify user
            throw new ConcurrentModificationException(
                "Product was modified by another user");
        }
    }
}

// Pessimistic Locking
@Service
public class PessimisticLockingService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // PESSIMISTIC_READ - shared lock
    @Transactional
    public Product readWithLock(Long id) {
        return entityManager.find(Product.class, id,
            LockModeType.PESSIMISTIC_READ);
        // SQL: SELECT ... FOR SHARE
        // Others can read but not write
    }
    
    // PESSIMISTIC_WRITE - exclusive lock
    @Transactional
    public void updateWithLock(Long id, BigDecimal newPrice) {
        Product product = entityManager.find(Product.class, id,
            LockModeType.PESSIMISTIC_WRITE);
        // SQL: SELECT ... FOR UPDATE
        // Others cannot read or write
        
        product.setPrice(newPrice);
    }
    
    // PESSIMISTIC_FORCE_INCREMENT
    @Transactional
    public void forceIncrementVersion(Long id) {
        Product product = entityManager.find(Product.class, id,
            LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        // Acquires lock AND increments version
    }
    
    // Lock with timeout
    @Transactional
    public void lockWithTimeout(Long id) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("javax.persistence.lock.timeout", 5000); // 5 seconds
        
        Product product = entityManager.find(Product.class, id,
            LockModeType.PESSIMISTIC_WRITE, properties);
    }
    
    // Lock in query
    @Transactional
    public List<Product> findAndLock(String category) {
        return entityManager
            .createQuery("SELECT p FROM Product p WHERE p.category = :category",
                        Product.class)
            .setParameter("category", category)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    }
}

// Spring Data JPA locking
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);
    
    @Lock(LockModeType.OPTIMISTIC)
    List<Product> findByCategory(String category);
}

// Retry mechanism for optimistic locking
@Service
public class RetryService {
    
    @Retryable(
        value = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional
    public void updateWithRetry(Long id, BigDecimal newPrice) {
        Product product = entityManager.find(Product.class, id);
        product.setPrice(newPrice);
    }
}
```

**Locking Comparison:**

| Aspect | Optimistic | Pessimistic |
|--------|-----------|-------------|
| Assumption | Conflicts rare | Conflicts common |
| Performance | Better | Worse |
| Scalability | Better | Worse |
| Deadlocks | No | Possible |
| Use Case | Low contention | High contention |

### 15. What is the difference between save() and saveAndFlush()?

**Answer:**

```java
@Service
public class SaveVsSaveAndFlushDemo {
    
    @Autowired
    private UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstrateSave() {
        User user = new User("john");
        
        // save() - queues the operation
        userRepository.save(user);
        // SQL not executed yet
        // Changes in persistence context
        
        // Can continue working with entity
        user.setEmail("john@example.com");
        
        // SQL executed at transaction commit or explicit flush
    }
    
    @Transactional
    public void demonstrateSaveAndFlush() {
        User user = new User("jane");
        
        // saveAndFlush() - immediate execution
        userRepository.saveAndFlush(user);
        // SQL: INSERT INTO users ...
        // Executed immediately
        
        // ID is available immediately (if auto-generated)
        Long id = user.getId();
        assertNotNull(id);
    }
    
    @Transactional
    public void whenToUseSaveAndFlush() {
        // Scenario 1: Need ID immediately
        User user = new User("bob");
        userRepository.saveAndFlush(user);
        Long userId = user.getId(); // Available now
        
        // Scenario 2: Trigger database constraints
        try {
            User duplicate = new User("bob"); // Same username
            userRepository.saveAndFlush(duplicate);
        } catch (DataIntegrityViolationException e) {
            // Caught immediately
        }
        
        // Scenario 3: Clear persistence context
        for (int i = 0; i < 1000; i++) {
            User u = new User("user" + i);
            userRepository.save(u);
            
            if (i % 100 == 0) {
                userRepository.flush();
                entityManager.clear(); // Prevent memory issues
            }
        }
    }
    
    @Transactional
    public void demonstrateFlushMode() {
        // Default: FlushModeType.AUTO
        // Flushes before query if needed
        
        User user = new User("alice");
        entityManager.persist(user);
        
        // This triggers flush automatically
        List<User> users = entityManager
            .createQuery("SELECT u FROM User u WHERE u.username = 'alice'",
                        User.class)
            .getResultList();
        
        // Manual flush mode
        entityManager.setFlushMode(FlushModeType.COMMIT);
        // Only flushes at commit
    }
}

// Performance implications
@Service
public class PerformanceComparison {
    
    @Transactional
    public void batchInsertWithSave() {
        // Better performance
        for (int i = 0; i < 1000; i++) {
            User user = new User("user" + i);
            userRepository.save(user);
        }
        // Single batch INSERT at commit
    }
    
    @Transactional
    public void batchInsertWithSaveAndFlush() {
        // Worse performance
        for (int i = 0; i < 1000; i++) {
            User user = new User("user" + i);
            userRepository.saveAndFlush(user);
        }
        // 1000 individual INSERTs
    }
}
```

**Decision Matrix:**

| Use Case | Method |
|----------|--------|
| Normal save | `save()` |
| Need ID immediately | `saveAndFlush()` |
| Batch operations | `save()` |
| Trigger constraints | `saveAndFlush()` |
| Memory management | `save()` + periodic `flush()` |

---

*Continued in Part 2...*
