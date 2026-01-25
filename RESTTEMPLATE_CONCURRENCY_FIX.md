# RestTemplate Concurrency Fix

## Issue Description

When multiple requests tried to enroll sections simultaneously, a `ConcurrentModificationException` occurred:

```
java.util.ConcurrentModificationException: null
    at java.base/java.util.ArrayList.sort(ArrayList.java:1806)
    at org.springframework.core.annotation.AnnotationAwareOrderComparator.sort
    at org.springframework.http.client.support.InterceptingHttpAccessor.setInterceptors
    at com.datagami.edudron.student.service.BulkEnrollmentService.getRestTemplate
```

## Root Cause

The `getRestTemplate()` method in `BulkEnrollmentService` was using lazy initialization without proper synchronization:

```java
private RestTemplate restTemplate;

private RestTemplate getRestTemplate() {
    if (restTemplate == null) {  // ❌ Not thread-safe
        restTemplate = new RestTemplate();
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new TenantContextRestTemplateInterceptor());
        interceptors.add(...);  // Add JWT forwarding interceptor
        restTemplate.setInterceptors(interceptors);  // ❌ Race condition here
    }
    return restTemplate;
}
```

**Problem:**
- Multiple threads can enter the `if (restTemplate == null)` block simultaneously
- Thread A creates RestTemplate and starts setting interceptors
- Thread B also creates RestTemplate and starts setting interceptors
- Spring tries to sort the interceptors list while it's being modified
- Results in `ConcurrentModificationException`

This is a classic **double-check locking** problem without proper synchronization.

## Solution

Implemented **thread-safe lazy initialization** using double-checked locking with volatile:

```java
private volatile RestTemplate restTemplate;
private final Object restTemplateLock = new Object();

private RestTemplate getRestTemplate() {
    if (restTemplate == null) {  // First check (no locking)
        synchronized (restTemplateLock) {  // Lock for initialization
            if (restTemplate == null) {  // Second check (with locking)
                RestTemplate template = new RestTemplate();
                List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
                interceptors.add(new TenantContextRestTemplateInterceptor());
                interceptors.add((request, body, execution) -> {
                    // JWT forwarding logic
                    return execution.execute(request, body);
                });
                template.setInterceptors(interceptors);
                restTemplate = template;  // ✅ Atomic assignment with volatile
            }
        }
    }
    return restTemplate;
}
```

## Why This Works

### Key Components

1. **`volatile` keyword**
   - Ensures visibility of changes across threads
   - Prevents instruction reordering
   - Guarantees that once `restTemplate` is set, all threads see the fully initialized object

2. **Synchronized block**
   - Only one thread can enter the initialization block at a time
   - Prevents multiple RestTemplate instances from being created

3. **Double-checked locking**
   - First check (`if (restTemplate == null)`) - Fast path, no locking needed if already initialized
   - Second check (inside synchronized block) - Ensures only one thread initializes
   - Minimizes synchronization overhead after initialization

4. **Local variable `template`**
   - Build RestTemplate locally before assigning to shared field
   - Ensures the object is fully constructed before making it visible to other threads

### Execution Flow

**Scenario 1: First Access (Cold Start)**
```
Thread A                                Thread B
--------                                --------
Check: restTemplate == null? YES
Enter synchronized block
Check again: restTemplate == null? YES
Create RestTemplate locally
Add interceptors
Assign to restTemplate (volatile write)
Exit synchronized block
                                        Check: restTemplate == null? NO
                                        Return existing restTemplate
```

**Scenario 2: Concurrent First Access**
```
Thread A                                Thread B
--------                                --------
Check: restTemplate == null? YES        Check: restTemplate == null? YES
Enter synchronized block                Wait for lock...
Check again: restTemplate == null? YES
Create RestTemplate
Assign to restTemplate
Exit synchronized block
                                        Acquire lock
                                        Check again: restTemplate == null? NO
                                        Exit synchronized block
                                        Return existing restTemplate
```

**Scenario 3: Subsequent Access (Hot Path)**
```
Thread A                                Thread B
--------                                --------
Check: restTemplate == null? NO         Check: restTemplate == null? NO
Return existing restTemplate            Return existing restTemplate
(No synchronization needed!)            (No synchronization needed!)
```

## Files Modified

**File:** `student/src/main/java/com/datagami/edudron/student/service/BulkEnrollmentService.java`

**Changes:**
- Added `volatile` modifier to `restTemplate` field (Line 59)
- Added `restTemplateLock` object for synchronization (Line 60)
- Updated `getRestTemplate()` method with double-checked locking (Lines 62-93)

## Benefits

1. ✅ **Thread-safe** - No more `ConcurrentModificationException`
2. ✅ **Efficient** - Synchronization only needed during first initialization
3. ✅ **Singleton** - Only one RestTemplate instance created per service instance
4. ✅ **Fast** - Subsequent calls have no synchronization overhead

## Performance Characteristics

- **First call:** Synchronized initialization (~microseconds overhead)
- **Subsequent calls:** Simple volatile read (~nanoseconds)
- **Memory:** Single RestTemplate instance per service bean
- **Contention:** Minimal - only during cold start

## Testing

### Test Concurrent Access

```java
@Test
public void testConcurrentRestTemplateAccess() throws Exception {
    BulkEnrollmentService service = new BulkEnrollmentService();
    int threadCount = 100;
    CountDownLatch latch = new CountDownLatch(threadCount);
    Set<RestTemplate> instances = ConcurrentHashMap.newKeySet();
    
    // Launch many threads simultaneously
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            RestTemplate template = service.getRestTemplate();
            instances.add(template);
            latch.countDown();
        });
    }
    
    latch.await(5, TimeUnit.SECONDS);
    executor.shutdown();
    
    // Should have exactly ONE instance
    assertEquals(1, instances.size());
}
```

## Alternative Approaches Considered

### 1. Eager Initialization (@Bean)

```java
@Bean
public RestTemplate bulkEnrollmentRestTemplate() {
    RestTemplate template = new RestTemplate();
    // configure interceptors
    return template;
}
```

**Pros:** Simple, thread-safe
**Cons:** Always initialized even if never used, harder to forward per-request headers

### 2. Synchronized Method

```java
private synchronized RestTemplate getRestTemplate() {
    if (restTemplate == null) {
        // initialize
    }
    return restTemplate;
}
```

**Pros:** Simple, thread-safe
**Cons:** Synchronization overhead on every call (slower)

### 3. Double-Checked Locking (Chosen)

**Pros:** Thread-safe, efficient, lazy initialization, per-request header forwarding
**Cons:** Slightly more complex (but well-understood pattern)

## Related Issues

This same pattern should be applied to other services with similar lazy RestTemplate initialization:

- `CourseCopyWorker.java` (content service)
- `UserService.java` (identity service)
- `BulkStudentImportService.java`
- `AnalyticsService.java`
- `EnrollmentService.java`

## References

- [Java Memory Model and volatile](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4.5)
- [Double-Checked Locking Pattern](https://en.wikipedia.org/wiki/Double-checked_locking)
- [Effective Java Item 83: Use lazy initialization judiciously](https://www.oreilly.com/library/view/effective-java/9780134686097/)
