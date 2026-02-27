# API Design & OpenAPI Interview Questions & Answers - Part 3 (Questions 13-25)

## Best Practices & Real-World Scenarios

### 13. How do you design APIs for file uploads and downloads?

**Answer:**

```java
// File Upload Controller
@RestController
@RequestMapping("/api/files")
public class FileUploadController {
    
    @Autowired
    private FileStorageService fileStorageService;
    
    // Single file upload
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description) {
        
        // Validate file
        validateFile(file);
        
        // Store file
        String fileId = fileStorageService.store(file, description);
        
        FileUploadResponse response = FileUploadResponse.builder()
            .fileId(fileId)
            .fileName(file.getOriginalFilename())
            .fileSize(file.getSize())
            .contentType(file.getContentType())
            .downloadUrl("/api/files/download/" + fileId)
            .build();
        
        return ResponseEntity.created(URI.create("/api/files/" + fileId))
            .body(response);
    }
    
    // Multiple file upload
    @PostMapping("/upload/multiple")
    public ResponseEntity<List<FileUploadResponse>> uploadMultipleFiles(
            @RequestParam("files") MultipartFile[] files) {
        
        List<FileUploadResponse> responses = Arrays.stream(files)
            .map(file -> {
                validateFile(file);
                String fileId = fileStorageService.store(file, null);
                return FileUploadResponse.builder()
                    .fileId(fileId)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .downloadUrl("/api/files/download/" + fileId)
                    .build();
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    // Chunked upload for large files
    @PostMapping("/upload/chunked")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks) {
        
        fileStorageService.storeChunk(uploadId, chunk, chunkNumber);
        
        if (chunkNumber == totalChunks - 1) {
            // Last chunk - finalize upload
            String fileId = fileStorageService.finalizeUpload(uploadId);
            return ResponseEntity.ok(ChunkUploadResponse.completed(fileId));
        }
        
        return ResponseEntity.ok(ChunkUploadResponse.inProgress(chunkNumber + 1));
    }
    
    // File download
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        FileMetadata metadata = fileStorageService.getMetadata(fileId);
        Resource resource = fileStorageService.loadAsResource(fileId);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + metadata.getFileName() + "\"")
            .contentType(MediaType.parseMediaType(metadata.getContentType()))
            .contentLength(metadata.getFileSize())
            .body(resource);
    }
    
    // Streaming download for large files
    @GetMapping("/stream/{fileId}")
    public ResponseEntity<StreamingResponseBody> streamFile(@PathVariable String fileId) {
        FileMetadata metadata = fileStorageService.getMetadata(fileId);
        
        StreamingResponseBody stream = outputStream -> {
            try (InputStream inputStream = fileStorageService.getInputStream(fileId)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        };
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + metadata.getFileName() + "\"")
            .contentType(MediaType.parseMediaType(metadata.getContentType()))
            .body(stream);
    }
    
    // Range request support (for resumable downloads)
    @GetMapping("/download/range/{fileId}")
    public ResponseEntity<Resource> downloadFileWithRange(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String range) {
        
        FileMetadata metadata = fileStorageService.getMetadata(fileId);
        Resource resource = fileStorageService.loadAsResource(fileId);
        
        if (range == null) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + metadata.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(metadata.getContentType()))
                .body(resource);
        }
        
        // Parse range header: bytes=0-1023
        long[] rangeValues = parseRange(range, metadata.getFileSize());
        long start = rangeValues[0];
        long end = rangeValues[1];
        long contentLength = end - start + 1;
        
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
            .header(HttpHeaders.CONTENT_RANGE, 
                String.format("bytes %d-%d/%d", start, end, metadata.getFileSize()))
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .contentLength(contentLength)
            .contentType(MediaType.parseMediaType(metadata.getContentType()))
            .body(resource);
    }
    
    // Presigned URL for direct upload to S3
    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> generatePresignedUrl(
            @RequestBody PresignedUrlRequest request) {
        
        String presignedUrl = fileStorageService.generatePresignedUploadUrl(
            request.getFileName(),
            request.getContentType(),
            Duration.ofMinutes(15)
        );
        
        PresignedUrlResponse response = PresignedUrlResponse.builder()
            .uploadUrl(presignedUrl)
            .expiresIn(900) // 15 minutes
            .method("PUT")
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File is empty");
        }
        
        // Check file size (10MB limit)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new FileSizeExceededException("File size exceeds 10MB limit");
        }
        
        // Check file type
        String contentType = file.getContentType();
        List<String> allowedTypes = Arrays.asList(
            "image/jpeg", "image/png", "image/gif",
            "application/pdf", "application/msword"
        );
        
        if (!allowedTypes.contains(contentType)) {
            throw new InvalidFileTypeException("File type not allowed: " + contentType);
        }
    }
}

// File Storage Service
@Service
public class FileStorageService {
    
    @Value("${file.upload.dir}")
    private String uploadDir;
    
    @Autowired
    private AmazonS3 s3Client;
    
    public String store(MultipartFile file, String description) {
        String fileId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        
        try {
            // Store in S3
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());
            metadata.addUserMetadata("original-filename", fileName);
            metadata.addUserMetadata("description", description);
            
            s3Client.putObject(
                "my-bucket",
                fileId,
                file.getInputStream(),
                metadata
            );
            
            // Store metadata in database
            FileMetadata fileMetadata = new FileMetadata();
            fileMetadata.setFileId(fileId);
            fileMetadata.setFileName(fileName);
            fileMetadata.setContentType(file.getContentType());
            fileMetadata.setFileSize(file.getSize());
            fileMetadata.setDescription(description);
            fileMetadata.setUploadedAt(LocalDateTime.now());
            
            fileMetadataRepository.save(fileMetadata);
            
            return fileId;
        } catch (IOException e) {
            throw new FileStorageException("Failed to store file", e);
        }
    }
    
    public Resource loadAsResource(String fileId) {
        try {
            S3Object s3Object = s3Client.getObject("my-bucket", fileId);
            return new InputStreamResource(s3Object.getObjectContent());
        } catch (Exception e) {
            throw new FileNotFoundException("File not found: " + fileId);
        }
    }
    
    public String generatePresignedUploadUrl(String fileName, 
                                            String contentType, 
                                            Duration expiration) {
        String fileId = UUID.randomUUID().toString();
        
        Date expirationDate = new Date(System.currentTimeMillis() + expiration.toMillis());
        
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
            "my-bucket", fileId)
            .withMethod(HttpMethod.PUT)
            .withExpiration(expirationDate);
        
        request.addRequestParameter("Content-Type", contentType);
        
        URL presignedUrl = s3Client.generatePresignedUrl(request);
        
        return presignedUrl.toString();
    }
}
```

### 14. How do you implement API caching strategies?

**Answer:**

```java
// HTTP Caching Headers
@RestController
@RequestMapping("/api/products")
public class ProductCachingController {
    
    // Cache-Control with max-age
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable Long id) {
        ProductDTO product = productService.findById(id);
        
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS)
                .cachePublic())
            .body(product);
    }
    
    // ETag for conditional requests
    @GetMapping("/{id}/with-etag")
    public ResponseEntity<ProductDTO> getProductWithETag(
            @PathVariable Long id,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        
        ProductDTO product = productService.findById(id);
        String etag = generateETag(product);
        
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .build();
        }
        
        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
            .body(product);
    }
    
    // Last-Modified for conditional requests
    @GetMapping("/{id}/with-last-modified")
    public ResponseEntity<ProductDTO> getProductWithLastModified(
            @PathVariable Long id,
            @RequestHeader(value = "If-Modified-Since", required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ifModifiedSince) {
        
        ProductDTO product = productService.findById(id);
        LocalDateTime lastModified = product.getUpdatedAt();
        
        if (ifModifiedSince != null && !lastModified.isAfter(ifModifiedSince)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .lastModified(lastModified.toInstant(ZoneOffset.UTC))
                .build();
        }
        
        return ResponseEntity.ok()
            .lastModified(lastModified.toInstant(ZoneOffset.UTC))
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
            .body(product);
    }
    
    // No-cache for sensitive data
    @GetMapping("/user/cart")
    public ResponseEntity<CartDTO> getUserCart() {
        CartDTO cart = cartService.getCurrentUserCart();
        
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache()
                .noStore()
                .mustRevalidate())
            .body(cart);
    }
    
    private String generateETag(ProductDTO product) {
        String data = product.getId() + ":" + product.getUpdatedAt();
        return DigestUtils.md5DigestAsHex(data.getBytes());
    }
}

// Application-Level Caching with Redis
@Service
public class ProductCacheService {
    
    @Autowired
    private RedisTemplate<String, ProductDTO> redisTemplate;
    
    @Autowired
    private ProductRepository productRepository;
    
    private static final String CACHE_KEY_PREFIX = "product:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    
    public ProductDTO findById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        
        // Try cache first
        ProductDTO cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Cache miss - fetch from database
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
        
        ProductDTO dto = convertToDTO(product);
        
        // Store in cache
        redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL);
        
        return dto;
    }
    
    public void invalidate(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        redisTemplate.delete(cacheKey);
    }
    
    public void invalidateAll() {
        Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

// Spring Cache Abstraction
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}

@Service
public class ProductService {
    
    @Cacheable(value = "products", key = "#id")
    public ProductDTO findById(Long id) {
        return productRepository.findById(id)
            .map(this::convertToDTO)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }
    
    @CachePut(value = "products", key = "#result.id")
    public ProductDTO update(Long id, ProductDTO product) {
        Product entity = productRepository.findById(id).orElseThrow();
        updateEntity(entity, product);
        return convertToDTO(productRepository.save(entity));
    }
    
    @CacheEvict(value = "products", key = "#id")
    public void delete(Long id) {
        productRepository.deleteById(id);
    }
    
    @CacheEvict(value = "products", allEntries = true)
    public void deleteAll() {
        productRepository.deleteAll();
    }
    
    @Cacheable(value = "products", key = "#category + ':' + #page + ':' + #size")
    public Page<ProductDTO> findByCategory(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findByCategory(category, pageable)
            .map(this::convertToDTO);
    }
}

// Cache-Aside Pattern
@Service
public class CacheAsideService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private ProductRepository productRepository;
    
    public ProductDTO getProduct(Long id) {
        String key = "product:" + id;
        
        // 1. Try to get from cache
        ProductDTO cached = (ProductDTO) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        
        // 2. Cache miss - get from database
        Product product = productRepository.findById(id).orElseThrow();
        ProductDTO dto = convertToDTO(product);
        
        // 3. Store in cache
        redisTemplate.opsForValue().set(key, dto, Duration.ofHours(1));
        
        return dto;
    }
    
    public ProductDTO updateProduct(Long id, ProductDTO updates) {
        // 1. Update database
        Product product = productRepository.findById(id).orElseThrow();
        updateEntity(product, updates);
        Product saved = productRepository.save(product);
        
        // 2. Update cache
        String key = "product:" + id;
        ProductDTO dto = convertToDTO(saved);
        redisTemplate.opsForValue().set(key, dto, Duration.ofHours(1));
        
        return dto;
    }
    
    public void deleteProduct(Long id) {
        // 1. Delete from database
        productRepository.deleteById(id);
        
        // 2. Invalidate cache
        String key = "product:" + id;
        redisTemplate.delete(key);
    }
}

// Write-Through Cache
@Service
public class WriteThroughCacheService {
    
    public ProductDTO updateProduct(Long id, ProductDTO updates) {
        // 1. Update cache first
        String key = "product:" + id;
        redisTemplate.opsForValue().set(key, updates, Duration.ofHours(1));
        
        // 2. Update database
        Product product = productRepository.findById(id).orElseThrow();
        updateEntity(product, updates);
        productRepository.save(product);
        
        return updates;
    }
}

// Cache Warming
@Component
public class CacheWarmer implements ApplicationRunner {
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private RedisTemplate<String, ProductDTO> redisTemplate;
    
    @Override
    public void run(ApplicationArguments args) {
        warmUpProductCache();
    }
    
    private void warmUpProductCache() {
        // Load popular products into cache
        List<Product> popularProducts = productRepository.findTop100ByOrderByViewCountDesc();
        
        popularProducts.forEach(product -> {
            ProductDTO dto = convertToDTO(product);
            String key = "product:" + product.getId();
            redisTemplate.opsForValue().set(key, dto, Duration.ofHours(1));
        });
        
        log.info("Cache warmed up with {} products", popularProducts.size());
    }
}
```

### 15. How do you design APIs for search and filtering?

**Answer:**

```java
// Search API with multiple filters
@RestController
@RequestMapping("/api/products/search")
public class ProductSearchController {
    
    @Autowired
    private ProductSearchService searchService;
    
    /**
     * GET /api/products/search?
     *   q=laptop
     *   &category=electronics
     *   &minPrice=500
     *   &maxPrice=2000
     *   &brand=Dell,HP,Lenovo
     *   &inStock=true
     *   &rating=4
     *   &sortBy=price
     *   &sortOrder=asc
     *   &page=0
     *   &size=20
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ProductDTO>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
            .query(q)
            .category(category)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .brands(brand)
            .inStock(inStock)
            .minRating(rating)
            .build();
        
        Sort.Direction direction = Sort.Direction.fromString(sortOrder);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<ProductDTO> results = searchService.search(criteria, pageable);
        
        return ResponseEntity.ok(convertToPagedResponse(results));
    }
    
    // Advanced search with POST (for complex queries)
    @PostMapping
    public ResponseEntity<PagedResponse<ProductDTO>> advancedSearch(
            @RequestBody AdvancedSearchRequest request) {
        
        Page<ProductDTO> results = searchService.advancedSearch(request);
        return ResponseEntity.ok(convertToPagedResponse(results));
    }
    
    // Autocomplete/suggestions
    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestions(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<String> suggestions = searchService.getSuggestions(q, limit);
        return ResponseEntity.ok(suggestions);
    }
    
    // Faceted search (aggregations)
    @GetMapping("/facets")
    public ResponseEntity<SearchFacets> getFacets(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category) {
        
        SearchFacets facets = searchService.getFacets(q, category);
        return ResponseEntity.ok(facets);
    }
}

// Search Service with Elasticsearch
@Service
public class ProductSearchService {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    public Page<ProductDTO> search(ProductSearchCriteria criteria, Pageable pageable) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        
        // Full-text search
        if (criteria.getQuery() != null && !criteria.getQuery().isEmpty()) {
            queryBuilder.must(QueryBuilders.multiMatchQuery(criteria.getQuery())
                .field("name", 2.0f) // Boost name field
                .field("description")
                .field("brand")
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO));
        }
        
        // Category filter
        if (criteria.getCategory() != null) {
            queryBuilder.filter(QueryBuilders.termQuery("category", criteria.getCategory()));
        }
        
        // Price range filter
        if (criteria.getMinPrice() != null || criteria.getMaxPrice() != null) {
            RangeQueryBuilder priceRange = QueryBuilders.rangeQuery("price");
            if (criteria.getMinPrice() != null) {
                priceRange.gte(criteria.getMinPrice());
            }
            if (criteria.getMaxPrice() != null) {
                priceRange.lte(criteria.getMaxPrice());
            }
            queryBuilder.filter(priceRange);
        }
        
        // Brand filter
        if (criteria.getBrands() != null && !criteria.getBrands().isEmpty()) {
            queryBuilder.filter(QueryBuilders.termsQuery("brand", criteria.getBrands()));
        }
        
        // Stock filter
        if (criteria.getInStock() != null && criteria.getInStock()) {
            queryBuilder.filter(QueryBuilders.rangeQuery("stockQuantity").gt(0));
        }
        
        // Rating filter
        if (criteria.getMinRating() != null) {
            queryBuilder.filter(QueryBuilders.rangeQuery("rating")
                .gte(criteria.getMinRating()));
        }
        
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withPageable(pageable)
            .build();
        
        SearchHits<ProductDocument> searchHits = 
            elasticsearchTemplate.search(searchQuery, ProductDocument.class);
        
        List<ProductDTO> products = searchHits.getSearchHits().stream()
            .map(hit -> convertToDTO(hit.getContent()))
            .collect(Collectors.toList());
        
        return new PageImpl<>(products, pageable, searchHits.getTotalHits());
    }
    
    public SearchFacets getFacets(String query, String category) {
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        
        if (query != null) {
            queryBuilder.must(QueryBuilders.multiMatchQuery(query)
                .field("name")
                .field("description"));
        }
        
        if (category != null) {
            queryBuilder.filter(QueryBuilders.termQuery("category", category));
        }
        
        // Aggregations
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .addAggregation(AggregationBuilders.terms("categories").field("category"))
            .addAggregation(AggregationBuilders.terms("brands").field("brand"))
            .addAggregation(AggregationBuilders.range("priceRanges").field("price")
                .addRange(0, 100)
                .addRange(100, 500)
                .addRange(500, 1000)
                .addRange(1000, Double.MAX_VALUE))
            .addAggregation(AggregationBuilders.terms("ratings").field("rating"))
            .build();
        
        SearchHits<ProductDocument> searchHits = 
            elasticsearchTemplate.search(searchQuery, ProductDocument.class);
        
        return extractFacets(searchHits.getAggregations());
    }
    
    public List<String> getSuggestions(String query, int limit) {
        CompletionSuggestionBuilder suggestionBuilder = 
            SuggestBuilders.completionSuggestion("suggest")
                .prefix(query)
                .size(limit);
        
        SuggestBuilder suggestBuilder = new SuggestBuilder()
            .addSuggestion("product-suggestions", suggestionBuilder);
        
        SearchRequest searchRequest = new SearchRequest("products")
            .source(new SearchSourceBuilder().suggest(suggestBuilder));
        
        // Execute and extract suggestions
        // Return list of suggestion strings
        return Collections.emptyList(); // Simplified
    }
}

// Advanced Search Request
@Data
public class AdvancedSearchRequest {
    private String query;
    private List<FilterCriteria> filters;
    private List<SortCriteria> sort;
    private int page;
    private int size;
}

@Data
public class FilterCriteria {
    private String field;
    private FilterOperator operator;
    private Object value;
}

public enum FilterOperator {
    EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, 
    CONTAINS, IN, NOT_IN, BETWEEN, IS_NULL, IS_NOT_NULL
}

// GraphQL for flexible querying
@Controller
public class ProductGraphQLController {
    
    @Autowired
    private ProductService productService;
    
    @QueryMapping
    public Page<ProductDTO> searchProducts(
            @Argument String query,
            @Argument ProductFilter filter,
            @Argument int page,
            @Argument int size) {
        
        return productService.search(query, filter, PageRequest.of(page, size));
    }
}

// GraphQL Schema
/*
type Query {
  searchProducts(
    query: String
    filter: ProductFilter
    page: Int = 0
    size: Int = 20
  ): ProductPage!
}

input ProductFilter {
  category: String
  minPrice: Float
  maxPrice: Float
  brands: [String!]
  inStock: Boolean
  minRating: Int
}

type ProductPage {
  content: [Product!]!
  totalElements: Int!
  totalPages: Int!
  number: Int!
  size: Int!
}

type Product {
  id: ID!
  name: String!
  description: String
  price: Float!
  category: String!
  brand: String!
  rating: Float
  stockQuantity: Int!
}
*/
```

### 16-25. Additional Important Questions

**16. How do you implement API documentation best practices?**
- Use OpenAPI 3.0 specification
- Include examples for all endpoints
- Document error responses
- Provide authentication details
- Include rate limiting information
- Add changelog for version updates

**17. How do you handle API deprecation?**
- Add deprecation headers (X-API-Deprecated, Sunset)
- Provide migration guides
- Maintain parallel versions
- Communicate timeline to clients
- Monitor usage of deprecated endpoints

**18. What are the best practices for API naming conventions?**
- Use lowercase with hyphens (kebab-case) for URLs
- Use plural nouns for collections
- Use verbs only for actions that don't fit CRUD
- Keep URLs short and intuitive
- Avoid deep nesting (max 2-3 levels)

**19. How do you implement API monitoring and observability?**
- Request/response logging
- Performance metrics (latency, throughput)
- Error rate tracking
- Distributed tracing (Zipkin, Jaeger)
- Health check endpoints
- Custom business metrics

**20. How do you design APIs for microservices?**
- Domain-driven design
- API Gateway pattern
- Service mesh for communication
- Event-driven architecture
- Circuit breakers and fallbacks
- Distributed transactions (Saga pattern)

**21. How do you handle API backward compatibility?**
- Additive changes only
- Optional new fields
- Maintain old field names
- Use API versioning for breaking changes
- Deprecation period before removal

**22. What are the best practices for API testing?**
- Unit tests for business logic
- Integration tests for endpoints
- Contract testing (Pact)
- Load testing (JMeter, Gatling)
- Security testing (OWASP)
- API documentation testing

**23. How do you implement API analytics?**
- Track endpoint usage
- Monitor response times
- Analyze error patterns
- User behavior tracking
- A/B testing support
- Custom event tracking

**24. How do you design APIs for mobile applications?**
- Minimize payload size
- Support offline mode
- Implement data synchronization
- Use compression
- Optimize for battery life
- Handle poor network conditions

**25. What are the common API design anti-patterns to avoid?**
- Chatty APIs (too many requests)
- Exposing internal implementation
- Ignoring HTTP status codes
- Poor error messages
- No versioning strategy
- Inconsistent naming
- Missing pagination
- No rate limiting
- Lack of documentation
- Ignoring security best practices

---

## Summary

This comprehensive API Design & OpenAPI interview guide covers:

### Core Topics:
1. **RESTful Principles** - Resource design, HTTP methods, status codes
2. **API Responses** - Error handling, pagination, filtering, sorting
3. **HATEOAS** - Hypermedia-driven APIs
4. **Idempotency** - Safe retry mechanisms
5. **OpenAPI Specification** - Documentation standards
6. **SpringDoc** - Code-first documentation

### Advanced Topics:
7. **API Versioning** - URI, header, content negotiation strategies
8. **Breaking Changes** - Migration strategies, feature flags
9. **Authentication** - JWT, OAuth2, API keys
10. **Rate Limiting** - Token bucket, sliding window algorithms
11. **Webhooks** - Event-driven integrations
12. **File Operations** - Upload, download, streaming
13. **Caching** - HTTP caching, Redis, cache patterns
14. **Search** - Elasticsearch, faceted search, autocomplete

### Best Practices:
15. Documentation standards
16. Deprecation strategies
17. Naming conventions
18. Monitoring and observability
19. Microservices design
20. Backward compatibility
21. Testing strategies
22. Analytics implementation
23. Mobile optimization
24. Anti-patterns to avoid

Perfect for interviews at **Senior Developer, Technical Lead, and Architect** levels with focus on real-world API design decisions and trade-offs.
