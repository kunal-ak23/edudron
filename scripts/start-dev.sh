#!/bin/bash

# Start EduDron development environment
# This script starts the database and services using Docker Compose

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}🚀 Starting EduDron Development Environment${NC}"
echo ""

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Stop any existing containers
echo -e "${YELLOW}🛑 Stopping existing containers...${NC}"
docker-compose -f docker-compose.dev.yml down

# Start the services
echo -e "${YELLOW}🐳 Starting services with Docker Compose...${NC}"
docker-compose -f docker-compose.dev.yml up --build -d

# Wait for services to be ready
echo -e "${YELLOW}⏳ Waiting for services to be ready...${NC}"
sleep 10

# Check service health
echo -e "${YELLOW}🔍 Checking service health...${NC}"

# Note: PostgreSQL and Redis should be running locally
# Services connect via host.docker.internal
echo -e "${YELLOW}ℹ️  Ensure PostgreSQL and Redis are running locally${NC}"

# Wait a bit more for Spring Boot services
echo -e "${YELLOW}⏳ Waiting for Spring Boot services to start...${NC}"
sleep 30

# Check Identity Service
if curl -s http://localhost:8081/actuator/health >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Identity Service is ready${NC}"
else
    echo -e "${YELLOW}⏳ Identity Service is starting...${NC}"
fi

# Check Content Service
if curl -s http://localhost:8082/actuator/health >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Content Service is ready${NC}"
else
    echo -e "${YELLOW}⏳ Content Service is starting...${NC}"
fi

# Check Student Service
if curl -s http://localhost:8083/actuator/health >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Student Service is ready${NC}"
else
    echo -e "${YELLOW}⏳ Student Service is starting...${NC}"
fi

# Check Payment Service
if curl -s http://localhost:8084/actuator/health >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Payment Service is ready${NC}"
else
    echo -e "${YELLOW}⏳ Payment Service is starting...${NC}"
fi

# Check Gateway Service
if curl -s http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Gateway Service is ready${NC}"
else
    echo -e "${YELLOW}⏳ Gateway Service is starting...${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Development environment is starting up!${NC}"
echo ""
echo -e "${YELLOW}📋 Service URLs:${NC}"
echo "• Identity Service: http://localhost:8081"
echo "• Content Service: http://localhost:8082"
echo "• Student Service: http://localhost:8083"
echo "• Payment Service: http://localhost:8084"
echo "• Gateway Service: http://localhost:8080"
echo "• PostgreSQL: localhost:5432 (edudron) - must be running locally"
echo "• Redis: localhost:6379 - must be running locally"
echo ""
echo -e "${YELLOW}📚 API Documentation:${NC}"
echo "• Identity API: http://localhost:8081/swagger-ui.html"
echo "• Content API: http://localhost:8082/swagger-ui.html"
echo "• Student API: http://localhost:8083/swagger-ui.html"
echo "• Payment API: http://localhost:8084/swagger-ui.html"
echo ""
echo -e "${YELLOW}🛠️  Useful Commands:${NC}"
echo "• View logs: docker-compose -f docker-compose.dev.yml logs -f"
echo "• Stop services: docker-compose -f docker-compose.dev.yml down"
echo "• Create super admin: ./scripts/create-super-admin.sh"
echo ""
echo -e "${GREEN}Happy coding! 🚀${NC}"

