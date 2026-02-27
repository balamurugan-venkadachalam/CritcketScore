# Elasticsearch Interview Questions & Answers - Part 2 (Questions 11-25)

## Performance & Optimization

### 11. How do you optimize Elasticsearch query performance?

**Answer:**
Query optimization involves multiple strategies from index design to query construction.

**Query Optimization Techniques:**

```java
// 1. Use Filter Context Instead of Query Context
GET /products/_search
{
  "query": {
    "bool": {
      "filter": [  // Faster: No scoring, cacheable
        { "term": { "status": "active" } },
        { "range": { "price": { "gte": 100, "lte": 1000 } } }
      ],
      "must": [   // Slower: Requires scoring
        { "match": { "name": "wireless headphones" } }
      ]
    }
  }
}

// 2. Use Keyword Fields for Exact Matching
GET /products/_search
{
  "query": {
    "term": { "category.keyword": "electronics" }  // Use .keyword field
  }
}

// 3. Optimize Range Queries
GET /logs/_search
{
  "query": {
    "range": {
      "@timestamp": {
        "gte": "now-24h/h",  // Round to hour for better caching
        "lte": "now/h"
      }
    }
  }
}

// 4. Use Source Filtering
GET /products/_search
{
  "_source": ["id", "name", "price"],  // Only return needed fields
  "query": {
    "match": { "name": "laptop" }
  }
}

// 5. Limit Results with Size and From
GET /products/_search
{
  "size": 20,  // Limit results
  "from": 0,
  "query": {
    "match_all": {}
  }
}

// 6. Use Search After for Deep Pagination
GET /products/_search
{
  "size": 20,
  "query": { "match_all": {} },
  "sort": [
    { "_id": "asc" }
  ],
  "search_after": ["doc_id_123"]  // Use last document's sort value
}
```

**Index Optimization:**
```java
// 1. Optimize Shard Count
PUT /my_index/_settings
{
  "index.number_of_shards": 3,  // Based on data size and hardware
  "index.number_of_replicas": 1
}

// 2. Force Merge for Read-Only Indices
POST /my_index/_forcemerge
{
  "max_num_segments": 1
}

// 3. Configure Refresh Interval
PUT /my_index/_settings
{
  "index.refresh_interval": "30s"  // Reduce refresh frequency for bulk indexing
}

// 4. Use Index Sorting
PUT /my_index
{
  "settings": {
    "index.sort.field": "timestamp",
    "index.sort.order": "desc"
  }
}
```

**Performance Monitoring:**
```java
// 1. Enable Slow Query Logging
PUT /_cluster/settings
{
  "logger.org.elasticsearch.index.search.slowlog.query": "DEBUG",
  "logger.org.elasticsearch.index.search.slowlog.fetch": "DEBUG"
}

// 2. Profile Queries
GET /products/_search
{
  "profile": true,  // Enable query profiling
  "query": {
    "match": { "name": "laptop" }
  }
}

// 3. Use Explain API
GET /products/_explain/1
{
  "query": {
    "match": { "name": "laptop" }
  }
}
```

### 12. What are the best practices for bulk indexing?

**Answer:**
Bulk indexing requires careful configuration for optimal performance.

**Bulk Indexing Configuration:**
```java
// 1. Optimize Bulk Request Size
public class BulkIndexingService {
    
    private static final int BATCH_SIZE = 1000;
    private static final int PARALLEL_BULKS = 4;
    
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    
    public void bulkIndex(List<Product> products) {
        List<List<Product>> batches = partition(products, BATCH_SIZE);
        
        batches.parallelStream()
            .limit(PARALLEL_BULKS)
            .forEach(this::processBatch);
    }
    
    private void processBatch(List<Product> batch) {
        List<IndexQuery> queries = batch.stream()
            .map(this::buildIndexQuery)
            .collect(Collectors.toList());
        
        elasticsearchOperations.bulkIndex(queries, Product.class);
    }
    
    private IndexQuery buildIndexQuery(Product product) {
        return new IndexQueryBuilder()
            .withId(product.getId())
            .withObject(product)
            .build();
    }
}

// 2. Disable Refresh During Bulk Indexing
PUT /products/_settings
{
  "index.refresh_interval": "-1"  // Disable refresh
}

// Bulk index documents...

PUT /products/_settings
{
  "index.refresh_interval": "1s"  // Re-enable refresh
}

// 3. Use Bulk API with Optimized Settings
POST /_bulk
{ "index": { "_index": "products", "_id": "1" } }
{ "name": "Product 1", "price": 100 }
{ "index": { "_index": "products", "_id": "2" } }
{ "name": "Product 2", "price": 200 }

// 4. Configure Thread Pool
PUT /_cluster/settings
{
  "thread_pool.bulk.size": 4,
  "thread_pool.bulk.queue_size": 1000
}
```

**Bulk Indexing Best Practices:**
- Use appropriate batch size (1000-5000 documents)
- Disable refresh during bulk operations
- Use parallel processing for large datasets
- Monitor heap memory usage
- Use routing for better data locality
- Implement retry logic for failed operations

### 13. How do you implement caching in Elasticsearch?

**Answer:**
Elasticsearch provides multiple caching mechanisms for performance optimization.

**Cache Types and Configuration:**
```java
// 1. Query Cache Configuration
PUT /_cluster/settings
{
  "indices.queries.cache.size": "20%",
  "indices.queries.cache.expire": "1h"
}

// 2. Field Data Cache
PUT /_cluster/settings
{
  "indices.fielddata.cache.size": "40%",
  "indices.fielddata.cache.expire": "30m"
}

// 3. Request Cache
PUT /my_index/_settings
{
  "index.requests.cache.enable": true
}

GET /my_index/_search?request_cache=true
{
  "query": {
    "term": { "status": "active" }
  }
}

// 4. Shard Request Cache
PUT /my_index/_settings
{
  "index.shard.request_cache.enable": true
}
```

**Application-Level Caching:**
```java
@Service
public class SearchCacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    
    @Cacheable(value = "search-results", key = "#query.hashCode()")
    public Page<Product> searchProducts(SearchQuery query) {
        NativeSearchQuery searchQuery = buildSearchQuery(query);
        SearchHits<Product> searchHits = 
            elasticsearchOperations.search(searchQuery, Product.class);
        
        return new PageImpl<>(
            searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList()),
            query.getPageable(),
            searchHits.getTotalHits()
        );
    }
    
    @CacheEvict(value = "search-results", allEntries = true)
    public void clearCache() {
        // Clear all cached search results
    }
}
```

**Cache Warming Strategy:**
```java
@Component
public class CacheWarmer {
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void warmCache() {
        List<String> popularQueries = getPopularQueries();
        
        popularQueries.parallelStream()
            .forEach(query -> {
                try {
                    searchService.searchProducts(new SearchQuery(query));
                } catch (Exception e) {
                    log.warn("Cache warming failed for query: {}", query, e);
                }
            });
    }
}
```

### 14. How do you handle high availability and disaster recovery?

**Answer:**
High availability requires proper cluster configuration and backup strategies.

**Cluster High Availability:**
```java
// 1. Minimum High Availability Setup (3 nodes)
PUT /_cluster/settings
{
  "discovery.zen.minimum_master_nodes": 2,
  "cluster.routing.allocation.awareness.attributes": ["zone"],
  "cluster.routing.allocation.awareness.force.zone.values": ["zone1", "zone2", "zone3"]
}

// 2. Cross-Cluster Replication (CCR)
PUT /_cluster/settings
{
  "cluster.remote.prod_cluster.seeds": [
    "es-prod-1:9300",
    "es-prod-2:9300"
  ]
}

PUT /follower_index/_ccr/follow
{
  "remote_cluster": "prod_cluster",
  "leader_index": "leader_index"
}

// 3. Snapshot and Restore
PUT /_snapshot/backup_repo
{
  "type": "s3",
  "settings": {
    "bucket": "elasticsearch-backups",
    "region": "us-west-2",
    "base_path": "snapshots"
  }
}

// Create snapshot
PUT /_snapshot/backup_repo/snapshot_1
{
  "indices": "products,orders",
  "ignore_unavailable": true,
  "include_global_state": false
}

// Restore snapshot
POST /_snapshot/backup_repo/snapshot_1/_restore
{
  "indices": "products",
  "ignore_unavailable": true,
  "include_global_state": false,
  "rename_pattern": "(.+)",
  "rename_replacement": "restored_$1"
}
```

**Disaster Recovery Strategy:**
```java
// 1. Automated Snapshot Policy
PUT /_slm/policy/daily-snapshots
{
  "schedule": "0 2 * * *",  // Daily at 2 AM
  "name": "<daily-snapshot-{now/d}>",
  "repository": "backup_repo",
  "config": {
    "indices": ["*"],
    "ignore_unavailable": true,
    "include_global_state": false
  },
  "retention": {
    "count": 30,
    "min_count": 5,
    "expire_after": "30d"
  }
}

// 2. Cluster Health Monitoring
public class ClusterHealthMonitor {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorClusterHealth() {
        ClusterHealth health = elasticsearchTemplate.execute(
            client -> client.cluster().health()
        );
        
        if (health.getStatus() != ClusterHealthStatus.GREEN) {
            alertService.sendAlert("Cluster health: " + health.getStatus());
        }
        
        if (health.getUnassignedShards() > 0) {
            alertService.sendAlert("Unassigned shards: " + health.getUnassignedShards());
        }
    }
}
```

### 15. What are the best practices for index lifecycle management (ILM)?

**Answer:**
ILM automates index management through different phases of data lifecycle.

**ILM Policy Configuration:**
```java
// 1. Complete ILM Policy
PUT /_ilm/policy/logs_policy
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_size": "10GB",
            "max_age": "24h",
            "max_docs": 1000000
          },
          "set_priority": {
            "priority": 100
          },
          "allocate": {
            "number_of_replicas": 1,
            "require": {
              "data": "hot"
            }
          }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "allocate": {
            "number_of_replicas": 0,
            "require": {
              "data": "warm"
            }
          },
          "forcemerge": {
            "max_num_segments": 1
          },
          "set_priority": {
            "priority": 50
          }
        }
      },
      "cold": {
        "min_age": "30d",
        "actions": {
          "allocate": {
            "number_of_replicas": 0,
            "require": {
              "data": "cold"
            }
          },
          "set_priority": {
            "priority": 0
          }
        }
      },
      "frozen": {
        "min_age": "90d",
        "actions": {
          "allocate": {
            "number_of_replicas": 0,
            "require": {
              "data": "frozen"
            }
          },
          "searchable_snapshot": {
            "snapshot_repository": "backup_repo"
          }
        }
      },
      "delete": {
        "min_age": "365d"
      }
    }
  }
}

// 2. Apply ILM to Index Template
PUT /_index_template/logs_template
{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "index.lifecycle.name": "logs_policy",
      "index.lifecycle.rollover_alias": "logs-write"
    },
    "mappings": {
      "properties": {
        "@timestamp": { "type": "date" },
        "level": { "type": "keyword" },
        "message": { "type": "text" }
      }
    }
  }
}

// 3. Bootstrap Index for Rollover
PUT /logs-000001
{
  "aliases": {
    "logs-write": {
      "is_write_index": true
    }
  }
}
```

**ILM Best Practices:**
- Define clear data retention policies
- Use appropriate hardware tiers (hot/warm/cold)
- Monitor phase transitions
- Test restore procedures regularly
- Consider searchable snapshots for long-term storage
- Use data streams for simplified management

---

## System Design & Scalability

### 16. How do you design a scalable Elasticsearch architecture?

**Answer:**
Scalable architecture requires proper planning for data growth, query load, and maintenance.

**Multi-Tier Architecture:**
```java
// 1. Hot-Warm-Cold Architecture
/*
Hot Tier (SSD, High Memory):
- Recent data (last 7 days)
- High query volume
- Frequent writes
- 3 replicas for HA

Warm Tier (SSD, Medium Memory):
- Medium age data (7-90 days)
- Moderate query volume
- Read-only
- 1 replica

Cold Tier (HDD, Low Memory):
- Old data (90+ days)
- Low query volume
- Read-only
- 0 replicas
*/

// 2. Node Configuration
PUT /_cluster/settings
{
  "cluster.routing.allocation.awareness.attributes": ["tier"],
  "cluster.routing.allocation.awareness.force.tier.values": ["hot", "warm", "cold"]
}

// Node attributes in elasticsearch.yml
/*
node.attr.tier: hot
node.attr.rack: rack1
node.attr.zone: zone1
*/
```

**Cross-Region Replication:**
```java
// 1. Active-Active Setup
PUT /_cluster/settings
{
  "cluster.remote.europe.seeds": ["es-eu-1:9300", "es-eu-2:9300"],
  "cluster.remote.asia.seeds": ["es-asia-1:9300", "es-asia-2:9300"]
}

// 2. Bidirectional Replication
PUT /products_europe/_ccr/follow
{
  "remote_cluster": "asia",
  "leader_index": "products_asia"
}

PUT /products_asia/_ccr/follow
{
  "remote_cluster": "europe",
  "leader_index": "products_europe"
}

// 3. Geo-Distributed Search
public class GeoDistributedSearchService {
    
    public SearchResult searchAcrossRegions(SearchQuery query) {
        List<CompletableFuture<SearchResult>> futures = Arrays.asList(
            CompletableFuture.supplyAsync(() -> searchInEurope(query)),
            CompletableFuture.supplyAsync(() -> searchInAsia(query)),
            CompletableFuture.supplyAsync(() -> searchInUS(query))
        );
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allOf.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Handle timeout and partial results
        }
        
        return mergeResults(futures);
    }
}
```

**Sharding Strategy:**
```java
// 1. Custom Sharding for Time-Series
PUT /_index_template/time_series_template
{
  "index_patterns": ["metrics-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_routing_shards": 30,  // For future scaling
      "index.routing.allocation.include.tier": "hot"
    }
  }
}

// 2. Shard Splitting for Scaling
POST /my_index/_shrink/shrunk_index
{
  "settings": {
    "index.number_of_shards": 1,
    "index.number_of_replicas": 0
  }
}

POST /my_index/_split/split_index
{
  "settings": {
    "index.number_of_shards": 6
  }
}
```

### 17. How do you handle real-time data streaming to Elasticsearch?

**Answer:**
Real-time data streaming requires efficient ingestion pipelines and proper indexing strategies.

**Streaming Architecture:**
```java
// 1. Kafka to Elasticsearch Pipeline
@Component
public class ElasticsearchSink {
    
    @KafkaListener(topics = "events")
    public void handleEvent(Event event) {
        try {
            IndexRequest indexRequest = new IndexRequest("events")
                .id(event.getId())
                .source(convertToJson(event))
                .opType(DocWriteRequest.OpType.INDEX);
            
            elasticsearchClient.index(indexRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            // Handle indexing errors
            errorHandler.handle(event, e);
        }
    }
}

// 2. Bulk Processor for High Throughput
public class BulkProcessorService {
    
    private BulkProcessor bulkProcessor;
    
    @PostConstruct
    public void init() {
        BulkProcessor.Listener listener = new BulkProcessor.Listener() {
            @Override
            public void beforeBulk(long executionId, BulkRequest request) {
                log.info("Executing bulk request with {} actions", request.numberOfActions());
            }
            
            @Override
            public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                if (response.hasFailures()) {
                    log.error("Bulk request failures: {}", response.buildFailureMessage());
                }
            }
        };
        
        bulkProcessor = BulkProcessor.builder(
            (request, listener) -> {
                try {
                    return elasticsearchClient.bulk(request, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            listener)
            .setBulkActions(1000)
            .setBulkSize(new ByteSizeValue(5, ByteSizeUnit.MB))
            .setFlushInterval(TimeValue.timeValueSeconds(5))
            .setConcurrentRequests(4)
            .build();
    }
    
    public void indexDocument(String index, String id, Map<String, Object> document) {
        IndexRequest request = new IndexRequest(index)
            .id(id)
            .source(document);
        
        bulkProcessor.add(request);
    }
}
```

**Ingest Pipeline for Data Enrichment:**
```java
// 1. Define Ingest Pipeline
PUT /_ingest/pipeline/enrich_events
{
  "description": "Enrich events with geo data",
  "processors": [
    {
      "date": {
        "field": "timestamp",
        "formats": ["UNIX_MS"]
      }
    },
    {
      "geoip": {
        "field": "client_ip",
        "target_field": "geo"
      }
    },
    {
      "user_agent": {
        "field": "user_agent_string",
        "target_field": "user_agent"
      }
    },
    {
      "script": {
        "source": """
          if (ctx.geo != null && ctx.geo.city_name != null) {
            ctx.city = ctx.geo.city_name.toLowerCase();
          }
          """
      }
    }
  ]
}

// 2. Use Pipeline During Indexing
POST /events/_doc?pipeline=enrich_events
{
  "timestamp": "1640995200000",
  "client_ip": "8.8.8.8",
  "user_agent_string": "Mozilla/5.0...",
  "event_type": "page_view"
}
```

### 18. How do you implement multi-tenancy in Elasticsearch?

**Answer:**
Multi-tenancy requires proper data isolation and security controls.

**Multi-Tenancy Strategies:**

**1. Index-per-Tenant:**
```java
// Separate indices for each tenant
PUT /tenant_1_products
PUT /tenant_2_products
PUT /tenant_3_products

// Tenant-specific search
public class TenantSearchService {
    
    public SearchResult search(String tenantId, SearchQuery query) {
        String indexName = "tenant_" + tenantId + "_products";
        
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(buildQuery(query));
        
        searchRequest.source(sourceBuilder);
        
        return executeSearch(searchRequest);
    }
}
```

**2. Field-based Tenancy:**
```java
// Single index with tenant field
PUT /products
{
  "mappings": {
    "properties": {
      "tenant_id": {
        "type": "keyword",
        "index": true
      },
      "name": { "type": "text" },
      "price": { "type": "scaled_float" }
    }
  }
}

// Always filter by tenant
GET /products/_search
{
  "query": {
    "bool": {
      "filter": [
        { "term": { "tenant_id": "tenant_1" } },
        { "match": { "name": "laptop" } }
      ]
    }
  }
}
```

**3. Alias-based Tenancy:**
```java
// Create filtered aliases for each tenant
POST /_aliases
{
  "actions": [
    {
      "add": {
        "index": "products",
        "alias": "tenant_1_products",
        "filter": {
          "term": { "tenant_id": "tenant_1" }
        }
      }
    },
    {
      "add": {
        "index": "products",
        "alias": "tenant_2_products",
        "filter": {
          "term": { "tenant_id": "tenant_2" }
        }
      }
    }
  ]
}

// Search using tenant alias
GET /tenant_1_products/_search
{
  "query": {
    "match": { "name": "laptop" }
  }
}
```

**Security Implementation:**
```java
// 1. Role-Based Access Control
PUT /_security/role/tenant_1_user
{
  "indices": [
    {
      "names": ["tenant_1_*"],
      "privileges": ["read", "write"]
    }
  ]
}

// 2. Document-Level Security
PUT /_security/role/tenant_1_limited
{
  "indices": [
    {
      "names": ["products"],
      "privileges": ["read"],
      "query": {
        "term": { "tenant_id": "tenant_1" }
      }
    }
  ]
}

// 3. Field-Level Security
PUT /_security/role/tenant_1_readonly
{
  "indices": [
    {
      "names": ["products"],
      "privileges": ["read"],
      "field_security": {
        "grant": ["name", "price"],
        "except": ["cost", "margin"]
      }
    }
  ]
}
```

### 19. How do you monitor and maintain Elasticsearch clusters?

**Answer:**
Comprehensive monitoring ensures cluster health and performance optimization.

**Monitoring Components:**
```java
// 1. Cluster Health Monitoring
@Service
public class ClusterMonitoringService {
    
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorClusterHealth() {
        ClusterHealthResponse health = elasticsearchClient.cluster()
            .health(RequestOptions.DEFAULT);
        
        // Check cluster status
        ClusterHealthStatus status = health.getStatus();
        if (status != ClusterHealthStatus.GREEN) {
            alertService.sendAlert("Cluster status: " + status);
        }
        
        // Check unassigned shards
        int unassignedShards = health.getUnassignedShards();
        if (unassignedShards > 0) {
            alertService.sendAlert("Unassigned shards: " + unassignedShards);
        }
        
        // Check pending tasks
        int pendingTasks = health.getPendingTasks();
        if (pendingTasks > 10) {
            alertService.sendAlert("High pending tasks: " + pendingTasks);
        }
        
        // Store metrics
        metricsCollector.recordClusterHealth(status, unassignedShards, pendingTasks);
    }
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorNodeStats() {
        NodesStatsResponse stats = elasticsearchClient.nodes()
            .stats(RequestOptions.DEFAULT);
        
        for (NodeStats nodeStats : stats.getNodes().values()) {
            // Monitor heap usage
            long heapUsed = nodeStats.getJvm().getHeap().getUsed().getBytes();
            long heapMax = nodeStats.getJvm().getHeap().getMax().getBytes();
            double heapUsagePercent = (double) heapUsed / heapMax * 100;
            
            if (heapUsagePercent > 85) {
                alertService.sendAlert("High heap usage: " + heapUsagePercent + "%");
            }
            
            // Monitor disk usage
            long diskUsed = nodeStats.getFs().getTotal().getAvailable().getBytes();
            long diskTotal = nodeStats.getFs().getTotal().getTotal().getBytes();
            double diskUsagePercent = (1.0 - (double) diskUsed / diskTotal) * 100;
            
            if (diskUsagePercent > 90) {
                alertService.sendAlert("High disk usage: " + diskUsagePercent + "%");
            }
            
            // Monitor CPU usage
            double cpuUsage = nodeStats.getOs().getCpu().getPercent();
            if (cpuUsage > 80) {
                alertService.sendAlert("High CPU usage: " + cpuUsage + "%");
            }
        }
    }
}
```

**Performance Metrics:**
```java
// 1. Index Performance Metrics
GET /_cat/indices?v&h=index,docs.count,store.size,segments.count,segments.memory&format=json

// 2. Search Performance Metrics
GET /_cat/thread_pool/search?v&h=node,name,active,queue,rejected,type&format=json

// 3. Custom Metrics Collection
@Component
public class ElasticsearchMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public ElasticsearchMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordSearchLatency(String index, long duration) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("elasticsearch.search.duration")
            .tag("index", index)
            .register(meterRegistry));
    }
    
    public void recordIndexingRate(String index, int documentCount) {
        meterRegistry.counter("elasticsearch.indexing.documents")
            .tag("index", index)
            .increment(documentCount);
    }
    
    public void recordQueryCacheHitRate(String index, double hitRate) {
        meterRegistry.gauge("elasticsearch.cache.hit_rate", 
            Tags.of("index", index), hitRate);
    }
}
```

**Maintenance Tasks:**
```java
// 1. Index Maintenance Scheduler
@Service
public class IndexMaintenanceService {
    
    @Scheduled(cron = "0 2 * * * *") // Daily at 2 AM
    public void performMaintenance() {
        // Force merge old indices
        List<String> oldIndices = findOldIndices(30); // Older than 30 days
        
        for (String index : oldIndices) {
            try {
                elasticsearchClient.indices()
                    .forcemerge(new ForceMergeRequest(index)
                        .maxNumSegments(1)
                        .onlyExpungeDeletes(true),
                        RequestOptions.DEFAULT);
                
                log.info("Force merged index: {}", index);
            } catch (Exception e) {
                log.error("Failed to force merge index: {}", index, e);
            }
        }
        
        // Clean up old snapshots
        cleanupOldSnapshots();
        
        // Optimize shard allocation
        optimizeShardAllocation();
    }
    
    @Scheduled(cron = "0 3 * * 0") // Weekly on Sunday at 3 AM
    public def cleanupOldIndices() {
        List<String> indicesToDelete = findOldIndices(90); // Older than 90 days
        
        for (String index : indicesToDelete) {
            try {
                elasticsearchClient.indices()
                    .delete(new DeleteIndexRequest(index),
                        RequestOptions.DEFAULT);
                
                log.info("Deleted old index: {}", index);
            } catch (Exception e) {
                log.error("Failed to delete index: {}", index, e);
            }
        }
    }
}
```

### 20. How do you implement search analytics and A/B testing?

**Answer:**
Search analytics provide insights into user behavior and search effectiveness.

**Search Analytics Implementation:**
```java
// 1. Search Event Tracking
@Component
public class SearchAnalyticsService {
    
    public void trackSearch(String userId, String query, SearchResult result) {
        SearchEvent event = SearchEvent.builder()
            .userId(userId)
            .query(query)
            .resultCount(result.getTotalHits())
            .clickedDocuments(result.getClickedDocuments())
            .searchDuration(result.getDuration())
            .timestamp(LocalDateTime.now())
            .build();
        
        // Store in analytics index
        indexSearchEvent(event);
        
        // Update real-time metrics
        updateRealTimeMetrics(query, result);
    }
    
    public void trackClick(String userId, String query, String documentId, int position) {
        ClickEvent event = ClickEvent.builder()
            .userId(userId)
            .query(query)
            .documentId(documentId)
            .position(position)
            .timestamp(LocalDateTime.now())
            .build();
        
        indexClickEvent(event);
    }
    
    private void indexSearchEvent(SearchEvent event) {
        IndexRequest request = new IndexRequest("search_events")
            .source(convertToJson(event));
        
        try {
            elasticsearchClient.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("Failed to index search event", e);
        }
    }
}

// 2. Analytics Dashboard Queries
@Service
public class AnalyticsService {
    
    public SearchAnalytics getSearchAnalytics(LocalDate from, LocalDate to) {
        // Popular queries
        List<QueryStats> popularQueries = getPopularQueries(from, to);
        
        // Click-through rates
        Map<String, Double> ctrByQuery = getClickThroughRates(from, to);
        
        // Zero result queries
        List<String> zeroResultQueries = getZeroResultQueries(from, to);
        
        // Search volume trends
        List<SearchVolume> searchTrends = getSearchVolumeTrends(from, to);
        
        return SearchAnalytics.builder()
            .popularQueries(popularQueries)
            .clickThroughRates(ctrByQuery)
            .zeroResultQueries(zeroResultQueries)
            .searchTrends(searchTrends)
            .build();
    }
    
    private List<QueryStats> getPopularQueries(LocalDate from, LocalDate to) {
        SearchRequest request = new SearchRequest("search_events");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        
        sourceBuilder.query(QueryBuilders.rangeQuery("timestamp")
            .gte(from).lte(to));
        
        sourceBuilder.aggregation(
            AggregationBuilders.terms("popular_queries")
                .field("query.keyword")
                .size(20)
        );
        
        // Execute query and extract results
        return extractQueryStats(request);
    }
}
```

**A/B Testing Framework:**
```java
// 1. A/B Test Configuration
@Data
public class SearchExperiment {
    private String experimentId;
    private String name;
    private double trafficSplit;
    private Map<String, Object> variantA;
    private Map<String, Object> variantB;
    private LocalDate startDate;
    private LocalDate endDate;
}

// 2. Experiment Service
@Service
public class SearchExperimentService {
    
    @Autowired
    private ExperimentRepository experimentRepository;
    
    public SearchQuery applyExperiment(String userId, SearchQuery originalQuery) {
        List<SearchExperiment> activeExperiments = 
            experimentRepository.findActiveExperiments();
        
        SearchQuery modifiedQuery = originalQuery;
        
        for (SearchExperiment experiment : activeExperiments) {
            if (shouldIncludeInExperiment(userId, experiment)) {
                modifiedQuery = applyVariant(userId, modifiedQuery, experiment);
            }
        }
        
        return modifiedQuery;
    }
    
    private boolean shouldIncludeInExperiment(String userId, SearchExperiment experiment) {
        // Use consistent hashing for traffic splitting
        int hash = Math.abs(userId.hashCode());
        return (hash % 100) < (experiment.getTrafficSplit() * 100);
    }
    
    private SearchQuery applyVariant(String userId, SearchQuery query, SearchExperiment experiment) {
        String variant = selectVariant(userId, experiment);
        
        if ("A".equals(variant)) {
            return applyVariantA(query, experiment.getVariantA());
        } else {
            return applyVariantB(query, experiment.getVariantB());
        }
    }
    
    private String selectVariant(String userId, SearchExperiment experiment) {
        int hash = Math.abs((userId + experiment.getExperimentId()).hashCode());
        return (hash % 2) == 0 ? "A" : "B";
    }
}

// 3. Experiment Results Analysis
@Service
public class ExperimentAnalysisService {
    
    public ExperimentResults analyzeExperiment(String experimentId) {
        SearchExperiment experiment = experimentRepository.findById(experimentId);
        
        // Get metrics for both variants
        VariantMetrics variantA = getVariantMetrics(experimentId, "A");
        VariantMetrics variantB = getVariantMetrics(experimentId, "B");
        
        // Calculate statistical significance
        StatisticalTestResult significanceTest = performSignificanceTest(
            variantA, variantB);
        
        return ExperimentResults.builder()
            .experiment(experiment)
            .variantA(variantA)
            .variantB(variantB)
            .significanceTest(significanceTest)
            .winner(determineWinner(variantA, variantB, significanceTest))
            .build();
    }
    
    private StatisticalTestResult performSignificanceTest(
            VariantMetrics variantA, VariantMetrics variantB) {
        
        // Implement chi-square test or t-test
        double pValue = calculatePValue(variantA, variantB);
        boolean isSignificant = pValue < 0.05;
        
        return StatisticalTestResult.builder()
            .pValue(pValue)
            .isSignificant(isSignificant)
            .confidenceInterval(calculateConfidenceInterval(variantA, variantB))
            .build();
    }
}
```

---

*Continued with final questions 21-25...*
