# Elasticsearch Interview Questions & Answers

## Table of Contents
1. [Architecture & Fundamentals](#architecture--fundamentals)
2. [Index Design & Best Practices](#index-design--best-practices)
3. [Performance & Optimization](#performance--optimization)
4. [System Design & Scalability](#system-design--scalability)
5. [Real-World Scenarios](#real-world-scenarios)

---

## Architecture & Fundamentals

### 1. What is Elasticsearch and how does it differ from traditional databases?

**Answer:**
Elasticsearch is a distributed, RESTful search and analytics engine built on Apache Lucene. It's designed for full-text search, real-time analytics, and complex querying.

**Key Differences from Traditional Databases:**

| Aspect | Traditional Database | Elasticsearch |
|--------|---------------------|---------------|
| **Primary Use** | Transactional data storage | Search and analytics |
| **Data Structure** | Tables with schemas | Documents with flexible schemas |
| **Query Language** | SQL | Query DSL (JSON) |
| **Indexing** | B-tree indexes | Inverted indexes |
| **Scalability** | Vertical scaling | Horizontal scaling |
| **Consistency** | ACID | Eventual consistency |
| **Search Capabilities** | Basic LIKE operations | Full-text, fuzzy, geospatial |

**Architecture Components:**
```java
// Node Types in Elasticsearch Cluster
public enum NodeType {
    MASTER_NODE,      // Manages cluster state
    DATA_NODE,        // Stores and processes data
    INGEST_NODE,      // Pre-processes documents
    COORDINATING_NODE // Routes requests
}

// Cluster Architecture
/*
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Master Node   │    │   Data Node 1   │    │   Data Node 2   │
│   (Cluster Mgmt)│    │   (Shard Storage)│    │   (Shard Storage)│
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │ Coordinating    │
                    │ Node (Client)   │
                    └─────────────────┘
*/
```

### 2. Explain the concept of inverted index in Elasticsearch

**Answer:**
An inverted index is a data structure that maps content to its location in documents, enabling fast full-text search.

**Traditional Index vs Inverted Index:**

```
Traditional Index (Forward):
Document 1 -> "The quick brown fox"
Document 2 -> "The lazy dog"

Inverted Index:
"the"    -> [Document 1, Document 2]
"quick"   -> [Document 1]
"brown"   -> [Document 1]
"fox"     -> [Document 1]
"lazy"    -> [Document 2]
"dog"     -> [Document 2]
```

**Implementation Example:**
```json
// Document Structure
{
  "title": "Elasticsearch Best Practices",
  "content": "Learn about indexing and searching"
}

// Inverted Index Structure
{
  "elasticsearch": {
    "doc_1": {
      "positions": [0],
      "frequency": 1
    }
  },
  "best": {
    "doc_1": {
      "positions": [1],
      "frequency": 1
    }
  },
  "practices": {
    "doc_1": {
      "positions": [2],
      "frequency": 1
    }
  },
  "learn": {
    "doc_1": {
      "positions": [3],
      "frequency": 1
    }
  }
}
```

**Benefits:**
- Fast full-text search
- Relevance scoring (TF-IDF, BM25)
- Support for complex queries
- Efficient phrase and proximity searches

### 3. What are shards and replicas in Elasticsearch?

**Answer:**
Shards and replicas are fundamental concepts for data distribution and high availability.

**Shards:**
- Horizontal partitioning of data
- Primary and replica shards
- Default: 5 primary shards per index
- Cannot be changed after index creation

**Replicas:**
- Copies of primary shards
- Provide high availability
- Enable read scaling
- Default: 1 replica per primary shard

**Architecture Example:**
```java
// Index with 3 primary shards and 1 replica
PUT /my_index
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1
  }
}

// Shard Distribution across 3 nodes
/*
Node 1: Primary Shard 0, Replica Shard 1
Node 2: Primary Shard 1, Replica Shard 2  
Node 3: Primary Shard 2, Replica Shard 0
*/
```

**Shard Allocation Strategy:**
```java
// Custom shard allocation
PUT /_cluster/settings
{
  "cluster.routing.allocation.awareness.attributes": ["zone"],
  "cluster.routing.allocation.awareness.force.zone.values": ["zone1", "zone2"],
  "cluster.routing.allocation.exclude._name": ["node_hot"]
}
```

### 4. How does Elasticsearch handle data distribution and load balancing?

**Answer:**
Elasticsearch uses a sophisticated routing and allocation system for data distribution.

**Routing Process:**
1. **Document Routing**: `_routing` field determines shard
2. **Hash Calculation**: `hash(routing) % number_of_primary_shards`
3. **Shard Selection**: Primary shard identified
4. **Replica Sync**: Changes propagated to replicas

**Load Balancing Mechanisms:**
```java
// 1. Round-robin query routing
// 2. Adaptive replica selection
// 3. Search timeout and retry logic

// Custom routing for better data locality
PUT /my_index/_doc/1?routing=user123
{
  "user_id": "user123",
  "content": "User specific data"
}

// Shard allocation awareness
PUT /_cluster/settings
{
  "cluster.routing.allocation.awareness.attributes": ["rack_id"]
}
```

**Query Distribution:**
```java
// Query executed in parallel across shards
// Results aggregated at coordinating node
// Scoring and ranking performed centrally

// Search request flow:
/*
Client -> Coordinating Node -> Broadcast to All Shards
         <- Partial Results <- Parallel Execution
         -> Final Results -> Aggregation & Ranking
         -> Client Response
*/
```

### 5. What is the difference between term query and match query?

**Answer:**
Term and match queries serve different purposes in Elasticsearch search.

**Term Query:**
- Exact matching
- No analysis on search term
- Case-sensitive
- Best for structured data (keywords, IDs, enums)

**Match Query:**
- Full-text search
- Analyzes search term
- Case-insensitive (with standard analyzer)
- Best for unstructured text

**Code Examples:**
```java
// Term Query - Exact match
GET /products/_search
{
  "query": {
    "term": {
      "status.keyword": "active"
    }
  }
}

// Match Query - Full-text search
GET /products/_search
{
  "query": {
    "match": {
      "description": "wireless headphones"
    }
  }
}

// Match Phrase Query - Exact phrase
GET /products/_search
{
  "query": {
    "match_phrase": {
      "title": "iPhone 13 Pro"
    }
  }
}
```

**Analysis Comparison:**
```java
// Text: "Apple iPhone 13 Pro"

// Term Query Analysis:
// "Apple iPhone 13 Pro" -> "Apple iPhone 13 Pro" (no analysis)

// Match Query Analysis:
// "Apple iPhone 13 Pro" -> ["apple", "iphone", "13", "pro"] (standard analyzer)
```

---

## Index Design & Best Practices

### 6. How do you design optimal index mappings for different data types?

**Answer:**
Proper index mapping is crucial for performance and search accuracy.

**Mapping Best Practices:**

```java
// 1. Text Fields with Multiple Analyzers
PUT /products
{
  "mappings": {
    "properties": {
      "name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          },
          "suggest": {
            "type": "completion",
            "analyzer": "simple"
          }
        }
      },
      "description": {
        "type": "text",
        "analyzer": "english",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 512
          }
        }
      }
    }
  }
}

// 2. Numeric Fields with Appropriate Types
PUT /metrics
{
  "mappings": {
    "properties": {
      "price": {
        "type": "scaled_float",
        "scaling_factor": 100
      },
      "rating": {
        "type": "half_float"
      },
      "count": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "strict_date_optional_time||epoch_millis"
      }
    }
  }
}

// 3. Nested Objects for Related Data
PUT /orders
{
  "mappings": {
    "properties": {
      "order_id": { "type": "keyword" },
      "customer": {
        "type": "object",
        "properties": {
          "id": { "type": "keyword" },
          "name": { "type": "text" }
        }
      },
      "items": {
        "type": "nested",
        "properties": {
          "product_id": { "type": "keyword" },
          "name": { "type": "text" },
          "quantity": { "type": "integer" },
          "price": { "type": "scaled_float", "scaling_factor": 100 }
        }
      }
    }
  }
}

// 4. Geo Fields for Location Search
PUT /locations
{
  "mappings": {
    "properties": {
      "name": { "type": "text" },
      "location": {
        "type": "geo_point"
      },
      "area": {
        "type": "geo_shape"
      }
    }
  }
}

// 5. Join Field for Parent-Child Relationships
PUT /company
{
  "mappings": {
    "properties": {
      "join": {
        "type": "join",
        "relations": {
          "company": "branch"
        }
      }
    }
  }
}
```

**Field Type Selection Guidelines:**

| Data Type | Elasticsearch Field Type | Use Case |
|-----------|-------------------------|----------|
| Text Content | `text` + `keyword` | Product names, descriptions |
| IDs/Enums | `keyword` | User IDs, status codes |
| Numbers | `integer`, `scaled_float` | Prices, counts, ratings |
| Dates | `date` | Timestamps, ranges |
| Boolean | `boolean` | Flags, switches |
| Binary | `binary` | Encrypted data |
| IP Address | `ip` | Network addresses |
| Geo Location | `geo_point` | Coordinates |

### 7. What are analyzers and how do you configure custom analyzers?

**Answer:**
Analyzers process text before indexing and searching, determining how text is tokenized and normalized.

**Analyzer Components:**
1. **Character Filters**: Pre-process characters
2. **Tokenizer**: Splits text into tokens
3. **Token Filters**: Post-process tokens

**Custom Analyzer Configuration:**
```java
// 1. Custom Analyzer for Product Names
PUT /products
{
  "settings": {
    "analysis": {
      "char_filter": {
        "html_strip": {
          "type": "html_strip"
        },
        "replace_symbols": {
          "type": "mapping",
          "mappings": ["& => and", "@ => at"]
        }
      },
      "tokenizer": {
        "product_tokenizer": {
          "type": "pattern",
          "pattern": "[^\\w]+"
        }
      },
      "filter": {
        "synonym_filter": {
          "type": "synonym",
          "synonyms_path": "synonyms.txt"
        },
        "stopwords_filter": {
          "type": "stop",
          "stopwords": ["a", "an", "the", "and", "or"]
        },
        "stemmer_filter": {
          "type": "stemmer",
          "name": "english"
        }
      },
      "analyzer": {
        "product_analyzer": {
          "type": "custom",
          "char_filter": ["html_strip", "replace_symbols"],
          "tokenizer": "product_tokenizer",
          "filter": [
            "lowercase",
            "synonym_filter",
            "stopwords_filter",
            "stemmer_filter"
          ]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "name": {
        "type": "text",
        "analyzer": "product_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      }
    }
  }
}

// 2. Multi-language Analyzer
PUT /multilang
{
  "settings": {
    "analysis": {
      "analyzer": {
        "french_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": [
            "french_stop",
            "french_stemmer",
            "lowercase"
          ]
        },
        "german_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": [
            "german_stop",
            "german_stemmer",
            "lowercase"
          ]
        }
      }
    }
  }
}

// 3. Analyzer Testing
GET /products/_analyze
{
  "field": "name",
  "text": "iPhone 13 Pro Max - Best Price!"
}

// Output:
/*
{
  "tokens": [
    { "token": "iphone", "position": 1 },
    { "token": "13", "position": 2 },
    { "token": "pro", "position": 3 },
    { "token": "max", "position": 4 },
    { "token": "best", "position": 5 },
    { "token": "price", "position": 6 }
  ]
}
*/
```

**Common Analyzers:**
- **Standard**: General purpose text analysis
- **Keyword**: No analysis, exact matching
- **English**: English-specific stemming and stop words
- **Whitespace**: Splits on whitespace only
- **Simple**: Lowercase and non-letter removal
- **Pattern**: Custom regex tokenization

### 8. How do you handle time-series data in Elasticsearch?

**Answer:**
Time-series data requires special considerations for indexing, retention, and querying.

**Time-Series Index Strategy:**
```java
// 1. Time-based Index Pattern
// Indices: logs-2024.01.15, logs-2024.01.16, etc.

// Index Template for Time-Series
PUT /_index_template/logs_template
{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "refresh_interval": "30s",
      "index.codec": "best_compression"
    },
    "mappings": {
      "properties": {
        "@timestamp": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        },
        "level": {
          "type": "keyword"
        },
        "message": {
          "type": "text",
          "analyzer": "standard"
        },
        "service": {
          "type": "keyword"
        },
        "host": {
          "type": "keyword"
        },
        "metrics": {
          "type": "object",
          "properties": {
            "cpu_usage": {
              "type": "scaled_float",
              "scaling_factor": 100
            },
            "memory_usage": {
              "type": "scaled_float",
              "scaling_factor": 100
            },
            "response_time": {
              "type": "long"
            }
          }
        }
      }
    }
  }
}

// 2. Rollover Policy for Automatic Index Management
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
          }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "allocate": {
            "number_of_replicas": 0
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
      "delete": {
        "min_age": "90d"
      }
    }
  }
}

// 3. Write Alias for Time-Series
PUT /logs-000001
{
  "aliases": {
    "logs-write": {}
  }
}

// 4. Querying Time-Series Data
GET /logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "range": {
            "@timestamp": {
              "gte": "now-24h",
              "lte": "now"
            }
          }
        },
        {
          "term": {
            "level": "ERROR"
          }
        }
      ]
    }
  },
  "aggs": {
    "hourly_errors": {
      "date_histogram": {
        "field": "@timestamp",
        "calendar_interval": "hour"
      },
      "aggs": {
        "services": {
          "terms": {
            "field": "service"
          }
        }
      }
    }
  }
}
```

**Time-Series Best Practices:**
- Use index templates for consistent mappings
- Implement ILM (Index Lifecycle Management)
- Use date-based index patterns
- Optimize shard count based on data volume
- Use rollover for automatic index management
- Configure appropriate refresh intervals
- Use data streams for simplified management

### 9. What is the difference between nested and join field types?

**Answer:**
Nested and join fields handle relationships but serve different purposes.

**Nested Field Type:**
- Stores array of objects as separate documents
- Maintains object boundaries
- Enables querying within array objects
- Limited to parent-child within same document

**Join Field Type:**
- Creates true parent-child relationships
- Documents stored independently
- Supports separate updates
- Can span across documents

**Nested Field Example:**
```java
// Product with Reviews (Nested)
PUT /products/_doc/1
{
  "name": "iPhone 13",
  "reviews": [
    {
      "user": "john",
      "rating": 5,
      "comment": "Excellent phone"
    },
    {
      "user": "jane",
      "rating": 4,
      "comment": "Good but expensive"
    }
  ]
}

// Query Nested Objects
GET /products/_search
{
  "query": {
    "nested": {
      "path": "reviews",
      "query": {
        "bool": {
          "must": [
            { "match": { "reviews.user": "john" } },
            { "match": { "reviews.rating": 5 } }
          ]
        }
      }
    }
  }
}

// Aggregate Nested Data
GET /products/_search
{
  "aggs": {
    "review_stats": {
      "nested": {
        "path": "reviews"
      },
      "aggs": {
        "avg_rating": {
          "avg": {
            "field": "reviews.rating"
          }
        }
      }
    }
  }
}
```

**Join Field Example:**
```java
// Company and Branches (Join)
PUT /company/_doc/1
{
  "name": "Tech Corp",
  "join": {
    "name": "company"
  }
}

PUT /company/_doc/2?routing=1
{
  "name": "NYC Branch",
  "address": "123 Main St, NYC",
  "join": {
    "name": "branch",
    "parent": "1"
  }
}

// Query Parent-Child
GET /company/_search
{
  "query": {
    "has_child": {
      "type": "branch",
      "query": {
        "match": {
          "address": "NYC"
        }
      }
    }
  }
}

// Query Child-Parent
GET /company/_search
{
  "query": {
    "has_parent": {
      "parent_type": "company",
      "query": {
        "match": {
          "name": "Tech Corp"
        }
      }
    }
  }
}
```

**Comparison:**

| Feature | Nested | Join |
|---------|---------|------|
| Document Structure | Single document with array | Separate documents |
| Query Performance | Faster (same shard) | Slower (cross-shard) |
| Update Granularity | Entire document | Individual child documents |
| Memory Usage | Higher (duplicate data) | Lower |
| Use Case | Product reviews, Order items | Company-branches, Blog-comments |

### 10. How do you implement synonyms and stop words in Elasticsearch?

**Answer:**
Synonyms and stop words enhance search relevance and reduce noise.

**Synonym Implementation:**
```java
// 1. Synonym File (synonyms.txt)
/*
iphone,apple phone => iphone
laptop,notebook => laptop
tv,television => tv
*/

// 2. Synonym Filter Configuration
PUT /products
{
  "settings": {
    "analysis": {
      "filter": {
        "synonym_filter": {
          "type": "synonym",
          "synonyms": [
            "iphone,apple phone => iphone",
            "laptop,notebook => laptop",
            "tv,television => tv",
            "sneakers,trainers,running shoes => sneakers"
          ]
        }
      },
      "analyzer": {
        "synonym_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "synonym_filter"
          ]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "name": {
        "type": "text",
        "analyzer": "synonym_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      }
    }
  }
}

// 3. Query Expansion with Synonyms
GET /products/_search
{
  "query": {
    "match": {
      "name": {
        "query": "apple phone",
        "analyzer": "synonym_analyzer"
      }
    }
  }
}

// 4. Multi-way Synonyms
PUT /products
{
  "settings": {
    "analysis": {
      "filter": {
        "multi_synonym": {
          "type": "synonym",
          "synonyms": "mobile phone,cell phone,smartphone"
        }
      }
    }
  }
}
```

**Stop Words Configuration:**
```java
// 1. Custom Stop Words
PUT /articles
{
  "settings": {
    "analysis": {
      "filter": {
        "custom_stop": {
          "type": "stop",
          "stopwords": ["a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for"]
        }
      },
      "analyzer": {
        "custom_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "custom_stop"
          ]
        }
      }
    }
  }
}

// 2. Stop Words from File
PUT /articles
{
  "settings": {
    "analysis": {
      "filter": {
        "file_stop": {
          "type": "stop",
          "stopwords_path": "custom_stopwords.txt"
        }
      }
    }
  }
}

// 3. Language-specific Stop Words
PUT /articles
{
  "settings": {
    "analysis": {
      "analyzer": {
        "english_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": [
            "lowercase",
            "english_stop",
            "english_stemmer"
          ]
        }
      }
    }
  }
}

// 4. Testing Stop Words
GET /articles/_analyze
{
  "analyzer": "custom_analyzer",
  "text": "The quick brown fox jumps over the lazy dog"
}

// Output: ["quick", "brown", "fox", "jumps", "over", "lazy", "dog"]
```

**Best Practices:**
- Use domain-specific synonyms
- Consider multi-word synonyms
- Balance stop words (too many vs too few)
- Test synonym expansion carefully
- Use different analyzers for different fields
- Monitor synonym performance impact

---

*Continued with remaining questions 11-25...*
