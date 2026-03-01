# JPA Interview Questions & Answers - Part 3 (Questions 26-50)

## Advanced Topics & Real-World Scenarios

### 26. How do you handle inheritance mapping in JPA?

**Answer:**
JPA supports three inheritance strategies: SINGLE_TABLE, JOINED, and TABLE_PER_CLASS.

```java
// Strategy 1: SINGLE_TABLE (default)
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "vehicle_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String manufacturer;
    private String model;
}

@Entity
@DiscriminatorValue("CAR")
public class Car extends Vehicle {
    private int numberOfDoors;
    private String fuelType;
}

@Entity
@DiscriminatorValue("MOTORCYCLE")
public class Motorcycle extends Vehicle {
    private boolean hasSidecar;
    private int engineCC;
}

// Single table: vehicles
// | id | vehicle_type | manufacturer | model | number_of_doors | fuel_type | has_sidecar | engine_cc |
// Pros: Best performance, simple queries
// Cons: Nullable columns, table can become wide

// Strategy 2: JOINED
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private BigDecimal baseSalary;
}

@Entity
@Table(name = "full_time_employees")
public class FullTimeEmployee extends Employee {
    private BigDecimal annualBonus;
    private int vacationDays;
}

@Entity
@Table(name = "contractors")
public class Contractor extends Employee {
    private BigDecimal hourlyRate;
    private LocalDate contractEndDate;
}

// Tables: employees, full_time_employees, contractors
// Pros: Normalized, no nullable columns
// Cons: Requires joins, slower queries

// Strategy 3: TABLE_PER_CLASS
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private Long id;
    
    private BigDecimal amount;
    private LocalDateTime paymentDate;
}

@Entity
@Table(name = "credit_card_payments")
public class CreditCardPayment extends Payment {
    private String cardNumber;
    private String cardType;
}

@Entity
@Table(name = "bank_transfers")
public class BankTransfer extends Payment {
    private String accountNumber;
    private String bankName;
}

// Separate tables for each concrete class
// Pros: No discriminator, clean separation
// Cons: Polymorphic queries use UNION, poor performance

// Querying with inheritance
@Service
public class InheritanceQueryService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Vehicle> findAllVehicles() {
        return entityManager
            .createQuery("SELECT v FROM Vehicle v", Vehicle.class)
            .getResultList();
    }
    
    public List<Car> findAllCars() {
        return entityManager
            .createQuery("SELECT c FROM Car c", Car.class)
            .getResultList();
    }
    
    // TYPE function
    public List<Vehicle> findCarAndMotorcycles() {
        return entityManager
            .createQuery(
                "SELECT v FROM Vehicle v WHERE TYPE(v) IN (Car, Motorcycle)",
                Vehicle.class)
            .getResultList();
    }
    
    // TREAT function (downcasting)
    public List<Vehicle> findCarsWithFourDoors() {
        return entityManager
            .createQuery(
                "SELECT v FROM Vehicle v WHERE TREAT(v AS Car).numberOfDoors = 4",
                Vehicle.class)
            .getResultList();
    }
}

// @MappedSuperclass (not an entity)
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

@Entity
public class Product extends BaseEntity {
    private String name;
    private BigDecimal price;
}

@Entity
public class Category extends BaseEntity {
    private String name;
    private String description;
}
// No polymorphic queries possible with @MappedSuperclass
```

**Strategy Comparison:**

| Strategy | Performance | Normalization | Use Case |
|----------|-------------|---------------|----------|
| SINGLE_TABLE | Best | Poor | Few subclasses, similar attributes |
| JOINED | Good | Best | Many subclasses, different attributes |
| TABLE_PER_CLASS | Poor | Good | Rarely query polymorphically |
| @MappedSuperclass | Best | N/A | Share common attributes only |

### 27. Explain @Embedded and @Embeddable annotations

**Answer:**
`@Embeddable` is used to designate a Java class whose instances are stored as an intrinsic part of an owning entity rather than having their own separate identity or table. The `@Embedded` annotation is used directly on the entity field to signify the inclusion of that embeddable object. This pattern is excellent for modeling reusable, highly cohesive domain concepts (like an `Address` or `ContactInfo` record) while physically keeping the data flattened inside a single database table for efficiency.

```java
// Embeddable class
@Embeddable
public class Address {
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    
    // Constructors, getters, setters
}

// Entity using embedded
@Entity
public class User {
    @Id
    private Long id;
    
    private String username;
    
    @Embedded
    private Address address;
    
    // Multiple embedded with attribute override
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "billing_street")),
        @AttributeOverride(name = "city", column = @Column(name = "billing_city")),
        @AttributeOverride(name = "state", column = @Column(name = "billing_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "billing_zip")),
        @AttributeOverride(name = "country", column = @Column(name = "billing_country"))
    })
    private Address billingAddress;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "shipping_street")),
        @AttributeOverride(name = "city", column = @Column(name = "shipping_city")),
        @AttributeOverride(name = "state", column = @Column(name = "shipping_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "shipping_zip")),
        @AttributeOverride(name = "country", column = @Column(name = "shipping_country"))
    })
    private Address shippingAddress;
}

// Nested embeddables
@Embeddable
public class ContactInfo {
    private String email;
    private String phone;
    
    @Embedded
    private Address address;
}

@Entity
public class Company {
    @Id
    private Long id;
    
    private String name;
    
    @Embedded
    private ContactInfo contactInfo;
}

// Collection of embeddables
@Entity
public class Customer {
    @Id
    private Long id;
    
    @ElementCollection
    @CollectionTable(name = "customer_addresses", 
                    joinColumns = @JoinColumn(name = "customer_id"))
    private List<Address> addresses = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "customer_phones",
                    joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "phone_number")
    private Set<String> phoneNumbers = new HashSet<>();
}

// Embeddable with relationships
@Embeddable
public class AuditInfo {
    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;
    
    private LocalDateTime createdAt;
    
    @ManyToOne
    @JoinColumn(name = "modified_by_user_id")
    private User modifiedBy;
    
    private LocalDateTime modifiedAt;
}

@Entity
public class Document {
    @Id
    private Long id;
    
    private String title;
    
    @Embedded
    private AuditInfo auditInfo;
}

// Querying embedded attributes
@Service
public class EmbeddedQueryService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<User> findByCity(String city) {
        return entityManager
            .createQuery(
                "SELECT u FROM User u WHERE u.address.city = :city",
                User.class)
            .setParameter("city", city)
            .getResultList();
    }
    
    public List<User> findByBillingState(String state) {
        return entityManager
            .createQuery(
                "SELECT u FROM User u WHERE u.billingAddress.state = :state",
                User.class)
            .setParameter("state", state)
            .getResultList();
    }
}
```

### 28. How do you implement multi-tenancy in JPA?

**Answer:**
Implementing Multi-Tenancy (where a single application instance serves multiple distinct client organizations) in JPA typically follows one of three strategies:
1. **Database-per-Tenant:** Achieved via dynamic routing of the `DataSource` using `AbstractRoutingDataSource`.
2. **Schema-per-Tenant:** Utilizes a `MultiTenantConnectionProvider` to transparently switch schemas whenever a connection is acquired.
3. **Discriminator Column (Shared Database):** Appends a `tenant_id` column to all tables. A Hibernate `@Filter` is then defined and automatically applied via an AOP Aspect to enforce strict tenant isolation silently on every query.

```java
// Strategy 1: Separate Database per Tenant
@Configuration
public class MultiTenantDatabaseConfig {
    
    @Bean
    public DataSource dataSource() {
        return new TenantRoutingDataSource();
    }
}

public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }
}

public class TenantContext {
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();
    
    public static void setCurrentTenant(String tenantId) {
        currentTenant.set(tenantId);
    }
    
    public static String getCurrentTenant() {
        return currentTenant.get();
    }
    
    public static void clear() {
        currentTenant.remove();
    }
}

@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        String tenantId = request.getHeader("X-Tenant-ID");
        TenantContext.setCurrentTenant(tenantId);
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) {
        TenantContext.clear();
    }
}

// Strategy 2: Separate Schema per Tenant
@Configuration
public class SchemaPerTenantConfig {
    
    @Bean
    public MultiTenantConnectionProvider multiTenantConnectionProvider() {
        return new SchemaBasedMultiTenantConnectionProvider();
    }
    
    @Bean
    public CurrentTenantIdentifierResolver currentTenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }
}

public class SchemaBasedMultiTenantConnectionProvider 
        implements MultiTenantConnectionProvider {
    
    private final DataSource dataSource;
    
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        connection.createStatement()
            .execute("USE " + tenantIdentifier);
        return connection;
    }
    
    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }
    
    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) 
            throws SQLException {
        connection.close();
    }
}

// Strategy 3: Discriminator Column (Shared Database)
@Entity
@Table(name = "users")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "string"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class User {
    @Id
    private Long id;
    
    @Column(name = "tenant_id")
    private String tenantId;
    
    private String username;
    private String email;
}

@Aspect
@Component
public class TenantFilterAspect {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Before("execution(* com.example.repository.*.*(..))")
    public void enableTenantFilter() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            Filter filter = session.enableFilter("tenantFilter");
            filter.setParameter("tenantId", tenantId);
        }
    }
}

// Base entity for multi-tenancy
@MappedSuperclass
public abstract class TenantAwareEntity {
    
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @PrePersist
    public void prePersist() {
        this.tenantId = TenantContext.getCurrentTenant();
    }
    
    // Getters and setters
}

@Entity
public class Order extends TenantAwareEntity {
    @Id
    private Long id;
    
    private String orderNumber;
    private BigDecimal totalAmount;
}

// Repository with tenant awareness
@Repository
public class TenantAwareRepository<T extends TenantAwareEntity, ID> {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final Class<T> entityClass;
    
    public TenantAwareRepository(Class<T> entityClass) {
        this.entityClass = entityClass;
    }
    
    public Optional<T> findById(ID id) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        
        query.select(root).where(
            cb.and(
                cb.equal(root.get("id"), id),
                cb.equal(root.get("tenantId"), TenantContext.getCurrentTenant())
            )
        );
        
        try {
            return Optional.of(entityManager.createQuery(query).getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
    
    public List<T> findAll() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        
        query.select(root).where(
            cb.equal(root.get("tenantId"), TenantContext.getCurrentTenant())
        );
        
        return entityManager.createQuery(query).getResultList();
    }
}

// Service layer
@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Transactional
    public Order createOrder(Order order) {
        // Tenant ID automatically set in @PrePersist
        return orderRepository.save(order);
    }
    
    public List<Order> findAllOrders() {
        // Automatically filtered by tenant
        return orderRepository.findAll();
    }
}
```

### 29. How do you handle database migrations with JPA?

**Answer:**
JPA's internal automatic schema generation (e.g. `hibernate.hbm2ddl.auto`) is extremely dangerous and strictly avoided in production environments.
Instead, professional database migrations are handled using schema-versioning tools like Flyway or Liquibase. These standalone tools track the sequential evolution of the database structure via ordered, raw SQL scripts or XML changelogs. They ensure that schema changes are predictably and safely deployed in tandem with your codebase across all environments without risking accidental table drops.

```java
// Flyway configuration
@Configuration
public class FlywayConfig {
    
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .load();
        
        flyway.migrate();
        return flyway;
    }
}

// Migration file: V1__initial_schema.sql
/*
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
*/

// V2__add_user_profile.sql
/*
CREATE TABLE user_profiles (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    bio TEXT,
    avatar_url VARCHAR(500),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
*/

// V3__add_orders_table.sql
/*
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_date ON orders(order_date);
*/

// Liquibase configuration
@Configuration
public class LiquibaseConfig {
    
    @Bean
    public SpringLiquibase liquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        liquibase.setContexts("dev,prod");
        liquibase.setShouldRun(true);
        return liquibase;
    }
}

// db.changelog-master.xml
/*
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <include file="db/changelog/v1.0/01-create-users-table.xml"/>
    <include file="db/changelog/v1.0/02-create-orders-table.xml"/>
    <include file="db/changelog/v1.1/01-add-user-profile.xml"/>
</databaseChangeLog>
*/

// 01-create-users-table.xml
/*
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
    http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.0.xsd">

    <changeSet id="1" author="developer">
        <createTable tableName="users">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="username" type="VARCHAR(100)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="email" type="VARCHAR(255)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="password_hash" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="status" type="VARCHAR(20)" defaultValue="ACTIVE"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
            <column name="updated_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP"/>
        </createTable>
        
        <createIndex indexName="idx_users_email" tableName="users">
            <column name="email"/>
        </createIndex>
        
        <createIndex indexName="idx_users_status" tableName="users">
            <column name="status"/>
        </createIndex>
    </changeSet>
    
    <changeSet id="2" author="developer">
        <addColumn tableName="users">
            <column name="phone" type="VARCHAR(20)"/>
        </addColumn>
    </changeSet>
    
    <changeSet id="3" author="developer">
        <modifyDataType tableName="users" columnName="bio" newDataType="TEXT"/>
    </changeSet>
    
    <changeSet id="4" author="developer">
        <renameColumn tableName="users" 
                     oldColumnName="password_hash" 
                     newColumnName="password_encrypted"/>
    </changeSet>
    
    <changeSet id="5" author="developer">
        <dropColumn tableName="users" columnName="old_field"/>
    </changeSet>
</databaseChangeLog>
*/

// JPA schema generation (not recommended for production)
// application.properties
/*
# Development only
spring.jpa.hibernate.ddl-auto=validate

# Options:
# - none: No schema management
# - validate: Validate schema, no changes
# - update: Update schema (dangerous in production)
# - create: Create schema, drop existing
# - create-drop: Create on startup, drop on shutdown
*/

// Custom migration service
@Service
public class DataMigrationService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void migrateUserData() {
        // Complex data migration logic
        List<User> users = entityManager
            .createQuery("SELECT u FROM User u WHERE u.migrated = false", User.class)
            .getResultList();
        
        for (User user : users) {
            // Transform data
            user.setEmail(user.getEmail().toLowerCase());
            user.setMigrated(true);
            entityManager.merge(user);
        }
    }
}
```

### 30. How do you implement custom ID generators in JPA?

**Answer:**
Standard JPA ID generation strategies (`IDENTITY`, `SEQUENCE`) often don't meet complex business requirements, such as needing to generate a sequential identifier with a specific string prefix (e.g. `USR-0001`).
You can implement completely custom ID generators by creating a class that implements Hibernate's `IdentifierGenerator` interface, specifically overriding the `generate()` method to construct the custom string. You then wire it to the entity class using the `@GenericGenerator` annotation coupled side-by-side with `@GeneratedValue`.

```java
// Custom ID generator
public class CustomIdGenerator implements IdentifierGenerator {
    
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        String prefix = "USR";
        
        String query = "SELECT MAX(CAST(SUBSTRING(id, 4) AS UNSIGNED)) FROM users";
        Long maxId = (Long) session.createNativeQuery(query).uniqueResult();
        
        long nextId = (maxId == null) ? 1 : maxId + 1;
        
        return prefix + String.format("%08d", nextId);
    }
}

// Using custom generator
@Entity
public class User {
    @Id
    @GeneratedValue(generator = "custom-id")
    @GenericGenerator(name = "custom-id", strategy = "com.example.CustomIdGenerator")
    private String id; // USR00000001, USR00000002, etc.
    
    private String username;
}

// UUID generator
@Entity
public class Order {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private String id;
    
    private String orderNumber;
}

// Sequence-based custom generator
public class PrefixedSequenceGenerator implements IdentifierGenerator {
    
    private String sequenceName;
    private String prefix;
    
    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry) {
        sequenceName = params.getProperty("sequence_name");
        prefix = params.getProperty("prefix", "");
    }
    
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        Long sequenceValue = (Long) session.createNativeQuery(
            "SELECT NEXTVAL('" + sequenceName + "')")
            .uniqueResult();
        
        return prefix + String.format("%010d", sequenceValue);
    }
}

@Entity
public class Invoice {
    @Id
    @GeneratedValue(generator = "invoice-generator")
    @GenericGenerator(
        name = "invoice-generator",
        strategy = "com.example.PrefixedSequenceGenerator",
        parameters = {
            @Parameter(name = "sequence_name", value = "invoice_seq"),
            @Parameter(name = "prefix", value = "INV")
        }
    )
    private String id; // INV0000000001
    
    private BigDecimal amount;
}

// Composite key generator
public class CompositeKeyGenerator implements IdentifierGenerator {
    
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        if (object instanceof OrderItem) {
            OrderItem item = (OrderItem) object;
            
            OrderItemId id = new OrderItemId();
            id.setOrderId(item.getOrder().getId());
            
            // Get next sequence for this order
            Long maxSequence = (Long) session.createQuery(
                "SELECT MAX(oi.id.sequence) FROM OrderItem oi " +
                "WHERE oi.id.orderId = :orderId")
                .setParameter("orderId", item.getOrder().getId())
                .uniqueResult();
            
            id.setSequence((maxSequence == null) ? 1 : maxSequence + 1);
            
            return id;
        }
        
        throw new IllegalArgumentException("Unsupported entity type");
    }
}

// Timestamp-based ID
public class TimestampIdGenerator implements IdentifierGenerator {
    
    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        
        return timestamp + "" + random;
    }
}

@Entity
public class Event {
    @Id
    @GeneratedValue(generator = "timestamp-id")
    @GenericGenerator(name = "timestamp-id", strategy = "com.example.TimestampIdGenerator")
    private String id; // 1640995200000123
    
    private String eventType;
}

// Snowflake ID generator (distributed systems)
public class SnowflakeIdGenerator implements IdentifierGenerator {
    
    private static final long EPOCH = 1609459200000L; // 2021-01-01
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    
    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }
    
    @Override
    public synchronized Serializable generate(SharedSessionContractImplementor session, 
                                             Object object) {
        long timestamp = System.currentTimeMillis();
        
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }
        
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & ((1 << SEQUENCE_BITS) - 1);
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        
        lastTimestamp = timestamp;
        
        return ((timestamp - EPOCH) << (WORKER_ID_BITS + DATACENTER_ID_BITS + SEQUENCE_BITS))
            | (datacenterId << (WORKER_ID_BITS + SEQUENCE_BITS))
            | (workerId << SEQUENCE_BITS)
            | sequence;
    }
    
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
```

### 31-50. Additional Important Questions

**31. What is the difference between persist() and save() in Hibernate?**
- `persist()` is JPA standard, `save()` is Hibernate-specific
- `persist()` returns void, `save()` returns the identifier
- `persist()` guarantees entity is managed, `save()` may not

**32. How do you handle LazyInitializationException?**
- Use JOIN FETCH in queries
- Enable @Transactional on service methods
- Use Entity Graphs
- Initialize collections within transaction
- Use Hibernate.initialize()

**33. What is the purpose of @Version annotation?**
- Enables optimistic locking
- Automatically incremented on updates
- Prevents lost updates in concurrent scenarios
- Throws OptimisticLockException on conflicts

**34. How do you implement full-text search in JPA?**
- Hibernate Search with Lucene/Elasticsearch
- Native database full-text features
- @Indexed and @Field annotations
- Custom search queries

**35. What are the different flush modes in JPA?**
- AUTO: Flush before query execution (default)
- COMMIT: Flush only at transaction commit
- MANUAL: Explicit flush() calls only

**36. How do you handle bidirectional relationships?**
- Use mappedBy on inverse side
- Implement helper methods for both sides
- Avoid infinite loops in toString/equals/hashCode
- Use @JsonManagedReference/@JsonBackReference

**37. What is the N+1 SELECT problem and solutions?**
- Problem: 1 query + N queries for related entities
- Solutions: JOIN FETCH, Entity Graphs, Batch fetching, @Fetch(SUBSELECT)

**38. How do you implement database views in JPA?**
- @Immutable annotation
- @Subselect for Hibernate
- Read-only entities
- No primary key generation

**39. What are the transaction isolation levels?**
- READ_UNCOMMITTED: Dirty reads possible
- READ_COMMITTED: No dirty reads
- REPEATABLE_READ: No non-repeatable reads
- SERIALIZABLE: Full isolation

**40. How do you handle large result sets efficiently?**
- Pagination with Page/Slice
- Streaming with Stream<T>
- Cursor-based pagination
- Batch processing with flush/clear

**41. What is the purpose of @EntityListeners?**
- Lifecycle callbacks
- Auditing
- Validation
- Custom business logic on entity events

**42. How do you implement custom converters?**
- @Converter annotation
- Implement AttributeConverter<X,Y>
- Convert entity attribute to database column
- Use for enums, JSON, encryption

**43. What are the differences between merge() and refresh()?**
- merge(): Updates entity from detached state
- refresh(): Reloads entity from database
- merge() returns managed entity
- refresh() updates existing managed entity

**44. How do you handle database sequences?**
- @SequenceGenerator annotation
- Configure allocation size
- Database-specific sequences
- Performance optimization with hi/lo algorithm

**45. What is the purpose of @Formula?**
- Computed/derived attributes
- SQL expression evaluated at runtime
- Read-only property
- Not persisted to database

**46. How do you implement soft deletes with queries?**
- @Where clause
- @SQLDelete annotation
- Hibernate Filters
- Custom repository methods

**47. What are the best practices for equals() and hashCode()?**
- Use business key, not ID
- Consistent across entity lifecycle
- Immutable fields preferred
- Consider proxy objects

**48. How do you handle time zones in JPA?**
- Use UTC in database
- Convert in application layer
- @Convert with timezone converter
- Database-specific types

**49. What is the purpose of @Transient?**
- Exclude field from persistence
- Calculated/derived fields
- Temporary state
- Not mapped to database column

**50. How do you implement database constraints in JPA?**
- @Column(unique=true, nullable=false)
- @UniqueConstraint at table level
- @Check for check constraints (Hibernate)
- Database-level constraints preferred

---

## Summary

These 50 JPA interview questions cover:
- **Fundamentals**: Entity lifecycle, relationships, transactions
- **Spring Data JPA**: Repositories, query methods, projections
- **Advanced Topics**: Inheritance, caching, multi-tenancy
- **Performance**: N+1 problem, pagination, optimization
- **Real-World**: Auditing, soft deletes, migrations
- **Best Practices**: Locking, error handling, design patterns

Each question includes detailed code examples and practical scenarios suitable for interviews at all levels from junior to senior/architect roles.
