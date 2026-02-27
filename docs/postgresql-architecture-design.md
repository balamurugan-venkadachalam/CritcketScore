# PostgreSQL Database Architecture & System Design

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Architecture Diagrams](#architecture-diagrams)
3. [Partitioning Strategies](#partitioning-strategies)
4. [Sharding Strategies](#sharding-strategies)
5. [Change Data Capture (CDC)](#change-data-capture-cdc)
6. [Table Design Examples](#table-design-examples)
7. [Pros and Cons](#pros-and-cons)
8. [50 System Design Interview Questions](#50-system-design-interview-questions)

---

## Architecture Overview

### High-Level PostgreSQL Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application Layer                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ Service 1│  │ Service 2│  │ Service 3│  │ Service N│       │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘       │
└───────┼─────────────┼─────────────┼─────────────┼──────────────┘
        │             │             │             │
        └─────────────┴─────────────┴─────────────┘
                      │
        ┌─────────────▼──────────────┐
        │   Connection Pooler        │
        │   (PgBouncer/Pgpool-II)   │
        └─────────────┬──────────────┘
                      │
        ┌─────────────▼──────────────┐
        │   Load Balancer (HAProxy)  │
        └─────────────┬──────────────┘
                      │
        ┌─────────────┴──────────────┐
        │                            │
┌───────▼────────┐          ┌────────▼───────┐
│ Primary (RW)   │◄────────►│ Standby (RO)   │
│  PostgreSQL    │  Streaming│  PostgreSQL    │
│                │  Replication                │
│  - WAL Writer  │          │  - WAL Receiver │
│  - Checkpointer│          │  - Recovery     │
└───────┬────────┘          └────────┬───────┘
        │                            │
        │                            │
┌───────▼────────┐          ┌────────▼───────┐
│  WAL Archive   │          │  WAL Archive   │
│  (S3/NFS)      │          │  (S3/NFS)      │
└────────────────┘          └────────────────┘
```

### Partitioned Table Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Parent Table (orders)                      │
│                   (Partitioned by range)                      │
└──────────────────────────┬───────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼────────┐ ┌───────▼────────┐ ┌──────▼─────────┐
│ orders_2024_q1 │ │ orders_2024_q2 │ │ orders_2024_q3 │
│ (Jan-Mar 2024) │ │ (Apr-Jun 2024) │ │ (Jul-Sep 2024) │
│                │ │                │ │                │
│ Index: idx_q1  │ │ Index: idx_q2  │ │ Index: idx_q3  │
└────────────────┘ └────────────────┘ └────────────────┘

Partition Pruning Example:
SELECT * FROM orders WHERE order_date >= '2024-04-01' 
  AND order_date < '2024-07-01';
  
→ Only scans orders_2024_q2 partition
```

### Sharded Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│              (Shard-Aware Routing Logic)                     │
└──────────────────────────┬──────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼────────┐ ┌───────▼────────┐ ┌──────▼─────────┐
│   Shard 1      │ │   Shard 2      │ │   Shard 3      │
│ (user_id % 3=0)│ │(user_id % 3=1) │ │(user_id % 3=2) │
│                │ │                │ │                │
│ Primary + RO   │ │ Primary + RO   │ │ Primary + RO   │
│ Replicas       │ │ Replicas       │ │ Replicas       │
└────────────────┘ └────────────────┘ └────────────────┘

Shard Key: user_id
Hash Function: user_id % 3
Range: 0-2 (3 shards)
```

### CDC (Change Data Capture) Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    PostgreSQL Primary                         │
│                                                               │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │   Tables    │───►│  WAL Writer  │───►│  WAL Files   │   │
│  └─────────────┘    └──────────────┘    └──────┬───────┘   │
└─────────────────────────────────────────────────┼───────────┘
                                                  │
                           ┌──────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
┌───────▼────────┐ ┌───────▼────────┐ ┌──────▼─────────┐
│   Debezium     │ │   Logical      │ │   pg_logical   │
│   Connector    │ │   Replication  │ │   Replication  │
│                │ │   Slot          │ │   Slot         │
└───────┬────────┘ └───────┬────────┘ └──────┬─────────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
        ┌──────────────────▼──────────────────┐
        │         Kafka / Event Stream         │
        │  ┌────────┐  ┌────────┐  ┌────────┐│
        │  │Topic 1 │  │Topic 2 │  │Topic 3 ││
        │  └────────┘  └────────┘  └────────┘│
        └──────────────────┬──────────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        │                                     │
┌───────▼────────┐ ┌───────▼────────┐ ┌──────▼─────────┐
│  Data Lake     │ │  Search Index  │ │  Analytics DB  │
│  (S3/Hadoop)   │ │  (Elasticsearch)│ │  (Redshift)    │
└────────────────┘ └────────────────┘ └────────────────┘
```

---

## Partitioning Strategies

### 1. Range Partitioning

**Use Case:** Time-series data, ordered data

```sql
-- Parent table
CREATE TABLE orders (
    order_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    order_date TIMESTAMP NOT NULL,
    total_amount DECIMAL(10,2),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (order_date);

-- Partitions by quarter
CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');

CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');

CREATE TABLE orders_2024_q3 PARTITION OF orders
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');

CREATE TABLE orders_2024_q4 PARTITION OF orders
    FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');

-- Indexes on each partition
CREATE INDEX idx_orders_2024_q1_user ON orders_2024_q1(user_id);
CREATE INDEX idx_orders_2024_q2_user ON orders_2024_q2(user_id);
CREATE INDEX idx_orders_2024_q3_user ON orders_2024_q3(user_id);
CREATE INDEX idx_orders_2024_q4_user ON orders_2024_q4(user_id);

-- Automatic partition creation function
CREATE OR REPLACE FUNCTION create_partition_if_not_exists()
RETURNS TRIGGER AS $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    partition_date := DATE_TRUNC('quarter', NEW.order_date);
    partition_name := 'orders_' || TO_CHAR(partition_date, 'YYYY_Q"q"');
    start_date := partition_date;
    end_date := partition_date + INTERVAL '3 months';
    
    IF NOT EXISTS (
        SELECT 1 FROM pg_tables WHERE tablename = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF orders FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        EXECUTE format(
            'CREATE INDEX idx_%I_user ON %I(user_id)',
            partition_name, partition_name
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER create_partition_trigger
    BEFORE INSERT ON orders
    FOR EACH ROW
    EXECUTE FUNCTION create_partition_if_not_exists();
```

**Pros:**
- Efficient for time-based queries
- Easy to archive old data (detach/drop partitions)
- Query pruning improves performance
- Maintenance operations can target specific partitions

**Cons:**
- Requires planning partition boundaries
- Can lead to uneven data distribution
- Complex queries across partitions may be slower

### 2. List Partitioning

**Use Case:** Categorical data, geographic regions

```sql
-- Parent table
CREATE TABLE users (
    user_id BIGSERIAL,
    username VARCHAR(100),
    email VARCHAR(255),
    region VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY LIST (region);

-- Partitions by region
CREATE TABLE users_us PARTITION OF users
    FOR VALUES IN ('US', 'USA', 'United States');

CREATE TABLE users_eu PARTITION OF users
    FOR VALUES IN ('UK', 'DE', 'FR', 'IT', 'ES');

CREATE TABLE users_asia PARTITION OF users
    FOR VALUES IN ('JP', 'CN', 'IN', 'SG', 'KR');

CREATE TABLE users_other PARTITION OF users
    DEFAULT;

-- Indexes
CREATE INDEX idx_users_us_email ON users_us(email);
CREATE INDEX idx_users_eu_email ON users_eu(email);
CREATE INDEX idx_users_asia_email ON users_asia(email);
```

**Pros:**
- Natural data segregation by category
- Compliance with data residency requirements
- Targeted maintenance per region

**Cons:**
- Requires predefined categories
- Uneven distribution if categories are imbalanced

### 3. Hash Partitioning

**Use Case:** Even distribution, no natural partitioning key

```sql
-- Parent table
CREATE TABLE events (
    event_id BIGSERIAL,
    user_id BIGINT,
    event_type VARCHAR(50),
    event_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY HASH (user_id);

-- Create 8 hash partitions
CREATE TABLE events_h0 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 0);

CREATE TABLE events_h1 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 1);

CREATE TABLE events_h2 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 2);

CREATE TABLE events_h3 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 3);

CREATE TABLE events_h4 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 4);

CREATE TABLE events_h5 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 5);

CREATE TABLE events_h6 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 6);

CREATE TABLE events_h7 PARTITION OF events
    FOR VALUES WITH (MODULUS 8, REMAINDER 7);
```

**Pros:**
- Even data distribution
- No hot partitions
- Good for parallel processing

**Cons:**
- Cannot easily drop old data
- No query pruning benefits
- Difficult to rebalance

### 4. Multi-Level Partitioning

```sql
-- Parent table partitioned by range (year)
CREATE TABLE transactions (
    transaction_id BIGSERIAL,
    user_id BIGINT,
    amount DECIMAL(10,2),
    transaction_date DATE,
    region VARCHAR(50)
) PARTITION BY RANGE (transaction_date);

-- Year partitions
CREATE TABLE transactions_2024 PARTITION OF transactions
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01')
    PARTITION BY LIST (region);

-- Sub-partitions by region
CREATE TABLE transactions_2024_us PARTITION OF transactions_2024
    FOR VALUES IN ('US');

CREATE TABLE transactions_2024_eu PARTITION OF transactions_2024
    FOR VALUES IN ('EU');

CREATE TABLE transactions_2024_asia PARTITION OF transactions_2024
    FOR VALUES IN ('ASIA');
```

---

## Sharding Strategies

### 1. Application-Level Sharding

```java
// Shard routing configuration
@Configuration
public class ShardingConfig {
    
    @Bean
    public Map<String, DataSource> shardDataSources() {
        Map<String, DataSource> shards = new HashMap<>();
        
        shards.put("shard0", createDataSource("jdbc:postgresql://shard0:5432/db"));
        shards.put("shard1", createDataSource("jdbc:postgresql://shard1:5432/db"));
        shards.put("shard2", createDataSource("jdbc:postgresql://shard2:5432/db"));
        
        return shards;
    }
    
    private DataSource createDataSource(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername("dbuser");
        config.setPassword("dbpass");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
}

// Shard resolver
@Component
public class ShardResolver {
    
    private static final int SHARD_COUNT = 3;
    private final Map<String, DataSource> shardDataSources;
    
    public DataSource resolveShardByUserId(Long userId) {
        int shardIndex = (int) (userId % SHARD_COUNT);
        return shardDataSources.get("shard" + shardIndex);
    }
    
    public DataSource resolveShardByKey(String key) {
        int hash = Math.abs(key.hashCode());
        int shardIndex = hash % SHARD_COUNT;
        return shardDataSources.get("shard" + shardIndex);
    }
    
    public List<DataSource> getAllShards() {
        return new ArrayList<>(shardDataSources.values());
    }
}

// Repository with sharding
@Repository
public class UserRepository {
    
    private final ShardResolver shardResolver;
    private final JdbcTemplate jdbcTemplatePrototype;
    
    public User findById(Long userId) {
        DataSource shard = shardResolver.resolveShardByUserId(userId);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(shard);
        
        return jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE user_id = ?",
            new Object[]{userId},
            new UserRowMapper()
        );
    }
    
    public List<User> findAll() {
        List<User> allUsers = new ArrayList<>();
        
        // Query all shards in parallel
        List<CompletableFuture<List<User>>> futures = 
            shardResolver.getAllShards().stream()
                .map(shard -> CompletableFuture.supplyAsync(() -> {
                    JdbcTemplate jdbcTemplate = new JdbcTemplate(shard);
                    return jdbcTemplate.query(
                        "SELECT * FROM users",
                        new UserRowMapper()
                    );
                }))
                .collect(Collectors.toList());
        
        // Collect results
        futures.forEach(future -> {
            try {
                allUsers.addAll(future.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        return allUsers;
    }
}
```

### 2. Citus (PostgreSQL Extension for Sharding)

```sql
-- Install Citus extension
CREATE EXTENSION citus;

-- Create distributed table
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100),
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Distribute table across shards
SELECT create_distributed_table('users', 'user_id');

-- Create reference table (replicated to all shards)
CREATE TABLE countries (
    country_code VARCHAR(2) PRIMARY KEY,
    country_name VARCHAR(100)
);

SELECT create_reference_table('countries');

-- Distributed queries work transparently
INSERT INTO users (username, email) VALUES ('john', 'john@example.com');
SELECT * FROM users WHERE user_id = 123;

-- Co-location for joins
CREATE TABLE orders (
    order_id BIGSERIAL,
    user_id BIGINT,
    total_amount DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

SELECT create_distributed_table('orders', 'user_id');

-- This join is efficient (co-located)
SELECT u.username, o.total_amount
FROM users u
JOIN orders o ON u.user_id = o.user_id
WHERE u.user_id = 123;
```

### 3. Consistent Hashing for Dynamic Sharding

```java
@Component
public class ConsistentHashShardResolver {
    
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodesPerShard = 150;
    
    @PostConstruct
    public void init() {
        List<String> shards = Arrays.asList("shard0", "shard1", "shard2");
        
        for (String shard : shards) {
            for (int i = 0; i < virtualNodesPerShard; i++) {
                String virtualNode = shard + "#" + i;
                long hash = hash(virtualNode);
                ring.put(hash, shard);
            }
        }
    }
    
    public String resolveShard(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        
        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        
        if (entry == null) {
            entry = ring.firstEntry();
        }
        
        return entry.getValue();
    }
    
    public void addShard(String shard) {
        for (int i = 0; i < virtualNodesPerShard; i++) {
            String virtualNode = shard + "#" + i;
            long hash = hash(virtualNode);
            ring.put(hash, shard);
        }
    }
    
    public void removeShard(String shard) {
        for (int i = 0; i < virtualNodesPerShard; i++) {
            String virtualNode = shard + "#" + i;
            long hash = hash(virtualNode);
            ring.remove(hash);
        }
    }
    
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

**Sharding Pros:**
- Horizontal scalability
- Improved write performance
- Reduced contention
- Geographic distribution

**Sharding Cons:**
- Complex application logic
- Cross-shard queries are expensive
- Difficult to rebalance
- Transaction limitations across shards
- Increased operational complexity

---

## Change Data Capture (CDC)

### 1. Logical Replication Setup

```sql
-- Enable logical replication in postgresql.conf
-- wal_level = logical
-- max_replication_slots = 10
-- max_wal_senders = 10

-- Create publication on source database
CREATE PUBLICATION my_publication FOR ALL TABLES;

-- Or specific tables
CREATE PUBLICATION orders_publication FOR TABLE orders, order_items;

-- Create replication slot
SELECT * FROM pg_create_logical_replication_slot('my_slot', 'pgoutput');

-- On target database, create subscription
CREATE SUBSCRIPTION my_subscription
    CONNECTION 'host=source-db port=5432 dbname=mydb user=replicator password=pass'
    PUBLICATION my_publication;

-- Monitor replication lag
SELECT 
    slot_name,
    confirmed_flush_lsn,
    pg_current_wal_lsn() - confirmed_flush_lsn AS lag_bytes
FROM pg_replication_slots;
```

### 2. Debezium CDC Configuration

```yaml
# Debezium PostgreSQL connector configuration
name: postgres-connector
config:
  connector.class: io.debezium.connector.postgresql.PostgresConnector
  database.hostname: postgres-host
  database.port: 5432
  database.user: debezium
  database.password: dbz_password
  database.dbname: mydb
  database.server.name: myserver
  
  # Replication slot
  slot.name: debezium_slot
  plugin.name: pgoutput
  
  # Tables to capture
  table.include.list: public.users,public.orders,public.products
  
  # Kafka topics
  topic.prefix: mydb
  
  # Snapshot mode
  snapshot.mode: initial
  
  # Transform timestamps
  transforms: unwrap
  transforms.unwrap.type: io.debezium.transforms.ExtractNewRecordState
  transforms.unwrap.drop.tombstones: false
```

```java
// Kafka consumer for CDC events
@Service
public class CDCEventConsumer {
    
    @KafkaListener(topics = "mydb.public.orders", groupId = "cdc-consumer")
    public void consumeOrderChanges(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            
            String operation = event.get("op").asText(); // c=create, u=update, d=delete
            JsonNode after = event.get("after");
            JsonNode before = event.get("before");
            
            switch (operation) {
                case "c": // INSERT
                    handleOrderCreated(after);
                    break;
                case "u": // UPDATE
                    handleOrderUpdated(before, after);
                    break;
                case "d": // DELETE
                    handleOrderDeleted(before);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing CDC event", e);
        }
    }
    
    private void handleOrderCreated(JsonNode order) {
        // Update search index
        searchService.indexOrder(order);
        
        // Update cache
        cacheService.cacheOrder(order);
        
        // Send notification
        notificationService.sendOrderCreatedNotification(order);
    }
    
    private void handleOrderUpdated(JsonNode before, JsonNode after) {
        // Update materialized view
        materializedViewService.updateOrderView(after);
        
        // Invalidate cache
        Long orderId = after.get("order_id").asLong();
        cacheService.evictOrder(orderId);
    }
    
    private void handleOrderDeleted(JsonNode order) {
        Long orderId = order.get("order_id").asLong();
        
        // Remove from search index
        searchService.deleteOrder(orderId);
        
        // Remove from cache
        cacheService.evictOrder(orderId);
    }
}
```

### 3. Custom Trigger-Based CDC

```sql
-- Create audit table
CREATE TABLE order_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT,
    operation VARCHAR(10),
    old_data JSONB,
    new_data JSONB,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create trigger function
CREATE OR REPLACE FUNCTION order_audit_trigger()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO order_audit (order_id, operation, new_data, changed_by)
        VALUES (NEW.order_id, 'INSERT', row_to_json(NEW)::jsonb, current_user);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO order_audit (order_id, operation, old_data, new_data, changed_by)
        VALUES (NEW.order_id, 'UPDATE', row_to_json(OLD)::jsonb, 
                row_to_json(NEW)::jsonb, current_user);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO order_audit (order_id, operation, old_data, changed_by)
        VALUES (OLD.order_id, 'DELETE', row_to_json(OLD)::jsonb, current_user);
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger
CREATE TRIGGER order_changes
    AFTER INSERT OR UPDATE OR DELETE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION order_audit_trigger();

-- Query audit history
SELECT 
    audit_id,
    order_id,
    operation,
    new_data->>'status' as new_status,
    old_data->>'status' as old_status,
    changed_by,
    changed_at
FROM order_audit
WHERE order_id = 12345
ORDER BY changed_at DESC;
```

**CDC Pros:**
- Real-time data synchronization
- Event-driven architecture
- Decoupled systems
- Audit trail
- Multiple consumers

**CDC Cons:**
- Additional infrastructure complexity
- Potential data lag
- Storage overhead for WAL
- Monitoring requirements
- Schema evolution challenges

---

## Table Design Examples

### 1. E-Commerce Schema with Partitioning

```sql
-- Users table (sharded by user_id)
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status) WHERE status = 'active';

-- Products table (reference table - replicated)
CREATE TABLE products (
    product_id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id BIGINT,
    price DECIMAL(10,2) NOT NULL,
    stock_quantity INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_active ON products(is_active) WHERE is_active = true;

-- Orders table (partitioned by order_date, sharded by user_id)
CREATE TABLE orders (
    order_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    tax DECIMAL(10,2) DEFAULT 0,
    shipping_cost DECIMAL(10,2) DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL,
    shipping_address_id BIGINT,
    billing_address_id BIGINT,
    payment_method VARCHAR(50),
    payment_status VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id, order_date)
) PARTITION BY RANGE (order_date);

-- Create partitions for 2024
CREATE TABLE orders_2024_q1 PARTITION OF orders
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');

CREATE TABLE orders_2024_q2 PARTITION OF orders
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');

CREATE TABLE orders_2024_q3 PARTITION OF orders
    FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');

CREATE TABLE orders_2024_q4 PARTITION OF orders
    FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');

-- Indexes on partitions
CREATE INDEX idx_orders_2024_q1_user ON orders_2024_q1(user_id);
CREATE INDEX idx_orders_2024_q1_status ON orders_2024_q1(status);
CREATE INDEX idx_orders_2024_q1_number ON orders_2024_q1(order_number);

-- Order items table (partitioned by order_date)
CREATE TABLE order_items (
    order_item_id BIGSERIAL,
    order_id BIGINT NOT NULL,
    order_date TIMESTAMP NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    discount DECIMAL(10,2) DEFAULT 0,
    subtotal DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_item_id, order_date)
) PARTITION BY RANGE (order_date);

CREATE TABLE order_items_2024_q1 PARTITION OF order_items
    FOR VALUES FROM ('2024-01-01') TO ('2024-04-01');

CREATE TABLE order_items_2024_q2 PARTITION OF order_items
    FOR VALUES FROM ('2024-04-01') TO ('2024-07-01');

-- Inventory table (hash partitioned for even distribution)
CREATE TABLE inventory_transactions (
    transaction_id BIGSERIAL,
    product_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    reference_id BIGINT,
    reference_type VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transaction_id, product_id)
) PARTITION BY HASH (product_id);

CREATE TABLE inventory_transactions_h0 PARTITION OF inventory_transactions
    FOR VALUES WITH (MODULUS 4, REMAINDER 0);

CREATE TABLE inventory_transactions_h1 PARTITION OF inventory_transactions
    FOR VALUES WITH (MODULUS 4, REMAINDER 1);

CREATE TABLE inventory_transactions_h2 PARTITION OF inventory_transactions
    FOR VALUES WITH (MODULUS 4, REMAINDER 2);

CREATE TABLE inventory_transactions_h3 PARTITION OF inventory_transactions
    FOR VALUES WITH (MODULUS 4, REMAINDER 3);
```

### 2. Analytics/Events Schema

```sql
-- Events table (time-series, range partitioned)
CREATE TABLE user_events (
    event_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    event_data JSONB,
    session_id VARCHAR(100),
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, created_at)
) PARTITION BY RANGE (created_at);

-- Monthly partitions for events
CREATE TABLE user_events_2024_01 PARTITION OF user_events
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE user_events_2024_02 PARTITION OF user_events
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Indexes for common queries
CREATE INDEX idx_events_2024_01_user ON user_events_2024_01(user_id, created_at DESC);
CREATE INDEX idx_events_2024_01_type ON user_events_2024_01(event_type, created_at DESC);
CREATE INDEX idx_events_2024_01_data ON user_events_2024_01 USING gin(event_data);

-- Materialized view for aggregations
CREATE MATERIALIZED VIEW daily_user_activity AS
SELECT 
    DATE(created_at) as activity_date,
    user_id,
    event_type,
    COUNT(*) as event_count,
    COUNT(DISTINCT session_id) as session_count
FROM user_events
WHERE created_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE(created_at), user_id, event_type;

CREATE UNIQUE INDEX idx_daily_activity ON daily_user_activity(activity_date, user_id, event_type);

-- Refresh materialized view periodically
CREATE OR REPLACE FUNCTION refresh_daily_activity()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_user_activity;
END;
$$ LANGUAGE plpgsql;
```

### 3. Multi-Tenant Schema

```sql
-- Tenants table
CREATE TABLE tenants (
    tenant_id BIGSERIAL PRIMARY KEY,
    tenant_name VARCHAR(255) UNIQUE NOT NULL,
    subdomain VARCHAR(100) UNIQUE NOT NULL,
    plan VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'active',
    settings JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tenant users (partitioned by tenant_id)
CREATE TABLE tenant_users (
    user_id BIGSERIAL,
    tenant_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tenant_id),
    UNIQUE (tenant_id, email)
) PARTITION BY LIST (tenant_id);

-- Create partition per tenant (for large tenants)
CREATE TABLE tenant_users_1 PARTITION OF tenant_users
    FOR VALUES IN (1);

CREATE TABLE tenant_users_2 PARTITION OF tenant_users
    FOR VALUES IN (2);

-- Default partition for smaller tenants
CREATE TABLE tenant_users_default PARTITION OF tenant_users
    DEFAULT;

-- Row-level security for multi-tenancy
ALTER TABLE tenant_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenant_users
    USING (tenant_id = current_setting('app.current_tenant_id')::bigint);

-- Set tenant context in application
-- SET app.current_tenant_id = 123;
```

---

## Pros and Cons

### PostgreSQL Advantages

**Pros:**
1. **ACID Compliance:** Full transactional support with strong consistency
2. **Rich Data Types:** JSON, arrays, hstore, geometric types, custom types
3. **Advanced Indexing:** B-tree, Hash, GiST, GIN, BRIN, Bloom filters
4. **Full-Text Search:** Built-in text search capabilities
5. **Extensibility:** Custom functions, operators, data types
6. **Mature Ecosystem:** Wide tool support, large community
7. **Cost:** Open-source, no licensing fees
8. **Standards Compliance:** SQL standard compliant
9. **Replication:** Streaming, logical, synchronous options
10. **Performance:** Excellent for complex queries and analytics

**Cons:**
1. **Write Scalability:** Vertical scaling limitations
2. **Vacuum Overhead:** MVCC requires periodic maintenance
3. **Replication Lag:** Async replication can lag under heavy load
4. **Connection Overhead:** Each connection is a separate process
5. **Sharding Complexity:** No native sharding (requires extensions/app logic)
6. **Memory Usage:** Can be memory-intensive for large datasets
7. **Configuration Complexity:** Many tuning parameters
8. **Upgrade Challenges:** Major version upgrades require downtime

### Partitioning Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Performance** | Faster queries with partition pruning | Overhead for partition management |
| **Maintenance** | Easier to archive/drop old data | Complex partition creation logic |
| **Scalability** | Better handling of large tables | Limited by single server capacity |
| **Complexity** | Transparent to applications | Requires careful planning |

### Sharding Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Scalability** | Horizontal scaling, unlimited growth | Complex application logic |
| **Performance** | Reduced contention, parallel processing | Cross-shard queries expensive |
| **Availability** | Shard isolation limits blast radius | More failure points |
| **Cost** | Can use commodity hardware | Higher operational costs |

### CDC Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Real-time** | Near real-time data propagation | Potential lag under load |
| **Decoupling** | Loose coupling between systems | Eventual consistency |
| **Scalability** | Multiple consumers possible | Infrastructure overhead |
| **Flexibility** | Easy to add new consumers | Schema evolution challenges |

---

## 50 System Design Interview Questions

### Database Architecture & Design (10 Questions)

1. **How would you design a database schema for a social media platform with billions of users?**
   - Discuss sharding strategy (user_id based)
   - Partitioning for posts (time-based)
   - Denormalization for feeds
   - Caching strategy
   - Read replicas for timeline queries

2. **Design a multi-tenant SaaS database architecture. What isolation strategies would you use?**
   - Separate database per tenant
   - Shared database, separate schemas
   - Shared schema with tenant_id column
   - Row-level security
   - Trade-offs of each approach

3. **How would you handle a table with 10 billion rows that needs to support both OLTP and OLAP workloads?**
   - Partitioning strategy (range/hash)
   - Separate read replicas for analytics
   - Materialized views for aggregations
   - Column-oriented storage for analytics
   - CDC to data warehouse

4. **Design a database architecture for an e-commerce platform handling Black Friday traffic (100x normal load).**
   - Read replicas for product catalog
   - Sharding for orders by user_id
   - Caching layer (Redis)
   - Queue-based order processing
   - Eventual consistency for inventory

5. **How would you migrate a 5TB monolithic database to a sharded architecture with zero downtime?**
   - Dual-write strategy
   - CDC for synchronization
   - Gradual traffic migration
   - Rollback plan
   - Data consistency verification

6. **Design a time-series database for IoT sensors generating 1 million events per second.**
   - Time-based partitioning (hourly/daily)
   - Compression strategies
   - Retention policies
   - Downsampling for old data
   - TimescaleDB or custom solution

7. **How would you design a database for a ride-sharing app like Uber?**
   - Geospatial indexing (PostGIS)
   - Sharding by geographic region
   - Real-time location updates
   - Trip history partitioning
   - Driver-rider matching algorithm

8. **Design a database schema for a messaging application like WhatsApp.**
   - Sharding by user_id
   - Message storage strategy
   - Group chat handling
   - Media storage (separate)
   - Message delivery status tracking

9. **How would you design a database for a recommendation engine?**
   - User behavior events table
   - Product features table
   - Precomputed recommendations
   - Real-time vs batch processing
   - Graph database considerations

10. **Design a database architecture for a financial trading platform requiring sub-millisecond latency.**
    - In-memory database (Redis/Memcached)
    - Persistent storage strategy
    - Event sourcing pattern
    - CQRS architecture
    - Audit trail requirements

### Partitioning & Sharding (10 Questions)

11. **When would you choose range partitioning over hash partitioning?**
    - Time-series data → range
    - Even distribution → hash
    - Query patterns matter
    - Maintenance considerations
    - Examples of each

12. **How do you handle cross-shard queries efficiently?**
    - Scatter-gather pattern
    - Denormalization
    - Reference tables
    - Caching
    - Application-level aggregation

13. **Design a resharding strategy for a growing application.**
    - Consistent hashing
    - Virtual shards
    - Gradual migration
    - Dual-write period
    - Data consistency checks

14. **How would you implement global secondary indexes in a sharded database?**
    - Separate index shards
    - Scatter-gather for queries
    - Eventual consistency
    - Update propagation
    - Trade-offs

15. **Explain partition pruning and how to optimize for it.**
    - Query predicate on partition key
    - Constraint exclusion
    - Execution plan analysis
    - Index strategies
    - Performance benefits

16. **How do you handle hot partitions/shards?**
    - Identify hot keys
    - Further sub-partitioning
    - Caching layer
    - Load balancing
    - Application-level mitigation

17. **Design a partitioning strategy for a multi-region application.**
    - Geographic partitioning
    - Data residency requirements
    - Cross-region queries
    - Replication strategy
    - Latency considerations

18. **How would you implement partition maintenance automation?**
    - Automatic partition creation
    - Old partition archival
    - Monitoring and alerts
    - Retention policies
    - Backup strategies

19. **Explain the trade-offs between vertical and horizontal partitioning.**
    - Vertical: column-based split
    - Horizontal: row-based split
    - Use cases for each
    - Performance implications
    - Maintenance overhead

20. **How do you ensure data consistency across shards?**
    - Distributed transactions (2PC)
    - Saga pattern
    - Eventual consistency
    - Compensation logic
    - Idempotency

### Performance & Optimization (10 Questions)

21. **How would you optimize a slow query that scans millions of rows?**
    - EXPLAIN ANALYZE
    - Index optimization
    - Query rewriting
    - Partitioning
    - Materialized views

22. **Design a caching strategy for a high-traffic application.**
    - Cache-aside pattern
    - Write-through cache
    - Cache invalidation
    - TTL strategies
    - Cache warming

23. **How do you handle database connection pooling at scale?**
    - PgBouncer configuration
    - Pool sizing calculations
    - Connection lifecycle
    - Monitoring
    - Failover handling

24. **Explain your approach to database capacity planning.**
    - Growth projections
    - Resource monitoring
    - Benchmark testing
    - Scaling triggers
    - Cost optimization

25. **How would you optimize bulk insert operations?**
    - COPY command
    - Batch inserts
    - Disable indexes temporarily
    - Parallel loading
    - Transaction batching

26. **Design a strategy to reduce replication lag.**
    - Increase wal_sender processes
    - Network optimization
    - Async vs sync replication
    - Monitoring and alerting
    - Read replica scaling

27. **How do you optimize JOIN performance on large tables?**
    - Index on join columns
    - Partition-wise joins
    - Denormalization
    - Query hints
    - Statistics updates

28. **Explain vacuum and analyze in PostgreSQL. How do you optimize them?**
    - MVCC and dead tuples
    - Autovacuum tuning
    - Vacuum strategies
    - Analyze for statistics
    - Monitoring bloat

29. **How would you implement read-write splitting?**
    - Primary for writes
    - Replicas for reads
    - Load balancer configuration
    - Replication lag handling
    - Failover strategy

30. **Design a database monitoring and alerting system.**
    - Key metrics (CPU, memory, I/O)
    - Query performance tracking
    - Replication lag monitoring
    - Disk space alerts
    - Tools (Prometheus, Grafana)

### High Availability & Disaster Recovery (10 Questions)

31. **Design a high-availability PostgreSQL architecture.**
    - Primary-standby setup
    - Streaming replication
    - Automatic failover (Patroni)
    - Load balancing
    - Backup strategy

32. **How would you implement zero-downtime database migrations?**
    - Backward-compatible changes
    - Dual-write strategy
    - Feature flags
    - Gradual rollout
    - Rollback plan

33. **Explain your disaster recovery strategy for a critical database.**
    - RPO and RTO requirements
    - Backup frequency
    - Cross-region replication
    - Restore testing
    - Failover procedures

34. **How do you handle database failover without data loss?**
    - Synchronous replication
    - Quorum-based commits
    - Automatic failover tools
    - Split-brain prevention
    - Application retry logic

35. **Design a backup and restore strategy for a 10TB database.**
    - Incremental backups
    - Point-in-time recovery
    - Backup verification
    - Restore time optimization
    - Storage considerations

36. **How would you implement blue-green deployment for database changes?**
    - Dual database setup
    - Traffic switching
    - Data synchronization
    - Rollback capability
    - Testing strategy

37. **Explain your approach to database upgrade with minimal downtime.**
    - Replica upgrade first
    - Logical replication
    - Version compatibility
    - Testing procedures
    - Rollback plan

38. **How do you prevent and recover from database corruption?**
    - Regular backups
    - Checksums enabled
    - Monitoring for errors
    - PITR capability
    - Replica verification

39. **Design a multi-region active-active database architecture.**
    - Conflict resolution
    - Data consistency model
    - Latency considerations
    - Failover strategy
    - Cost implications

40. **How would you handle a database outage during peak traffic?**
    - Circuit breaker pattern
    - Graceful degradation
    - Queue-based processing
    - Status page updates
    - Post-mortem analysis

### CDC & Data Integration (10 Questions)

41. **Design a CDC pipeline for real-time analytics.**
    - Debezium setup
    - Kafka as message bus
    - Stream processing (Flink)
    - Target data warehouse
    - Schema evolution handling

42. **How would you implement event sourcing with PostgreSQL?**
    - Event store design
    - Aggregate snapshots
    - Replay mechanism
    - Projection updates
    - Consistency guarantees

43. **Explain your approach to data synchronization between microservices.**
    - CDC for data changes
    - Event-driven architecture
    - Saga pattern
    - Eventual consistency
    - Idempotency

44. **How do you handle schema evolution in a CDC pipeline?**
    - Backward compatibility
    - Schema registry
    - Consumer updates
    - Version management
    - Testing strategy

45. **Design a system to replicate data from PostgreSQL to Elasticsearch.**
    - Logical replication slot
    - Kafka Connect
    - Index mapping
    - Bulk indexing
    - Error handling

46. **How would you implement audit logging using CDC?**
    - Capture all changes
    - Audit table design
    - Retention policies
    - Query capabilities
    - Compliance requirements

47. **Explain the trade-offs between trigger-based and log-based CDC.**
    - Trigger overhead
    - Log-based performance
    - Completeness guarantees
    - Operational complexity
    - Use cases for each

48. **How do you ensure exactly-once delivery in a CDC pipeline?**
    - Idempotent consumers
    - Deduplication logic
    - Transaction boundaries
    - Offset management
    - Failure handling

49. **Design a data lake architecture fed by PostgreSQL CDC.**
    - CDC to Kafka
    - S3 as data lake
    - Partitioning strategy
    - Data catalog (Glue)
    - Query engine (Athena)

50. **How would you migrate from batch ETL to real-time CDC?**
    - Parallel running period
    - Data validation
    - Gradual cutover
    - Monitoring and alerting
    - Rollback capability

---

## Additional Resources

### PostgreSQL Configuration Best Practices

```ini
# postgresql.conf - Production settings

# Memory settings
shared_buffers = 8GB                    # 25% of RAM
effective_cache_size = 24GB             # 75% of RAM
work_mem = 64MB                         # Per operation
maintenance_work_mem = 2GB              # For maintenance operations

# WAL settings
wal_level = replica                     # or 'logical' for CDC
max_wal_size = 4GB
min_wal_size = 1GB
wal_compression = on
wal_buffers = 16MB

# Checkpoint settings
checkpoint_timeout = 15min
checkpoint_completion_target = 0.9

# Query planner
random_page_cost = 1.1                  # For SSD
effective_io_concurrency = 200          # For SSD
default_statistics_target = 100

# Connection settings
max_connections = 200
superuser_reserved_connections = 3

# Logging
log_destination = 'csvlog'
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%Y-%m-%d_%H%M%S.log'
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_min_duration_statement = 1000       # Log slow queries
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on

# Autovacuum
autovacuum = on
autovacuum_max_workers = 4
autovacuum_naptime = 30s
autovacuum_vacuum_scale_factor = 0.1
autovacuum_analyze_scale_factor = 0.05

# Replication
max_wal_senders = 10
max_replication_slots = 10
hot_standby = on
hot_standby_feedback = on
```

### Monitoring Queries

```sql
-- Check table sizes
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
LIMIT 20;

-- Check index usage
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;

-- Check bloat
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - 
                   pg_relation_size(schemaname||'.'||tablename)) AS indexes_size
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Check replication lag
SELECT 
    client_addr,
    state,
    sync_state,
    pg_wal_lsn_diff(pg_current_wal_lsn(), sent_lsn) AS sent_lag_bytes,
    pg_wal_lsn_diff(pg_current_wal_lsn(), write_lsn) AS write_lag_bytes,
    pg_wal_lsn_diff(pg_current_wal_lsn(), flush_lsn) AS flush_lag_bytes,
    pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS replay_lag_bytes
FROM pg_stat_replication;

-- Long-running queries
SELECT 
    pid,
    now() - query_start AS duration,
    state,
    query
FROM pg_stat_activity
WHERE state != 'idle'
  AND query NOT LIKE '%pg_stat_activity%'
ORDER BY duration DESC;
```

---

## Conclusion

This document provides a comprehensive overview of PostgreSQL architecture patterns including partitioning, sharding, and CDC. The 50 interview questions cover critical topics for senior developer, technical lead, and architect roles, focusing on system design, scalability, performance, and operational excellence.

Key takeaways:
- **Partitioning** improves query performance and maintenance
- **Sharding** enables horizontal scalability
- **CDC** enables real-time data integration
- **Trade-offs** exist for every architectural decision
- **Monitoring** and **automation** are critical for production systems
