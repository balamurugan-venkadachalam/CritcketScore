# Elasticsearch Architecture for Cricket Dashboard Search

## Overview

This document outlines the architecture for integrating Elasticsearch into a cricket dashboard to enable powerful search capabilities for match details, player statistics, and historical data.

## Architecture Diagram

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend      │    │   Backend API   │    │   Database      │
│   Dashboard     │◄──►│   (Spring Boot) │◄──►│   PostgreSQL    │
│   (React/Vue)   │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │  Elasticsearch  │
                       │     Cluster     │
                       │                 │
                       │ ┌─────────────┐ │
                       │ │ Match Index │ │
                       │ └─────────────┘ │
                       │ ┌─────────────┐ │
                       │ │ Player Index│ │
                       │ └─────────────┘ │
                       │ ┌─────────────┐ │
                       │ │ Team Index  │ │
                       │ └─────────────┘ │
                       └─────────────────┘
```

## 1. Data Model & Index Design

### Match Index
```json
{
  "mappings": {
    "properties": {
      "matchId": { "type": "keyword" },
      "title": { 
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "description": { 
        "type": "text",
        "analyzer": "english"
      },
      "teams": {
        "type": "nested",
        "properties": {
          "teamId": { "type": "keyword" },
          "teamName": { 
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword" }
            }
          },
          "score": { "type": "integer" },
          "wickets": { "type": "integer" },
          "overs": { "type": "float" }
        }
      },
      "venue": {
        "type": "object",
        "properties": {
          "name": { 
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword" }
            }
          },
          "city": { 
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword" }
            }
          },
          "country": { "type": "keyword" }
        }
      },
      "matchType": { "type": "keyword" },
      "format": { "type": "keyword" },
      "status": { "type": "keyword" },
      "startDate": { "type": "date" },
      "endDate": { "type": "date" },
      "players": {
        "type": "nested",
        "properties": {
          "playerId": { "type": "keyword" },
          "playerName": { 
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword" }
            }
          },
          "teamId": { "type": "keyword" },
          "role": { "type": "keyword" },
          "runs": { "type": "integer" },
          "wickets": { "type": "integer" },
          "catches": { "type": "integer" }
        }
      },
      "highlights": {
        "type": "text",
        "analyzer": "english"
      },
      "tags": { "type": "keyword" },
      "tournament": {
        "type": "object",
        "properties": {
          "id": { "type": "keyword" },
          "name": { 
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword" }
            }
          },
          "year": { "type": "integer" }
        }
      }
    }
  }
}
```

### Player Index
```json
{
  "mappings": {
    "properties": {
      "playerId": { "type": "keyword" },
      "name": { 
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword" },
          "suggest": { 
            "type": "completion",
            "analyzer": "simple"
          }
        }
      },
      "fullName": { 
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "dateOfBirth": { "type": "date" },
      "nationality": { "type": "keyword" },
      "role": { "type": "keyword" },
      "battingStyle": { "type": "keyword" },
      "bowlingStyle": { "type": "keyword" },
      "teams": { "type": "keyword" },
      "careerStats": {
        "type": "object",
        "properties": {
          "totalMatches": { "type": "integer" },
          "totalRuns": { "type": "integer" },
          "average": { "type": "float" },
          "strikeRate": { "type": "float" },
          "totalWickets": { "type": "integer" },
          "economy": { "type": "float" }
        }
      },
      "recentForm": {
        "type": "nested",
        "properties": {
          "matchId": { "type": "keyword" },
          "runs": { "type": "integer" },
          "wickets": { "type": "integer" },
          "date": { "type": "date" }
        }
      }
    }
  }
}
```

## 2. Spring Boot Integration

### Configuration
```java
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.cricket.repository")
public class ElasticsearchConfig {
    
    @Bean
    public ElasticsearchOperations elasticsearchTemplate(ElasticsearchClient client) {
        return new ElasticsearchRestTemplate(client);
    }
    
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return ElasticsearchClients.createSimple(
            HttpHost.create("localhost:9200")
        );
    }
}
```

### Document Models
```java
@Document(indexName = "matches")
public class MatchDocument {
    
    @Id
    private String matchId;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;
    
    @Field(type = FieldType.Text, analyzer = "english")
    private String description;
    
    @Field(type = FieldType.Nested)
    private List<TeamPerformance> teams;
    
    @Field(type = FieldType.Object)
    private Venue venue;
    
    @Field(type = FieldType.Keyword)
    private String matchType;
    
    @Field(type = FieldType.Keyword)
    private String format;
    
    @Field(type = FieldType.Keyword)
    private String status;
    
    @Field(type = FieldType.Date)
    private LocalDateTime startDate;
    
    @Field(type = FieldType.Date)
    private LocalDateTime endDate;
    
    @Field(type = FieldType.Nested)
    private List<PlayerPerformance> players;
    
    @Field(type = FieldType.Text, analyzer = "english")
    private String highlights;
    
    @Field(type = FieldType.Keyword)
    private List<String> tags;
    
    @Field(type = FieldType.Object)
    private Tournament tournament;
    
    // Getters and setters
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamPerformance {
    @Field(type = FieldType.Keyword)
    private String teamId;
    
    @Field(type = FieldType.Text)
    private String teamName;
    
    @Field(type = FieldType.Integer)
    private Integer score;
    
    @Field(type = FieldType.Integer)
    private Integer wickets;
    
    @Field(type = FieldType.Float)
    private Float overs;
}

@Data
public class PlayerPerformance {
    @Field(type = FieldType.Keyword)
    private String playerId;
    
    @Field(type = FieldType.Text)
    private String playerName;
    
    @Field(type = FieldType.Keyword)
    private String teamId;
    
    @Field(type = FieldType.Keyword)
    private String role;
    
    @Field(type = FieldType.Integer)
    private Integer runs;
    
    @Field(type = FieldType.Integer)
    private Integer wickets;
    
    @Field(type = FieldType.Integer)
    private Integer catches;
}
```

### Repository Layer
```java
@Repository
public interface MatchSearchRepository extends ElasticsearchRepository<MatchDocument, String> {
    
    // Custom search methods
    Page<MatchDocument> findByTitleContaining(String title, Pageable pageable);
    
    Page<MatchDocument> findByTeamsTeamNameContaining(String teamName, Pageable pageable);
    
    Page<MatchDocument> findByVenueCity(String city, Pageable pageable);
    
    // Date range queries
    Page<MatchDocument> findByStartDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    // Complex queries using @Query
    @Query("""
        {
          "bool": {
            "must": [
              {"match": {"title": "?0"}},
              {"term": {"status": "?1"}}
            ]
          }
        }
        """)
    Page<MatchDocument> findByTitleAndStatus(String title, String status, Pageable pageable);
}
```

### Search Service
```java
@Service
public class MatchSearchService {
    
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    
    @Autowired
    private MatchSearchRepository matchSearchRepository;
    
    public Page<MatchDocument> searchMatches(MatchSearchRequest request) {
        
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        
        // Text search across multiple fields
        if (StringUtils.hasText(request.getQuery())) {
            queryBuilder.must(QueryBuilders.multiMatchQuery(request.getQuery())
                .field("title", 2.0f) // Boost title field
                .field("description")
                .field("teams.teamName")
                .field("venue.name")
                .field("highlights")
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO));
        }
        
        // Team filters
        if (request.getTeams() != null && !request.getTeams().isEmpty()) {
            queryBuilder.filter(QueryBuilders.termsQuery("teams.teamId", request.getTeams()));
        }
        
        // Venue filters
        if (StringUtils.hasText(request.getVenue())) {
            queryBuilder.filter(QueryBuilders.matchQuery("venue.name", request.getVenue()));
        }
        
        if (StringUtils.hasText(request.getCity())) {
            queryBuilder.filter(QueryBuilders.termQuery("venue.city", request.getCity()));
        }
        
        // Date range filter
        if (request.getStartDate() != null || request.getEndDate() != null) {
            RangeQueryBuilder dateRange = QueryBuilders.rangeQuery("startDate");
            if (request.getStartDate() != null) {
                dateRange.gte(request.getStartDate());
            }
            if (request.getEndDate() != null) {
                dateRange.lte(request.getEndDate());
            }
            queryBuilder.filter(dateRange);
        }
        
        // Match type and format
        if (StringUtils.hasText(request.getMatchType())) {
            queryBuilder.filter(QueryBuilders.termQuery("matchType", request.getMatchType()));
        }
        
        if (StringUtils.hasText(request.getFormat())) {
            queryBuilder.filter(QueryBuilders.termQuery("format", request.getFormat()));
        }
        
        // Status filter
        if (StringUtils.hasText(request.getStatus())) {
            queryBuilder.filter(QueryBuilders.termQuery("status", request.getStatus()));
        }
        
        // Tournament filter
        if (request.getTournamentId() != null) {
            queryBuilder.filter(QueryBuilders.termQuery("tournament.id", request.getTournamentId()));
        }
        
        // Build search query
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(request.getPageable())
            .withHighlightFields(
                new HighlightBuilder.Field("title"),
                new HighlightBuilder.Field("description"),
                new HighlightBuilder.Field("highlights")
            )
            .build();
        
        SearchHits<MatchDocument> searchHits = 
            elasticsearchOperations.search(searchQuery, MatchDocument.class);
        
        return new PageImpl<>(
            searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList()),
            request.getPageable(),
            searchHits.getTotalHits()
        );
    }
    
    public List<String> getSuggestions(String query) {
        CompletionSuggestionBuilder suggestionBuilder = 
            SuggestBuilders.completionSuggestion("name.suggest")
                .prefix(query)
                .size(10);
        
        SuggestBuilder suggestBuilder = new SuggestBuilder()
            .addSuggestion("player_suggestions", suggestionBuilder);
        
        // Execute suggestion query on player index
        // Return list of suggested player names
        return Collections.emptyList(); // Simplified
    }
    
    public List<AggregationResult> getAggregations(MatchSearchRequest request) {
        BoolQueryBuilder queryBuilder = buildBaseQuery(request);
        
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .addAggregation(AggregationBuilders.terms("teams").field("teams.teamId"))
            .addAggregation(AggregationBuilders.terms("venues").field("venue.name.keyword"))
            .addAggregation(AggregationBuilders.terms("formats").field("format"))
            .addAggregation(AggregationBuilders.terms("matchTypes").field("matchType"))
            .addAggregation(AggregationBuilders.dateHistogram("dates")
                .field("startDate")
                .calendarInterval(CalendarInterval.MONTH))
            .build();
        
        SearchHits<MatchDocument> searchHits = 
            elasticsearchOperations.search(searchQuery, MatchDocument.class);
        
        return extractAggregations(searchHits.getAggregations());
    }
}
```

## 3. API Layer

### Search Controller
```java
@RestController
@RequestMapping("/api/search")
public class SearchController {
    
    @Autowired
    private MatchSearchService matchSearchService;
    
    @PostMapping("/matches")
    public ResponseEntity<SearchResponse<MatchDocument>> searchMatches(
            @RequestBody MatchSearchRequest request) {
        
        Page<MatchDocument> results = matchSearchService.searchMatches(request);
        
        SearchResponse<MatchDocument> response = SearchResponse.<MatchDocument>builder()
            .content(results.getContent())
            .page(results.getNumber())
            .size(results.getSize())
            .totalElements(results.getTotalElements())
            .totalPages(results.getTotalPages())
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<String> suggestions = matchSearchService.getSuggestions(query);
        return ResponseEntity.ok(suggestions);
    }
    
    @PostMapping("/aggregations")
    public ResponseEntity<List<AggregationResult>> getAggregations(
            @RequestBody MatchSearchRequest request) {
        
        List<AggregationResult> aggregations = matchSearchService.getAggregations(request);
        return ResponseEntity.ok(aggregations);
    }
}

@Data
public class MatchSearchRequest {
    private String query;
    private List<String> teams;
    private String venue;
    private String city;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String matchType;
    private String format;
    private String status;
    private String tournamentId;
    private int page = 0;
    private int size = 20;
    private String sortBy = "startDate";
    private String sortOrder = "desc";
    
    public Pageable getPageable() {
        Sort.Direction direction = Sort.Direction.fromString(sortOrder);
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}
```

## 4. Data Synchronization

### Event-Driven Sync
```java
@Service
public class MatchIndexingService {
    
    @Autowired
    private MatchSearchRepository matchSearchRepository;
    
    @Autowired
    private MatchRepository matchRepository;
    
    @EventListener
    @Async
    public void handleMatchCreated(MatchCreatedEvent event) {
        Match match = matchRepository.findById(event.getMatchId()).orElseThrow();
        MatchDocument document = convertToDocument(match);
        matchSearchRepository.save(document);
    }
    
    @EventListener
    @Async
    public void handleMatchUpdated(MatchUpdatedEvent event) {
        Match match = matchRepository.findById(event.getMatchId()).orElseThrow();
        MatchDocument document = convertToDocument(match);
        matchSearchRepository.save(document);
    }
    
    @EventListener
    @Async
    public void handleMatchDeleted(MatchDeletedEvent event) {
        matchSearchRepository.deleteById(event.getMatchId());
    }
    
    private MatchDocument convertToDocument(Match match) {
        MatchDocument document = new MatchDocument();
        document.setMatchId(match.getId());
        document.setTitle(match.getTitle());
        document.setDescription(match.getDescription());
        document.setTeams(convertTeams(match.getTeams()));
        document.setVenue(convertVenue(match.getVenue()));
        document.setMatchType(match.getMatchType());
        document.setFormat(match.getFormat());
        document.setStatus(match.getStatus());
        document.setStartDate(match.getStartDate());
        document.setEndDate(match.getEndDate());
        document.setPlayers(convertPlayers(match.getPlayers()));
        document.setHighlights(match.getHighlights());
        document.setTags(match.getTags());
        document.setTournament(convertTournament(match.getTournament()));
        return document;
    }
}
```

### Bulk Indexing
```java
@Service
public class BulkIndexingService {
    
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    
    public void bulkIndexMatches(List<Match> matches) {
        List<IndexQuery> queries = matches.stream()
            .map(match -> {
                MatchDocument document = convertToDocument(match);
                IndexQuery query = new IndexQueryBuilder()
                    .withId(match.getId())
                    .withObject(document)
                    .build();
                return query;
            })
            .collect(Collectors.toList());
        
        elasticsearchOperations.bulkIndex(queries, MatchDocument.class);
    }
    
    public void reindexAllMatches() {
        // Fetch all matches from database
        List<Match> matches = matchRepository.findAll();
        
        // Delete existing index
        elasticsearchOperations.indexOps(MatchDocument.class).delete();
        
        // Create index with mapping
        elasticsearchOperations.indexOps(MatchDocument.class).create();
        
        // Bulk index all matches
        bulkIndexMatches(matches);
    }
}
```

## 5. Frontend Integration

### Search Component (React)
```javascript
import React, { useState, useEffect } from 'react';
import { debounce } from 'lodash';

const MatchSearch = () => {
    const [searchQuery, setSearchQuery] = useState('');
    const [filters, setFilters] = useState({});
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [suggestions, setSuggestions] = useState([]);
    const [aggregations, setAggregations] = useState({});

    const debouncedSearch = debounce(async (query, filters) => {
        setLoading(true);
        try {
            const response = await fetch('/api/search/matches', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    query,
                    ...filters,
                    page: 0,
                    size: 20
                })
            });
            const data = await response.json();
            setResults(data.content);
        } catch (error) {
            console.error('Search error:', error);
        } finally {
            setLoading(false);
        }
    }, 300);

    const handleSearchChange = (value) => {
        setSearchQuery(value);
        debouncedSearch(value, filters);
    };

    const handleFilterChange = (newFilters) => {
        setFilters(newFilters);
        debouncedSearch(searchQuery, newFilters);
    };

    const loadSuggestions = async (query) => {
        if (query.length < 2) return;
        
        try {
            const response = await fetch(`/api/search/suggestions?query=${query}`);
            const data = await response.json();
            setSuggestions(data);
        } catch (error) {
            console.error('Suggestions error:', error);
        }
    };

    return (
        <div className="match-search">
            <div className="search-header">
                <input
                    type="text"
                    placeholder="Search matches, players, teams..."
                    value={searchQuery}
                    onChange={(e) => handleSearchChange(e.target.value)}
                    className="search-input"
                />
                
                {suggestions.length > 0 && (
                    <div className="suggestions">
                        {suggestions.map((suggestion, index) => (
                            <div key={index} className="suggestion-item">
                                {suggestion}
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <div className="filters">
                <FilterPanel 
                    filters={filters}
                    onFilterChange={handleFilterChange}
                    aggregations={aggregations}
                />
            </div>

            <div className="results">
                {loading ? (
                    <div className="loading">Searching...</div>
                ) : (
                    <MatchList matches={results} />
                )}
            </div>
        </div>
    );
};
```

## 6. Performance Optimization

### Index Settings
```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "refresh_interval": "30s",
    "index": {
      "max_result_window": 50000,
      "analysis": {
        "analyzer": {
          "cricket_analyzer": {
            "type": "custom",
            "tokenizer": "standard",
            "filter": [
              "lowercase",
              "stop",
              "snowball"
            ]
          }
        }
      }
    }
  }
}
```

### Caching Strategy
```java
@Service
public class SearchCacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Cacheable(value = "search-results", key = "#request.hashCode()")
    public Page<MatchDocument> searchMatches(MatchSearchRequest request) {
        return matchSearchService.searchMatches(request);
    }
    
    @Cacheable(value = "aggregations", key = "#request.hashCode()")
    public List<AggregationResult> getAggregations(MatchSearchRequest request) {
        return matchSearchService.getAggregations(request);
    }
}
```

## 7. Monitoring & Analytics

### Search Metrics
```java
@Component
public class SearchMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter searchCounter;
    private final Timer searchTimer;
    
    public SearchMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.searchCounter = Counter.builder("search.requests")
            .description("Number of search requests")
            .register(meterRegistry);
        this.searchTimer = Timer.builder("search.duration")
            .description("Search request duration")
            .register(meterRegistry);
    }
    
    public void recordSearch(String query, long duration) {
        searchCounter.increment(Tags.of("query_type", query.isEmpty() ? "browse" : "search"));
        searchTimer.record(duration, TimeUnit.MILLISECONDS);
    }
}
```

## 8. Deployment Considerations

### Docker Compose
```yaml
version: '3.8'
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
      - xpack.security.enabled=false
    ports:
      - "9200:9200"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    networks:
      - cricket-network

  cricket-app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATA_ELASTICSEARCH_URIS=http://elasticsearch:9200
    depends_on:
      - elasticsearch
    networks:
      - cricket-network

volumes:
  es_data:

networks:
  cricket-network:
    driver: bridge
```

## 9. Search Features

### Advanced Search Capabilities
- **Full-text search** across match titles, descriptions, highlights
- **Faceted search** with team, venue, format filters
- **Auto-complete** for player and team names
- **Date range filtering** for match periods
- **Geographic search** by venue/city
- **Tournament-based filtering**
- **Real-time updates** as matches progress
- **Highlighting** of search terms in results
- **Sorting** by relevance, date, score
- **Pagination** for large result sets

### Search Analytics
- **Popular search terms**
- **Search result click-through rates**
- **Filter usage patterns**
- **Performance metrics**
- **User behavior tracking**

This architecture provides a robust, scalable search solution for your cricket dashboard, enabling users to quickly find matches, players, and relevant information with powerful search capabilities.
