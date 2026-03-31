# Exam Scalability Fixes — Post-Incident Plan

**Date**: 2026-03-31
**Incident**: 1400-student exam caused DB connection pool exhaustion, cascading failures
**Status**: Stabilized (pool bumped to 100, services restarted)

---

## Root Cause Analysis

1. **HikariPool exhaustion** — core-api had `maximum-pool-size: 50`. Under 1400 concurrent students, all 50 connections were active with 30+ waiting. Login and exam APIs stopped responding.

2. **Cascading internal calls** — `UserUtil` resolves user IDs by calling the identity service via RestTemplate (`/idp/users/me`). Since identity runs in the same core-api process and shares the same DB pool, this creates a deadlock-like pattern: exam API holds a connection → calls identity → identity needs a connection → pool exhausted → both block.

3. **DB CPU spike on scale-up** — When we added replicas, all new instances tried to establish SSL connections simultaneously. The Burstable `Standard_B2s` (2 vCPUs) hit 95% CPU and rejected SSL handshakes, causing `PSQLException: The server does not support SSL`.

4. **Gateway over-scaling** — Gateway scaled to 20 replicas because backed-up requests (600s timeout) kept concurrent request count high per replica.

---

## Fix Plan

### P0 — Immediate (before next exam)

#### 1. Right-size connection pools
**Files**: `core-api/src/main/resources/application.yml`, `content/src/main/resources/application.yml`

```yaml
# core-api (handles identity + student + payment)
hikari:
  minimum-idle: 10
  maximum-pool-size: 80
  connection-timeout: 20000
  idle-timeout: 300000

# content
hikari:
  minimum-idle: 5
  maximum-pool-size: 30
```

**Why 80 for core-api**: It bundles 3 services. At 3 replicas = 240 connections. Under 750 max with headroom.
**Why minimum-idle**: Prevents thundering herd of connections on startup. Connections grow gradually.

#### 2. Fix UserUtil cascading call
**File**: `student/.../util/UserUtil.java`

The `UserUtil.resolveUserId()` makes a synchronous HTTP call to identity service while holding a DB transaction/connection. Fix options:

- **Option A (recommended)**: Cache user ID lookups in Redis (user ID rarely changes)
- **Option B**: Move the HTTP call outside the `@Transactional` boundary so it doesn't hold a DB connection while waiting
- **Option C**: Query the user table directly instead of going through HTTP (they're in the same DB since core-api bundles everything)

#### 3. Reduce gateway response timeout for auth routes
**File**: `gateway/src/main/resources/application.yml`

Add per-route timeout for auth (no login should take 10 minutes):

```yaml
- id: identity-auth
  uri: ${CORE_API_SERVICE_URL:http://localhost:8085}
  predicates:
    - Path=/auth/**
  metadata:
    response-timeout: 30000  # 30 seconds for auth
```

### P1 — Before scaling to 2000+ students

#### 4. Upgrade DB tier
Current: `Standard_B2s` (Burstable, 2 vCPUs, burst credits)
Recommended: `Standard_D2ds_v5` (General Purpose, 2 vCPUs, consistent performance)

Burstable tier throttles CPU once burst credits are exhausted. During an exam, sustained load depletes credits.

```bash
az postgres flexible-server update \
  --resource-group edudron-dev-rg \
  --name edudron-dev-edudron-dev-outy-postgres \
  --sku-name Standard_D2ds_v5 \
  --tier GeneralPurpose
```

#### 5. Add connection pooler (PgBouncer)
Azure Flexible Server supports built-in PgBouncer. This pools connections at the DB level, reducing total connection count even with many replicas.

```bash
az postgres flexible-server parameter set \
  --resource-group edudron-dev-rg \
  --server-name edudron-dev-edudron-dev-outy-postgres \
  --name pgbouncer.enabled --value true
```

Then change connection strings to use port 6432 instead of 5432.

#### 6. Pre-warm replicas before exams
Scale up replicas 15-20 minutes before exam start, not right when traffic hits. This gives:
- Time for Liquibase migrations to complete
- HikariCP to establish connections gradually
- Health probes to pass before traffic arrives

### P2 — Architecture improvements

#### 7. Separate exam traffic from auth traffic
Consider a dedicated "exam-api" service or at minimum separate thread pools for exam endpoints vs auth endpoints in core-api, so exam save-progress doesn't starve login.

#### 8. Add circuit breaker on UserUtil
If identity service is slow, fail fast instead of holding connections. Use Resilience4j circuit breaker.

---

## Post-Exam Cleanup (today)

After the exam ends, scale back down:

```bash
az containerapp update --name gateway-dev --resource-group edudron-dev-rg --min-replicas 2 --max-replicas 10
az containerapp update --name core-api-dev --resource-group edudron-dev-rg --min-replicas 1 --max-replicas 5
az containerapp update --name content-dev --resource-group edudron-dev-rg --min-replicas 1 --max-replicas 5
```

Remove temporary env vars:
```bash
az containerapp update --name core-api-dev --resource-group edudron-dev-rg --remove-env-vars RESTART_MARKER HIKARI_MAX_POOL_SIZE
az containerapp update --name gateway-dev --resource-group edudron-dev-rg --remove-env-vars RESTART_TS
```
