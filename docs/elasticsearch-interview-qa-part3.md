# Elasticsearch Interview Questions & Answers - Part 3 (Questions 21-25)

## Real-World Scenarios & Advanced Topics

### 21. How do you handle geospatial search in Elasticsearch?

**Answer:**
Elasticsearch provides powerful geospatial capabilities for location-based queries.

**Geospatial Data Types:**
```java
// 1. Geo-point for precise locations
PUT /locations
{
  "mappings": {
    "properties": {
      "name": { "type": "text" },
      "location": {
        "type": "geo_point"
      },
      "address": {
        "type": "object",
        "properties": {
          "city": { "type": "text" },
          "country": { "type": "keyword" }
        }
      }
    }
  }
}

// Index documents with geo points
PUT /locations/_doc/1
{
  "name": "Central Park",
  "location": {
    "lat": 40.7829,
    "lon": -73.9654
  },
  "address": {
    "city": "New York",
    "country": "USA"
  }
}

// 2. Geo-shape for areas and boundaries
PUT /regions
{
  "mappings": {
    "properties": {
      "name": { "type": "text" },
      "boundary": {
        "type": "geo_shape"
      }
    }
  }
}

// Index polygon
PUT /regions/_doc/1
{
  "name": "Manhattan",
  "boundary": {
    "type": "polygon",
    "coordinates": [[
      [-74.0200, 40.7000],
      [-73.9300, 40.7000],
      [-73.9300, 40.8000],
      [-74.0200, 40.8000],
      [-74.0200, 40.7000]
    ]]
  }
}
```

**Geospatial Queries:**
```java
// 1. Distance Query
GET /locations/_search
{
  "query": {
    "bool": {
      "filter": {
        "geo_distance": {
          "distance": "5km",
          "location": {
            "lat": 40.7580,
            "lon": -73.9855
          }
        }
      }
    }
  },
  "sort": [
    {
      "_geo_distance": {
        "location": {
          "lat": 40.7580,
          "lon": -73.9855
        },
        "order": "asc",
        "unit": "km"
      }
    }
  ]
}

// 2. Bounding Box Query
GET /locations/_search
{
  "query": {
    "bool": {
      "filter": {
        "geo_bounding_box": {
          "location": {
            "top_left": {
              "lat": 40.8000,
              "lon": -74.0200
            },
            "bottom_right": {
              "lat": 40.7000,
              "lon": -73.9300
            }
          }
        }
      }
    }
  }
}

// 3. GeoShape Query
GET /regions/_search
{
  "query": {
    "bool": {
      "filter": {
        "geo_shape": {
          "boundary": {
            "shape": {
              "type": "point",
              "coordinates": [-73.9855, 40.7580]
            },
            "relation": "within"
          }
        }
      }
    }
  }
}

// 4. GeoHash Grid Aggregation
GET /locations/_search
{
  "size": 0,
  "aggs": {
    "zoomed_in": {
      "geohash_grid": {
        "field": "location",
        "precision": 5
      }
    }
  }
}
```

**Real-world Use Cases:**
```java
// 1. Store Finder Application
@Service
public class StoreFinderService {
    
    public List<Store> findNearbyStores(double lat, double lon, double radiusKm) {
        SearchRequest request = new SearchRequest("stores");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        
        // Geo distance filter
        sourceBuilder.query(QueryBuilders.geoDistanceQuery("location")
            .point(lat, lon)
            .distance(radiusKm + "km"));
        
        // Sort by distance
        sourceBuilder.sort(SortBuilders.geoDistanceSort("location", lat, lon)
            .unit(DistanceUnit.KILOMETERS)
            .order(SortOrder.ASC));
        
        request.source(sourceBuilder);
        
        return executeSearch(request);
    }
    
    public List<Store> findStoresInArea(List<double[]> polygon) {
        SearchRequest request = new SearchRequest("stores");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        
        // Geo polygon filter
        sourceBuilder.query(QueryBuilders.geoPolygonQuery("location", polygon));
        
        request.source(sourceBuilder);
        
        return executeSearch(request);
    }
}

// 2. Location-based Analytics
@Service
public class LocationAnalyticsService {
    
    public Map<String, Long> getStoreDensityByRegion(double lat, double lon, double radiusKm) {
        SearchRequest request = new SearchRequest("stores");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        
        sourceBuilder.query(QueryBuilders.geoDistanceQuery("location")
            .point(lat, lon)
            .distance(radiusKm + "km"));
        
        // Geohash grid aggregation
        sourceBuilder.aggregation(
            AggregationBuilders.geohashGrid("density_map")
                .field("location")
                .precision(6) // About 1.2km x 1.2km
        );
        
        request.source(sourceBuilder);
        
        return extractDensityMap(executeSearch(request));
    }
}
```

### 22. How do you implement machine learning and anomaly detection in Elasticsearch?

**Answer:**
Elasticsearch provides built-in machine learning capabilities for anomaly detection and forecasting.

**Anomaly Detection Setup:**
```java
// 1. Create ML Job for Anomaly Detection
PUT /_ml/anomaly_detectors/cpu_anomaly_detector
{
  "analysis_config": {
    "bucket_span": "5m",
    "detectors": [
      {
        "detector_description": "High CPU usage",
        "function": "high_count",
        "by_field_name": "host",
        "over_field_name": "metric_type"
      }
    ]
  },
  "data_description": {
    "time_field": "@timestamp",
    "time_format": "epoch_ms"
  },
  "model_plot_config": {
    "enabled": true
  }
}

// 2. Start ML Job
POST /_ml/anomaly_detectors/cpu_anomaly_detector/_open

// 3. Feed Data to ML Job
POST /_ml/anomaly_detectors/cpu_anomaly_detector/_data
{
  "data": [
    {
      "@timestamp": 1640995200000,
      "host": "server-1",
      "metric_type": "cpu",
      "value": 85.5
    }
  ]
}
```

**Real-time Anomaly Detection:**
```java
// 1. Anomaly Detection Service
@Service
public class AnomalyDetectionService {
    
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    
    public void detectAnomalies(String metricName, double value, String host) {
        try {
            // Check for existing anomalies
            GetAnomaliesRequest request = new GetAnomaliesRequest("cpu_anomaly_detector");
            GetAnomaliesResponse response = elasticsearchClient.ml()
                .getAnomalies(request, RequestOptions.DEFAULT);
            
            List<AnomalyRecord> recentAnomalies = response.getAnomalies().stream()
                .filter(a -> a.getTimestamp() > System.currentTimeMillis() - 3600000) // Last hour
                .collect(Collectors.toList());
            
            // If too many recent anomalies, trigger alert
            if (recentAnomalies.size() > 5) {
                alertService.sendCriticalAlert(
                    "High anomaly frequency detected for host: " + host);
            }
            
        } catch (Exception e) {
            log.error("Failed to detect anomalies", e);
        }
    }
    
    public List<AnomalyRecord> getRecentAnomalies(int hours) {
        try {
            GetAnomaliesRequest request = new GetAnomaliesRequest("cpu_anomaly_detector");
            request.setStartTime(System.currentTimeMillis() - hours * 3600000L);
            request.setEndTime(System.currentTimeMillis());
            
            GetAnomaliesResponse response = elasticsearchClient.ml()
                .getAnomalies(request, RequestOptions.DEFAULT);
            
            return response.getAnomalies();
        } catch (Exception e) {
            log.error("Failed to get anomalies", e);
            return Collections.emptyList();
        }
    }
}

// 2. Forecasting Service
@Service
public class ForecastingService {
    
    public ForecastResult forecastMetric(String metricName, int days) {
        try {
            // Create forecasting job
            PutJobRequest jobRequest = new PutJobRequest(metricName + "_forecast");
            jobRequest.setAnalysisConfig(createForecastConfig());
            
            elasticsearchClient.ml().putJob(jobRequest, RequestOptions.DEFAULT);
            
            // Open job and forecast
            OpenJobRequest openRequest = new OpenJobRequest(metricName + "_forecast");
            elasticsearchClient.ml().openJob(openRequest, RequestOptions.DEFAULT);
            
            ForecastRequest forecastRequest = new ForecastRequest(metricName + "_forecast");
            forecastRequest.setDuration(days + "d");
            
            ForecastResponse response = elasticsearchClient.ml()
                .forecast(forecastRequest, RequestOptions.DEFAULT);
            
            return convertToForecastResult(response);
            
        } catch (Exception e) {
            log.error("Failed to create forecast", e);
            return null;
        }
    }
    
    private AnalysisConfig createForecastConfig() {
        AnalysisConfig config = new AnalysisConfig();
        config.setBucketSpan("1h");
        config.setDetectors(Arrays.asList(
            new Detector("mean", "value")
        ));
        return config;
    }
}
```

### 23. How do you handle search relevance and scoring customization?

**Answer:**
Search relevance can be customized through various scoring mechanisms and boosting strategies.

**Scoring Customization:**
```java
// 1. Function Score Query for Custom Relevance
GET /products/_search
{
  "query": {
    "function_score": {
      "query": {
        "multi_match": {
          "query": "wireless headphones",
          "fields": ["name^3", "description^2", "tags"]
        }
      },
      "functions": [
        {
          "filter": { "term": { "featured": true } },
          "weight": 2.0
        },
        {
          "filter": { "range": { "rating": { "gte": 4.5 } } },
          "weight": 1.5
        },
        {
          "gauss": {
            "price": {
              "origin": 100,
              "scale": 50,
              "offset": 10
            }
          }
        },
        {
          "field_value_factor": {
            "field": "popularity",
            "modifier": "log1p",
            "factor": 0.1
          }
        }
      ],
      "score_mode": "multiply",
      "boost_mode": "replace"
    }
  }
}

// 2. Learning to Rank (LTR)
PUT /_ltr/_featureset/product_features
{
  "featureset": {
    "features": [
      {
        "name": "title_match",
        "params": ["keywords"],
        "template_language": "mustache",
        "template": {
          "match": {
            "title": "{{keywords}}"
          }
        }
      },
      {
        "name": "price_feature",
        "params": [],
        "template": {
          "script": {
            "source": "doc['price'].value"
          }
        }
      },
      {
        "name": "rating_feature",
        "params": [],
        "template": {
          "script": {
            "source": "doc['rating'].value"
          }
        }
      }
    ]
  }
}

// Create LTR model
PUT /_ltr/_model/product_ranking_model
{
  "model": {
    "type": "model/xgboost",
    "definition": "base64-encoded-model"
  }
}

// Use LTR in search
GET /products/_search
{
  "query": {
    "sltr": {
      "params": {
        "keywords": "wireless headphones"
      },
      "model": "product_ranking_model"
    }
  }
}
```

**Relevance Tuning:**
```java
// 1. Custom Similarity Algorithm
PUT /products
{
  "settings": {
    "similarity": {
      "custom_bm25": {
        "type": "BM25",
        "k1": 1.2,
        "b": 0.75
      }
    }
  },
  "mappings": {
    "properties": {
      "name": {
        "type": "text",
        "similarity": "custom_bm25"
      },
      "description": {
        "type": "text",
        "similarity": "custom_bm25"
      }
    }
  }
}

// 2. Script Scoring for Complex Logic
GET /products/_search
{
  "query": {
    "script_score": {
      "query": { "match_all": {} },
      "script": {
        "source": """
          double score = 0;
          
          // Boost based on rating
          if (doc['rating'].size() != 0) {
            score += doc['rating'].value * 10;
          }
          
          // Boost based on recent sales
          if (doc['recent_sales'].size() != 0) {
            score += Math.log(doc['recent_sales'].value + 1) * 5;
          }
          
          // Penalty for out of stock
          if (doc['stock_quantity'].value == 0) {
            score *= 0.1;
          }
          
          return score;
          """
      }
    }
  }
}

// 3. Search Analytics for Relevance Improvement
@Service
public class RelevanceAnalyticsService {
    
    public void trackSearchMetrics(String query, List<String> resultIds, 
                                List<String> clickedIds, long duration) {
        SearchMetrics metrics = SearchMetrics.builder()
            .query(query)
            .resultIds(resultIds)
            .clickedIds(clickedIds)
            .duration(duration)
            .timestamp(LocalDateTime.now())
            .build();
        
        // Store metrics for analysis
        indexSearchMetrics(metrics);
        
        // Calculate click-through rate
        double ctr = (double) clickedIds.size() / resultIds.size();
        
        // Update query performance metrics
        updateQueryPerformance(query, ctr, duration);
    }
    
    public List<QueryPerformance> getLowPerformingQueries() {
        SearchRequest request = new SearchRequest("search_metrics");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        
        sourceBuilder.query(QueryBuilders.rangeQuery("timestamp")
            .gte(LocalDateTime.now().minusDays(7)));
        
        sourceBuilder.aggregation(
            AggregationBuilders.terms("query_performance")
                .field("query.keyword")
                .size(100)
                .subAggregation(
                    AggregationBuilders.avg("avg_ctr")
                        .field("click_through_rate")
                )
                .subAggregation(
                    AggregationBuilders.avg("avg_duration")
                        .field("search_duration")
                )
        );
        
        request.source(sourceBuilder);
        
        return extractLowPerformingQueries(executeSearch(request));
    }
}
```

### 24. How do you implement secure search and data encryption?

**Answer:**
Security in Elasticsearch involves authentication, authorization, encryption, and audit logging.

**Security Configuration:**
```java
// 1. Enable TLS/SSL for Node Communication
// elasticsearch.yml
xpack.security.enabled: true
xpack.security.transport.ssl.enabled: true
xpack.security.transport.ssl.verification_mode: certificate
xpack.security.transport.ssl.keystore.path: certs/elastic-certificates.p12
xpack.security.transport.ssl.truststore.path: certs/elastic-certificates.p12

// 2. Configure HTTPS for HTTP Layer
xpack.security.http.ssl.enabled: true
xpack.security.http.ssl.keystore.path: certs/elastic-certificates.p12
xpack.security.http.ssl.truststore.path: certs/elastic-certificates.p12

// 3. Field-Level Encryption
PUT /secure_documents
{
  "settings": {
    "index": {
      "codec": "best_compression"
    }
  },
  "mappings": {
    "properties": {
      "title": { "type": "text" },
      "content": {
        "type": "text",
        "index": false  // Don't index sensitive content
      },
      "encrypted_ssn": {
        "type": "keyword",
        "index": false
      },
      "credit_card": {
        "type": "keyword",
        "index": false
      }
    }
  }
}

// 4. Role-Based Access Control
PUT /_security/role/analyst_role
{
  "indices": [
    {
      "names": ["logs-*", "metrics-*"],
      "privileges": ["read", "view_index_metadata"]
    }
  ],
  "cluster": ["monitor"]
}

PUT /_security/role/admin_role
{
  "indices": [
    {
      "names": ["*"],
      "privileges": ["all"]
    }
  ],
  "cluster": ["all"]
}

// 5. Document-Level Security
PUT /_security/role/tenant_user_role
{
  "indices": [
    {
      "names": ["documents"],
      "privileges": ["read"],
      "query": {
        "term": { "tenant_id": "{{_user.metadata.tenant_id}}" }
      }
    }
  ]
}

// 6. Field-Level Security
PUT /_security/role/limited_access_role
{
  "indices": [
    {
      "names": ["employees"],
      "privileges": ["read"],
      "field_security": {
        "grant": ["name", "department", "position"],
        "except": ["salary", "ssn", "address"]
      }
    }
  ]
}
```

**Encryption Service:**
```java
// 1. Field Encryption Service
@Service
public class FieldEncryptionService {
    
    @Autowired
    private AESUtil aesUtil;
    
    public void indexSecureDocument(SecureDocument document) {
        // Encrypt sensitive fields before indexing
        if (document.getSsn() != null) {
            document.setEncryptedSsn(aesUtil.encrypt(document.getSsn()));
            document.setSsn(null); // Clear original
        }
        
        if (document.getCreditCard() != null) {
            document.setEncryptedCreditCard(aesUtil.encrypt(document.getCreditCard()));
            document.setCreditCard(null); // Clear original
        }
        
        // Index document with encrypted fields
        indexDocument(document);
    }
    
    public SecureDocument retrieveSecureDocument(String id) {
        SecureDocument document = getDocument(id);
        
        // Decrypt sensitive fields
        if (document.getEncryptedSsn() != null) {
            document.setSsn(aesUtil.decrypt(document.getEncryptedSsn()));
        }
        
        if (document.getEncryptedCreditCard() != null) {
            document.setCreditCard(aesUtil.decrypt(document.getEncryptedCreditCard()));
        }
        
        return document;
    }
}

// 2. Audit Logging Service
@Service
public class AuditService {
    
    public void logSearchAttempt(String userId, String query, String index) {
        AuditEvent event = AuditEvent.builder()
            .userId(userId)
            .action("SEARCH")
            .resource(index)
            .query(query)
            .timestamp(LocalDateTime.now())
            .ipAddress(getClientIP())
            .userAgent(getUserAgent())
            .build();
        
        indexAuditEvent(event);
    }
    
    public void logDocumentAccess(String userId, String documentId, String action) {
        AuditEvent event = AuditEvent.builder()
            .userId(userId)
            .action(action)
            .resource("document:" + documentId)
            .timestamp(LocalDateTime.now())
            .ipAddress(getClientIP())
            .build();
        
        indexAuditEvent(event);
    }
    
    @EventListener
    public void handleSecurityEvent(SecurityEvent event) {
        AuditEvent auditEvent = AuditEvent.builder()
            .userId(event.getUserId())
            .action(event.getEventType())
            .resource(event.getResource())
            .details(event.getDetails())
            .timestamp(LocalDateTime.now())
            .build();
        
        indexAuditEvent(auditEvent);
        
        // Send alerts for suspicious activities
        if (event.isSuspicious()) {
            securityAlertService.sendAlert(event);
        }
    }
}
```

### 25. How do you design a multi-region Elasticsearch architecture for global applications?

**Answer:**
Multi-region architecture requires careful planning for data consistency, latency, and disaster recovery.

**Multi-Region Architecture Patterns:**

**1. Active-Active with Cross-Cluster Replication:**
```java
// Architecture Overview
/*
US Region (Primary)          EU Region (Secondary)          APAC Region (Secondary)
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  ES Cluster US  │◄──────►│  ES Cluster EU  │◄──────►│  ES Cluster APAC│
│  (Write + Read)│         │  (Read Only)   │         │  (Read Only)   │
└─────────────────┘         └─────────────────┘         └─────────────────┘
         │                           │                           │
         └───────────────────────────┴───────────────────────────┘
                                   │
                            ┌─────────────────┐
                            │  Global Load    │
                            │   Balancer      │
                            └─────────────────┘
*/

// 1. Configure Cross-Cluster Replication
PUT /_cluster/settings
{
  "cluster.remote.europe.seeds": [
    "es-eu-1:9300",
    "es-eu-2:9300"
  ],
  "cluster.remote.asia.seeds": [
    "es-asia-1:9300",
    "es-asia-2:9300"
  ]
}

// 2. Set up replication from US to EU
PUT /products_europe/_ccr/follow
{
  "remote_cluster": "us_cluster",
  "leader_index": "products_us",
  "settings": {
    "index.number_of_replicas": 0
  }
}

// 3. Set up replication from US to APAC
PUT /products_apac/_ccr/follow
{
  "remote_cluster": "us_cluster",
  "leader_index": "products_us",
  "settings": {
    "index.number_of_replicas": 0
  }
}
```

**2. Geo-Distributed Search Service:**
```java
@Service
public class GlobalSearchService {
    
    @Autowired
    private SearchService usSearchService;
    
    @Autowired
    private SearchService euSearchService;
    
    @Autowired
    private SearchService apacSearchService;
    
    public SearchResult globalSearch(SearchQuery query) {
        String userRegion = getUserRegion();
        
        // Route to nearest region for reads
        switch (userRegion) {
            case "US":
                return usSearchService.search(query);
            case "EU":
                return euSearchService.search(query);
            case "APAC":
                return apacSearchService.search(query);
            default:
                return searchWithFallback(query);
        }
    }
    
    private SearchResult searchWithFallback(SearchQuery query) {
        // Try all regions in parallel, return fastest response
        List<CompletableFuture<SearchResult>> futures = Arrays.asList(
            CompletableFuture.supplyAsync(() -> usSearchService.search(query)),
            CompletableFuture.supplyAsync(() -> euSearchService.search(query)),
            CompletableFuture.supplyAsync(() -> apacSearchService.search(query))
        );
        
        try {
            return CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
                .get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Fallback to US region
            return usSearchService.search(query);
        }
    }
    
    public void writeDocument(Document document) {
        // Always write to primary region (US)
        usSearchService.index(document);
        
        // Replication handled by CCR automatically
    }
}
```

**3. Disaster Recovery and Failover:**
```java
// 1. Region Health Monitoring
@Service
public class RegionHealthMonitor {
    
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorRegionHealth() {
        Map<String, Boolean> regionHealth = new HashMap<>();
        
        regionHealth.put("US", checkRegionHealth("us_cluster"));
        regionHealth.put("EU", checkRegionHealth("europe"));
        regionHealth.put("APAC", checkRegionHealth("asia"));
        
        updateRoutingTable(regionHealth);
        
        // Alert if primary region is down
        if (!regionHealth.get("US")) {
            alertService.sendCriticalAlert("Primary US region is down!");
            initiateFailover();
        }
    }
    
    private boolean checkRegionHealth(String clusterName) {
        try {
            ClusterHealthResponse health = elasticsearchClient(clusterName)
                .cluster()
                .health(RequestOptions.DEFAULT);
            
            return health.getStatus() == ClusterHealthStatus.GREEN ||
                   health.getStatus() == ClusterHealthStatus.YELLOW;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void initiateFailover() {
        // Promote EU region to primary
        promoteRegionToPrimary("EU");
        
        // Update DNS to point to EU region
        updateGlobalDNS("EU");
        
        // Notify all services about failover
        publishFailoverEvent("EU");
    }
}

// 2. Data Consistency Management
@Service
public class ConsistencyManager {
    
    public void ensureEventualConsistency() {
        // Check replication lag
        Map<String, Long> replicationLags = checkReplicationLag();
        
        replicationLags.forEach((region, lag) -> {
            if (lag > 300000) { // 5 minutes
                log.warn("High replication lag detected for region {}: {}ms", region, lag);
                
                // Trigger manual sync if needed
                if (lag > 1800000) { // 30 minutes
                    triggerManualSync(region);
                }
            }
        });
    }
    
    private Map<String, Long> checkReplicationLag() {
        Map<String, Long> lags = new HashMap<>();
        
        // Check CCR stats for each follower index
        lags.put("EU", getReplicationLag("products_europe"));
        lags.put("APAC", getReplicationLag("products_apac"));
        
        return lags;
    }
    
    private void triggerManualSync(String region) {
        try {
            // Pause and resume replication to force sync
            PauseFollowRequest pauseRequest = new PauseFollowRequest("products_" + region.toLowerCase());
            elasticsearchClient.ccr().pauseFollow(pauseRequest, RequestOptions.DEFAULT);
            
            Thread.sleep(5000); // Wait 5 seconds
            
            ResumeFollowRequest resumeRequest = new ResumeFollowRequest("products_" + region.toLowerCase());
            elasticsearchClient.ccr().resumeFollow(resumeRequest, RequestOptions.DEFAULT);
            
            log.info("Manual sync triggered for region: {}", region);
        } catch (Exception e) {
            log.error("Failed to trigger manual sync for region: {}", region, e);
        }
    }
}
```

**4. Performance Optimization for Global Architecture:**
```java
// 1. Smart Routing Based on Query Type
@Service
public class IntelligentRoutingService {
    
    public SearchResult routeQuery(SearchQuery query) {
        // Route analytics queries to nearest region
        if (query.isAnalyticsQuery()) {
            return routeToNearestRegion(query);
        }
        
        // Route real-time queries to primary region
        if (query.isRealTimeQuery()) {
            return routeToPrimaryRegion(query);
        }
        
        // Route historical queries to any region with data
        if (query.isHistoricalQuery()) {
            return routeToOptimalRegion(query);
        }
        
        return routeToNearestRegion(query);
    }
    
    private String selectOptimalRegion(SearchQuery query) {
        Map<String, RegionMetrics> regionMetrics = getRegionMetrics();
        
        return regionMetrics.entrySet().stream()
            .min(Comparator.comparing(entry -> 
                calculateRegionScore(entry.getValue(), query)))
            .map(Map.Entry::getKey)
            .orElse("US");
    }
    
    private double calculateRegionScore(RegionMetrics metrics, SearchQuery query) {
        double latencyScore = metrics.getAverageLatency();
        double loadScore = metrics.getCurrentLoad();
        double dataFreshnessScore = metrics.getDataFreshness();
        
        // Weighted score calculation
        return (latencyScore * 0.4) + (loadScore * 0.3) + (dataFreshnessScore * 0.3);
    }
}

// 2. Cache Coordination Across Regions
@Service
public class GlobalCacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public void cacheSearchResult(String query, SearchResult result) {
        String cacheKey = "search:" + DigestUtils.md5Hex(query);
        
        // Cache in local region
        redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(30));
        
        // Invalidate cache in other regions
        invalidateCacheInOtherRegions(cacheKey);
    }
    
    private void invalidateCacheInOtherRegions(String cacheKey) {
        // Publish cache invalidation event to global message bus
        CacheInvalidationEvent event = new CacheInvalidationEvent(
            cacheKey, getCurrentRegion());
        
        messageBus.publish("cache.invalidation", event);
    }
    
    @EventListener
    public void handleCacheInvalidation(CacheInvalidationEvent event) {
        if (!event.getSourceRegion().equals(getCurrentRegion())) {
            redisTemplate.delete(event.getCacheKey());
        }
    }
}
```

**Best Practices for Multi-Region Architecture:**

1. **Data Consistency**: Use cross-cluster replication for eventual consistency
2. **Latency Optimization**: Route reads to nearest region
3. **Disaster Recovery**: Implement automatic failover mechanisms
4. **Performance Monitoring**: Monitor replication lag and region health
5. **Security**: Encrypt data in transit and at rest
6. **Cost Optimization**: Use appropriate instance types for each region
7. **Testing**: Regular failover drills and consistency checks

This architecture ensures high availability, low latency, and data consistency for global applications while maintaining disaster recovery capabilities.

---

## Summary

These 25 Elasticsearch interview questions cover:

### **Architecture & Fundamentals (1-5)**
- Elasticsearch vs traditional databases
- Inverted index concept
- Shards and replicas
- Data distribution and load balancing
- Term vs match queries

### **Index Design & Best Practices (6-10)**
- Optimal index mappings
- Custom analyzers
- Time-series data handling
- Nested vs join field types
- Synonyms and stop words

### **Performance & Optimization (11-15)**
- Query optimization techniques
- Bulk indexing best practices
- Caching strategies
- High availability and disaster recovery
- Index lifecycle management

### **System Design & Scalability (16-20)**
- Scalable architecture design
- Real-time data streaming
- Multi-tenancy implementation
- Monitoring and maintenance
- Search analytics and A/B testing

### **Real-World Scenarios (21-25)**
- Geospatial search
- Machine learning and anomaly detection
- Search relevance and scoring
- Security and encryption
- Multi-region architecture

Perfect for **Senior Developer, Technical Lead, and Architect** level interviews with focus on practical Elasticsearch implementation and system design decisions.
