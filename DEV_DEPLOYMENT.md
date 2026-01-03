# EduDron Development Deployment Guide

This guide covers multiple ways to deploy EduDron for development purposes.

## Prerequisites

- **Java 21** - Required for building and running services
- **Docker & Docker Compose** - For containerized deployment
- **Gradle 8.5+** - Build tool (included via wrapper)
- **PostgreSQL 16** - Database (can run via Docker)
- **Redis 7** - Cache (can run via Docker)

## Deployment Options

### Option 1: Full Docker Deployment (Recommended for Dev)

This option runs everything in Docker containers, including the database and services.

#### Step 1: Set Up Environment Variables

Create a `.env` file from the template:

```bash
cp env.example .env
```

Edit `.env` and configure:
- Database credentials (if using custom values)
- JWT secret (generate a secure one)
- Service ports (defaults are fine)
- Optional: Azure Storage, Razorpay, Azure OpenAI credentials

Generate a secure JWT secret:
```bash
openssl rand -base64 32
```

Generate encryption keys:
```bash
openssl rand -base64 32
```

#### Step 2: Start Database and Redis

```bash
# Start PostgreSQL and Redis
docker-compose -f docker-compose.db-only.yml up -d

# Verify they're running
docker ps | grep edudron
```

#### Step 3: Start All Services

```bash
# Using the dev deployment script
./scripts/start-dev.sh

# Or manually with docker-compose
docker-compose -f docker-compose.dev.yml up --build -d
```

#### Step 4: Verify Services

```bash
# Check service health
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Identity
curl http://localhost:8082/actuator/health  # Content
curl http://localhost:8083/actuator/health  # Student
curl http://localhost:8084/actuator/health  # Payment

# View logs
docker-compose -f docker-compose.dev.yml logs -f
```

#### Step 5: Create Initial Admin User

```bash
./scripts/create-super-admin.sh
```

### Option 2: Hybrid Deployment (Services in Docker, DB Local)

This option runs services in Docker but connects to a locally running PostgreSQL/Redis.

#### Step 1: Start Local Database

If you have PostgreSQL and Redis installed locally:

```bash
# Start PostgreSQL (if using Homebrew on macOS)
brew services start postgresql@16

# Start Redis (if using Homebrew on macOS)
brew services start redis

# Or use Docker for just the database
docker-compose -f docker-compose.db-only.yml up -d
```

#### Step 2: Configure Database

Create the database:

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database and user
CREATE DATABASE edudron;
CREATE USER edudron WITH PASSWORD 'edudron';
GRANT ALL PRIVILEGES ON DATABASE edudron TO edudron;
\q
```

#### Step 3: Start Services

```bash
# Services will connect to host.docker.internal
docker-compose -f docker-compose.dev.yml up --build -d
```

### Option 3: Local Development (No Docker)

This option runs everything locally using Gradle.

#### Step 1: Start Database and Redis

```bash
# Using Docker for infrastructure
docker-compose -f docker-compose.db-only.yml up -d

# Or use local installations
```

#### Step 2: Set Environment Variables

Export environment variables or create a `.env` file:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=edudron
export DB_USERNAME=edudron
export DB_PASSWORD=edudron
# ... (see env.example for full list)
```

#### Step 3: Start Services

```bash
# Option A: Use the management script
./scripts/edudron.sh start

# Option B: Start individually
./gradlew :identity:bootRun &
./gradlew :content:bootRun &
./gradlew :student:bootRun &
./gradlew :payment:bootRun &
./gradlew :gateway:bootRun &
```

## Service URLs

Once deployed, services are available at:

- **Gateway**: http://localhost:8080
- **Identity Service**: http://localhost:8081
- **Content Service**: http://localhost:8082
- **Student Service**: http://localhost:8083
- **Payment Service**: http://localhost:8084

## API Documentation

Swagger UI is available for each service:

- Identity: http://localhost:8081/swagger-ui.html
- Content: http://localhost:8082/swagger-ui.html
- Student: http://localhost:8083/swagger-ui.html
- Payment: http://localhost:8084/swagger-ui.html

## Useful Commands

### View Logs

```bash
# All services
docker-compose -f docker-compose.dev.yml logs -f

# Specific service
docker-compose -f docker-compose.dev.yml logs -f identity-service

# Local services (if using Option 3)
tail -f /tmp/identity.log
tail -f /tmp/content.log
```

### Stop Services

```bash
# Docker services
docker-compose -f docker-compose.dev.yml down

# Local services
./scripts/edudron.sh stop
```

### Restart Services

```bash
# Docker services
docker-compose -f docker-compose.dev.yml restart

# Local services
./scripts/edudron.sh restart
```

### Check Status

```bash
# Docker services
docker-compose -f docker-compose.dev.yml ps

# Local services
./scripts/edudron.sh status
```

### Rebuild Services

```bash
# Rebuild and restart
docker-compose -f docker-compose.dev.yml up --build -d

# Rebuild specific service
docker-compose -f docker-compose.dev.yml build identity-service
docker-compose -f docker-compose.dev.yml up -d identity-service
```

## Database Management

### Access Database

```bash
# Using Docker
docker exec -it edudron-postgres-local psql -U edudron -d edudron

# Using local PostgreSQL
psql -U edudron -d edudron -h localhost
```

### Run Migrations

Migrations are typically handled automatically by Spring Boot on startup. Check service logs to verify.

### Backup Database

```bash
# Using Docker
docker exec edudron-postgres-local pg_dump -U edudron edudron > backup.sql

# Using local PostgreSQL
pg_dump -U edudron -d edudron > backup.sql
```

### Restore Database

```bash
# Using Docker
docker exec -i edudron-postgres-local psql -U edudron -d edudron < backup.sql

# Using local PostgreSQL
psql -U edudron -d edudron < backup.sql
```

## Troubleshooting

### Services Won't Start

1. **Check Docker is running:**
   ```bash
   docker info
   ```

2. **Check ports are available:**
   ```bash
   lsof -i :8080
   lsof -i :8081
   # ... etc
   ```

3. **Check database connection:**
   ```bash
   docker exec -it edudron-postgres-local psql -U edudron -d edudron -c "SELECT 1;"
   ```

4. **View service logs:**
   ```bash
   docker-compose -f docker-compose.dev.yml logs identity-service
   ```

### Database Connection Issues

1. **Verify database is running:**
   ```bash
   docker ps | grep postgres
   ```

2. **Check database credentials** in `.env` file match docker-compose configuration

3. **For Docker services connecting to local DB**, ensure `host.docker.internal` is accessible:
   ```bash
   # On macOS/Windows, this should work automatically
   # On Linux, you may need to add to docker-compose:
   extra_hosts:
     - "host.docker.internal:host-gateway"
   ```

### Service Health Checks Fail

1. **Wait longer** - Services may take 30-60 seconds to fully start
2. **Check logs** for startup errors
3. **Verify dependencies** - Ensure database and Redis are ready before services start

### Port Conflicts

If ports are already in use:

1. **Find the process:**
   ```bash
   lsof -i :8080
   ```

2. **Kill the process:**
   ```bash
   kill -9 <PID>
   ```

3. **Or change ports** in `docker-compose.dev.yml` and `.env`

## Next Steps

After successful deployment:

1. **Create admin user:**
   ```bash
   ./scripts/create-super-admin.sh
   ```

2. **Set up frontend apps** (see `frontend/QUICK_START.md`)

3. **Configure optional services:**
   - Azure Storage for media files
   - Razorpay for payments
   - Azure OpenAI for AI features

4. **Test API endpoints** using Swagger UI

## Environment-Specific Configuration

### Development Environment Variables

Key variables for development:

```bash
# Database
DB_HOST=localhost  # or host.docker.internal for Docker services
DB_PORT=5432
DB_NAME=edudron
DB_USERNAME=edudron
DB_PASSWORD=edudron

# JWT (use a secure secret in production!)
JWT_SECRET=devSecretKey123456789012345678901234567890
JWT_EXPIRATION=86400

# Service URLs (for gateway routing)
IDENTITY_SERVICE_URL=http://localhost:8081
CONTENT_SERVICE_URL=http://localhost:8082
STUDENT_SERVICE_URL=http://localhost:8083
PAYMENT_SERVICE_URL=http://localhost:8084
```

### Production Considerations

For production deployment:

1. Use strong, unique secrets for JWT and encryption keys
2. Use managed database services (Azure Database for PostgreSQL)
3. Use managed Redis (Azure Cache for Redis)
4. Configure proper CORS settings
5. Set up SSL/TLS certificates
6. Configure monitoring and logging
7. Set up backup strategies
8. Use environment-specific configuration files

## Additional Resources

- [README.md](README.md) - Project overview
- [INTEGRATION.md](INTEGRATION.md) - Service integration details
- [frontend/QUICK_START.md](frontend/QUICK_START.md) - Frontend setup
- [frontend/SETUP.md](frontend/SETUP.md) - Detailed frontend guide

