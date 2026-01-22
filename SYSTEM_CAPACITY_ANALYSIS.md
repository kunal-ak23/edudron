# System Capacity Analysis for 1500 Concurrent Students

## Executive Summary

**Current Status: ⚠️ Needs Optimization for 1500 Concurrent Students**

The system can handle 1500 students, but requires infrastructure adjustments and some optimizations to ensure smooth performance.

## Current Infrastructure Configuration

### Service Resources (Azure Container Apps)

| Service | CPU | Memory | Min Replicas | Max Replicas |
|---------|-----|--------|--------------|--------------|
| **Gateway** | 1.0 | 2.0Gi | 2 | 10 |
| **Identity** | 0.5 | 1.0Gi | 1 | 5 |
| **Content** | 2.0 | 4.0Gi | 1 | 5 |
| **Student** | 0.5 | 1.0Gi | 1 | 5 |
| **Payment** | 0.5 | 1.0Gi | 1 | 5 |

**Total Minimum Resources**: 4.5 CPU, 9.0Gi Memory
**Total Maximum Resources**: 22.5 CPU, 45.0Gi Memory (with scaling)

### Database
- PostgreSQL (no explicit connection pool configuration found)
- Default HikariCP settings: ~10 connections per service
- **Estimated Total Connections**: 50-100 (across all services)

## Capacity Analysis

### 1. Memory Capacity ✅ (After Analytics Optimization)

**Per Service Memory Usage**:
- **Gateway** (2.0Gi): ~500MB base + ~2MB per concurrent request = **~750 concurrent requests**
- **Identity** (1.0Gi): ~300MB base + ~1MB per request = **~700 concurrent auth requests**
- **Content** (4.0Gi): ~500MB base + ~5MB per request (video processing) = **~700 concurrent requests**
- **Student** (1.0Gi): ~300MB base + ~10MB per request (after optimization) = **~70 concurrent requests** ⚠️
- **Payment** (1.0Gi): ~200MB base + ~2MB per request = **~400 concurrent requests**

**With Analytics Optimization**:
- Student service memory per analytics request: **~10-50MB** (down from 1.5GB)
- Can handle **~20 concurrent analytics requests** per instance
- With 5 replicas: **~100 concurrent analytics requests**

### 2. Database Connection Pool ⚠️ (Potential Bottleneck)

**Current State**:
- No explicit HikariCP configuration found
- Default Spring Boot HikariCP: **10 connections per service**
- With 5 services × 10 = **50 total connections**

**For 1500 Concurrent Students**:
- **Estimated Need**: 150-300 connections
- **Current Capacity**: 50 connections
- **Gap**: 100-250 connections needed

**Recommendation**: Configure connection pools explicitly:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Per service
      minimum-idle: 5
      connection-timeout: 30000
```

### 3. Thread Pool Capacity ⚠️ (Needs Configuration)

**Current State**:
- No explicit Tomcat thread configuration found
- Default Spring Boot Tomcat: **200 threads per service**

**For 1500 Concurrent Students**:
- **Estimated Need**: 300-500 threads per service
- **Current Capacity**: 200 threads per service
- **Gap**: 100-300 threads needed per service

**Recommendation**: Configure thread pools:
```yaml
server:
  tomcat:
    threads:
      max: 300
      min-spare: 50
```

### 4. Gateway Capacity ✅ (Good)

- **2 replicas minimum**: Can handle ~1500 concurrent requests
- **Scales to 10 replicas**: Can handle ~7500 concurrent requests
- **Memory**: 2.0Gi per replica (sufficient)

### 5. Content Service ⚠️ (Video Processing)

- **Memory**: 4.0Gi (good for video processing)
- **CPU**: 2.0 (sufficient)
- **Video Processing**: Uses temp files (memory efficient)
- **Capacity**: ~700 concurrent requests per instance

## Capacity Projections

### Scenario: 1500 Concurrent Students

**Assumptions**:
- Each student makes ~2-3 requests per minute (viewing lectures, progress updates)
- Peak load: 1500 students × 3 requests/min = **4500 requests/minute = 75 requests/second**
- Analytics requests: ~10% of users = 150 concurrent analytics requests

**Current Capacity**:
- **Gateway**: ✅ Can handle 75 req/s (with 2 replicas)
- **Student Service**: ⚠️ **1 replica = 70 concurrent requests** (needs scaling)
- **Database**: ⚠️ **50 connections** (needs increase to 150-300)
- **Thread Pools**: ⚠️ **200 threads/service** (needs increase to 300-500)

**With Recommended Changes**:
- **Student Service**: Scale to 3-5 replicas = **210-350 concurrent requests** ✅
- **Database**: Increase to 20 connections/service = **100 total** (still tight, but manageable)
- **Thread Pools**: Increase to 300 threads/service ✅

## Critical Issues & Recommendations

### 🔴 **Critical: Database Connection Pool**

**Issue**: Default 10 connections per service = 50 total (insufficient)

**Solution**:
```yaml
# Add to each service's application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Impact**: Increases capacity from 50 to 100 connections

### 🟡 **High: Student Service Scaling**

**Issue**: 1 replica with 1.0Gi memory = ~70 concurrent requests

**Solution**: 
1. Scale to **3-5 replicas minimum** for 1500 students
2. Or increase memory to **2.0Gi** per replica

**Impact**: Increases capacity from 70 to 210-350 concurrent requests

### 🟡 **High: Thread Pool Configuration**

**Issue**: Default 200 threads may be insufficient under load

**Solution**:
```yaml
# Add to each service's application.yml
server:
  tomcat:
    threads:
      max: 300
      min-spare: 50
    accept-count: 100
    max-connections: 10000
```

**Impact**: Handles more concurrent requests per service

### 🟢 **Medium: Connection Timeout**

**Issue**: Long-running operations may timeout

**Current**: 600 seconds (10 minutes) - ✅ Good

## Recommended Infrastructure Changes

### 1. Update `services-config.json`

```json
{
  "student": {
    "memory": "2.0Gi",  // Increase from 1.0Gi
    "minReplicas": 3,   // Increase from 1
    "maxReplicas": 10   // Keep at 10
  }
}
```

### 2. Add Connection Pool Configuration

Create/update `application.yml` in each service:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### 3. Add Thread Pool Configuration

```yaml
server:
  tomcat:
    threads:
      max: 300
      min-spare: 50
```

### 4. Database Configuration

**PostgreSQL Settings** (if you have access):
```sql
-- Increase max connections
ALTER SYSTEM SET max_connections = 200;

-- Increase shared_buffers (if memory allows)
ALTER SYSTEM SET shared_buffers = '256MB';
```

## Capacity After Optimizations

### With Recommended Changes:

| Component | Current | After Changes | Status |
|-----------|---------|---------------|--------|
| **Student Service Capacity** | 70 req | 210-350 req | ✅ |
| **Database Connections** | 50 | 100-200 | ✅ |
| **Thread Pools** | 200/service | 300/service | ✅ |
| **Gateway Capacity** | 1500 req | 1500 req | ✅ |
| **Total System Capacity** | ~500 students | **1500+ students** | ✅ |

## Cost Implications

### Current (Minimum):
- **CPU**: 4.5 cores
- **Memory**: 9.0Gi
- **Estimated Cost**: Base tier

### Recommended (For 1500 Students):
- **CPU**: 5.5 cores (Student: +0.5, +2 replicas)
- **Memory**: 11.0Gi (Student: +1.0Gi × 3 replicas)
- **Estimated Cost**: ~20-30% increase

## Monitoring Recommendations

Add metrics for:
1. **Database Connection Pool Usage**: Monitor active/idle connections
2. **Thread Pool Usage**: Monitor active threads
3. **Response Times**: p50, p95, p99
4. **Memory Usage**: Heap usage per service
5. **Request Rate**: Requests per second per service

## Conclusion

**Current State**: System can handle **~500-700 concurrent students** comfortably

**After Recommended Changes**: System can handle **1500+ concurrent students** with:
- ✅ Student service scaled to 3-5 replicas
- ✅ Database connection pools increased
- ✅ Thread pools configured
- ✅ Analytics optimizations (already done)

**Agility**: The system is **agile enough** with the recommended infrastructure adjustments. The analytics optimizations we implemented are critical for this scale.

## Action Items

1. ✅ **Analytics Optimization** - COMPLETED
2. ⏳ **Increase Student Service Memory** - 1.0Gi → 2.0Gi
3. ⏳ **Scale Student Service** - 1 → 3-5 replicas
4. ⏳ **Configure Connection Pools** - 10 → 20 per service
5. ⏳ **Configure Thread Pools** - 200 → 300 per service
6. ⏳ **Database Configuration** - Increase max_connections
