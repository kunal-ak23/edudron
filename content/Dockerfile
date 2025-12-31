# Multi-stage build with versioning support
ARG VERSION=latest
ARG BUILD_DATE
ARG GIT_COMMIT
ARG SERVICE_NAME=content

FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy gradle files
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/

# Copy source code
COPY content/ content/
COPY common/ common/

# Build the application
RUN gradle :content:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Add labels for versioning
ARG VERSION
ARG BUILD_DATE
ARG GIT_COMMIT
ARG SERVICE_NAME

LABEL org.opencontainers.image.title="EduDron ${SERVICE_NAME} Service" \
      org.opencontainers.image.description="EduDron ${SERVICE_NAME} microservice" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.vendor="DataGami" \
      com.datagami.edudron.service="${SERVICE_NAME}" \
      com.datagami.edudron.version="${VERSION}"

WORKDIR /app

# Copy the built JAR file from builder stage
COPY --from=builder /app/content/build/libs/content-*.jar app.jar

# Expose the port
EXPOSE 8082

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV APP_VERSION=${VERSION}
ENV BUILD_DATE=${BUILD_DATE}
ENV GIT_COMMIT=${GIT_COMMIT}

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

