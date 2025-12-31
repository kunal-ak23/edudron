# EduDron - Learning Management System

EduDron is a multi-tenant Learning Management System (LMS) platform built with Spring Boot 3 and Java 21. It provides a comprehensive solution for universities and educational institutions to manage courses, students, and content delivery.

## Architecture

EduDron follows a microservices architecture with the following services:

- **Gateway** (port 8080) - API Gateway for routing requests
- **Identity** (port 8081) - Authentication and user management
- **Content** (port 8082) - Course and content management
- **Student** (port 8083) - Student enrollment and progress tracking
- **Payment** (port 8084) - Payment processing and subscriptions

## Tech Stack

- **Backend**: Spring Boot 3.3.4, Java 21
- **Database**: PostgreSQL 16
- **Cache**: Redis 7
- **Build**: Gradle
- **Containerization**: Docker
- **Deployment**: Azure Container Apps

## Quick Start

### Prerequisites

- Java 21
- Docker and Docker Compose
- Gradle 8.5+

### Local Development

1. **Start database and Redis:**
   ```bash
   docker-compose up -d
   ```

2. **Start all services:**
   ```bash
   ./scripts/edudron.sh start
   ```

3. **Check service status:**
   ```bash
   ./scripts/edudron.sh status
   ```

### Building Docker Images

```bash
# Build all services
./scripts/build-docker-images.sh all

# Build specific service
./scripts/build-docker-images.sh gateway
```

### Version Management

```bash
# List all versions
./scripts/manage-versions.sh list

# Get version for a service
./scripts/manage-versions.sh get gateway

# Set version
./scripts/manage-versions.sh set gateway 0.2.0

# Bump version
./scripts/manage-versions.sh bump gateway patch
```

## Project Structure

```
edudron/
├── common/          # Shared utilities and DTOs
├── gateway/         # API Gateway service
├── identity/        # Authentication service
├── content/         # Content management service
├── student/         # Student enrollment service
├── payment/         # Payment service
├── scripts/         # Build and deployment scripts
└── azure/           # Azure deployment configuration
```

## Database Schemas

- `common` - Tenants, audit logs, outbox events
- `idp` - Users, roles, permissions
- `content` - Courses, chapters, modules
- `student` - Enrollments, progress, assessments
- `payment` - Subscriptions, payments

## Features

- Multi-tenant architecture
- Role-based access control
- Course content management
- Student enrollment and progress tracking
- Batch management and batch-level progress tracking
- Drip content system (sequential unlocking)
- Premium subscriptions
- Razorpay payment integration
- Bulk student upload

## Development

### Running Individual Services

```bash
./gradlew :identity:bootRun
./gradlew :content:bootRun
./gradlew :student:bootRun
./gradlew :payment:bootRun
./gradlew :gateway:bootRun
```

### Service URLs

- Gateway: http://localhost:8080
- Identity: http://localhost:8081
- Content: http://localhost:8082
- Student: http://localhost:8083
- Payment: http://localhost:8084

## API Endpoints

### Student Service (via Gateway: `/student/**` or `/api/student/**`)

**Enrollments:**
- `POST /api/courses/{courseId}/enroll` - Enroll in a course (optionally specify batchId in request body)
- `GET /api/enrollments` - List my enrollments
- `GET /api/enrollments/{id}` - Get enrollment details
- `GET /api/courses/{courseId}/enrolled` - Check enrollment status
- `DELETE /api/courses/{courseId}/enroll` - Unenroll from course

**Batches:**
- `POST /api/batches` - Create a new batch for a course
- `GET /api/batches/{id}` - Get batch details
- `GET /api/batches/courses/{courseId}` - List all batches for a course
- `GET /api/batches/courses/{courseId}/active` - List active batches for a course
- `PUT /api/batches/{id}` - Update batch
- `DELETE /api/batches/{id}` - Deactivate batch
- `GET /api/batches/{id}/progress` - Get batch-level progress statistics

**Progress Tracking:**
- `PUT /api/courses/{courseId}/progress` - Update progress
- `GET /api/courses/{courseId}/progress` - Get course progress
- `GET /api/courses/{courseId}/lectures/progress` - Get lecture progress

**Assessment Submissions:**
- `POST /api/courses/{courseId}/assessments/submit` - Submit assessment
- `GET /api/courses/{courseId}/assessments/submissions` - List submissions
- `GET /api/assessments/{assessmentId}/submissions/latest` - Get latest submission

### Payment Service (via Gateway: `/payment/**` or `/api/payment/**`)

**Subscription Plans:**
- `GET /api/subscription-plans` - List active plans
- `GET /api/subscription-plans/{id}` - Get plan details
- `POST /api/subscription-plans` - Create plan (admin)
- `PUT /api/subscription-plans/{id}` - Update plan (admin)
- `DELETE /api/subscription-plans/{id}` - Deactivate plan (admin)

**Subscriptions:**
- `POST /api/subscriptions` - Create subscription
- `GET /api/subscriptions` - List my subscriptions
- `GET /api/subscriptions/active` - Get active subscription
- `POST /api/subscriptions/{id}/cancel` - Cancel subscription

**Payments:**
- `POST /api/payments` - Create payment
- `GET /api/payments` - List my payments
- `GET /api/payments/{id}` - Get payment details

**Webhooks:**
- `POST /api/webhooks/razorpay` - Razorpay webhook handler

## API Documentation

Each service provides Swagger/OpenAPI documentation:
- Identity: http://localhost:8081/swagger-ui.html
- Content: http://localhost:8082/swagger-ui.html
- Student: http://localhost:8083/swagger-ui.html
- Payment: http://localhost:8084/swagger-ui.html

## Service Integration

See [INTEGRATION.md](INTEGRATION.md) for details on how services integrate with each other.

## License

MIT License

