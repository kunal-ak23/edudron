# Cache & Redis – OffsetDateTime / JavaTimeModule

## Where the fix was applied

- **Content service** – Redis cache for exam detail was fixed in `content/config/CacheConfig.java`: (1) a Redis-only `ObjectMapper` with `JavaTimeModule` and default typing is used so `OffsetDateTime` serializes and `@class` is written; (2) on read, JSON with `@class` deserializes back to `ExamDetailDTO`. Old entries stored without `@class` will fail on read until deleted or expired.

## Checked – no change needed

| Service  | Cache backend | Cached types with date/time      | Status |
|----------|----------------|-----------------------------------|--------|
| **Content** | Redis        | `ExamDetailDTO` (examDetail)      | Fixed in CacheConfig |
| **Student** | Caffeine (in-memory) | `examFromContent` → `JsonNode` (no DTO) | OK – no Redis, no Redis serializer |
| **Identity** | Caffeine     | tenantBranding, tenantFeature     | OK – no Redis |

- **Content** – `CourseCopyWorker` and `AIJobQueueService` use `StringRedisTemplate` and serialize with the injected `ObjectMapper` (already has JavaTimeModule). No change needed.
- **Content** – `RedisTemplate<String, Object>` in `RedisConfig` is not used anywhere. If you later use it for DTOs with `OffsetDateTime`, configure its value serializer with the same `ObjectMapper` (or use StringRedisTemplate + manual JSON).

## Do you need to remove Redis entries?

- **After deploying the default-typing fix:** Existing exam detail keys were stored **without** `@class`. The cache will **fail on read** for those entries (deserialization expects type info). So after this fix you should either **delete** existing examDetail keys once, or **wait 5 minutes** (TTL) for them to expire. New entries will be written with `@class` and will read correctly.

- **If you want to clear anyway:**
  - Keys look like: `examDetail::<clientId>::<examId>` (Spring Data Redis cache default pattern with `prefixCacheNameWith("examDetail::")`).
  - In Redis CLI:
    - List: `KEYS examDetail::*`
    - Delete all: `EVAL "return redis.call('del', unpack(redis.call('keys', 'examDetail::*')))" 0`  
      (or use `SCAN` + `DEL` in production instead of `KEYS`.)
  - Or wait **5 minutes** (cache TTL); entries will expire.

## Impact on other Redis usage

**No other code is affected.** Verified:

- **Content service – Redis cache:** The only Redis cache is `examDetail`. Only `ExamService.getExamDetailDTO` uses it (`@Cacheable` and `getCache(EXAM_DETAIL_CACHE)`). The updated `CacheManager` and `GenericJackson2JsonRedisSerializer` apply only to this cache.
- **Content service – RedisTemplate / StringRedisTemplate:** `CourseCopyWorker` and `AIJobQueueService` use `StringRedisTemplate` and serialize/deserialize JSON themselves (injected `ObjectMapper`). They do **not** use the `CacheManager` or `GenericJackson2JsonRedisSerializer`. Unchanged.
- **Content service – `RedisTemplate<String, Object>`:** Defined in `RedisConfig` but **not used** anywhere. Unchanged.
- **Identity service:** Caching uses **Caffeine** only (`tenantBranding`, `tenantFeature`). No Redis. Unchanged.
- **Student service:** Caching uses **Caffeine** only (`courseAnalytics`, `lectureAnalytics`, `sectionAnalytics`, `classAnalytics`, `examFromContent`). No Redis. Unchanged.

So the changes are limited to the content module’s single Redis cache (exam detail). Existing Redis usage elsewhere is unchanged and will not break.

## Summary

- Only **content** Redis cache needed a fix; that’s done in `CacheConfig` (Redis-only ObjectMapper with JavaTimeModule + default typing).
- Student and Identity caches don’t use Redis for these flows; no risk there.
- After deploying this fix, delete existing examDetail keys or wait 5 min for TTL so all entries use the new format with `@class`.
