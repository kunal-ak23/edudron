# Database connection pooling and PostgreSQL configuration

## Application side (HikariCP)

All services that use PostgreSQL (identity, content, student, payment) now use explicit HikariCP settings in `application.yml`:

- **maximum-pool-size**: 30 per service (override with `HIKARI_MAX_POOL_SIZE` per service if needed)
- **minimum-idle**: 5
- **connection-timeout**: 20s
- **idle-timeout**: 5 min, **max-lifetime**: 10 min
- **leak-detection-threshold**: 60s (logs a warning if a connection is held too long)

So with default settings, **total connections = (number of instances) × 30 per service type**.

- **Single instance per service (4 pods total):** 4 × 30 = **120** connections.
- **With service replicas:** each instance has its own pool. For example:
  - 2 identity + 2 content + 2 student + 2 payment → 8 × 30 = **240** connections.
  - 3 identity + 3 content + 3 student + 3 payment → 12 × 30 = **360** connections.

**Formula:**  
`max_connections` (PostgreSQL) &gt; **(identity instances + content instances + student instances + payment instances) × 30** + headroom (e.g. 20–50 for admin, replication, migrations).

---

## PostgreSQL server side

PostgreSQL has a server limit: **`max_connections`** (default **100**).  
If the sum of all application pool sizes (plus any other clients) exceeds this, new connections will be rejected with:

```text
FATAL: sorry, too many clients already
```

So you **do** need to change the PostgreSQL config when you run all services with the default pool size.

### When to change

| Setup | Total connections | Set `max_connections` to |
|-------|-------------------|--------------------------|
| 1 instance per service (4 total) | 120 | **150** (min), **200** (safe) |
| 2 instances per service (8 total) | 240 | **280** (min), **300** (safe) |
| 3 instances per service (12 total) | 360 | **400** (min), **450** (safe) |
| 4 instances per service (16 total) | 480 | **530** (min), **550** (safe) |

If your replica counts differ per service (e.g. 3 identity, 2 student, 2 content, 2 payment), add them up: (3+2+2+2)×30 = 270 → set at least **300**.

### How to change (PostgreSQL)

1. **Check current value**
   ```sql
   SHOW max_connections;
   ```

2. **Set in `postgresql.conf`** (path varies by install; on Linux often `/etc/postgresql/<version>/main/postgresql.conf` or under data directory):
   ```ini
   max_connections = 200
   ```
   Use a value **greater than** the sum of all HikariCP `maximum-pool-size` across all app instances, plus a few for superuser/admin (e.g. 10–20).

3. **Restart PostgreSQL** (changing `max_connections` requires restart):
   ```bash
   sudo systemctl restart postgresql   # systemd
   # or
   pg_ctl restart -D /path/to/data
   ```

4. **Optional: memory**  
   PostgreSQL allocates ~400KB–1MB per connection. With `max_connections = 200`, ensure enough RAM; if needed, tune `shared_buffers` and other memory settings.

---

## Summary

| Where              | What to do |
|--------------------|------------|
| **App (done)**     | HikariCP pool size 30 per service instance; same config for identity, content, student, payment. |
| **PostgreSQL**     | Set `max_connections` &gt; **(total service instances × 30)** + headroom (20–50). With replicas, e.g. 8 instances → 240 + 50 = **300**; 12 instances → **450**. Restart PostgreSQL after changing. |

If you see `sorry, too many clients already`, increase `max_connections` on the PostgreSQL server.
