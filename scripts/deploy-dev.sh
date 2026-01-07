#!/bin/bash

# Deploy EduDron Development Environment
# This script provides an easy way to deploy the full dev environment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  EduDron Dev Deployment Script       ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "build.gradle" ]; then
    echo -e "${RED}❌ Please run this script from the root of the EduDron project${NC}"
    exit 1
fi

# Function to check service health
check_service() {
    local service_name=$1
    local url=$2
    local max_attempts=30
    local attempt=0
    
    echo -e "${YELLOW}⏳ Waiting for ${service_name}...${NC}"
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f "$url" >/dev/null 2>&1; then
            echo -e "${GREEN}✅ ${service_name} is ready${NC}"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done
    
    echo -e "${YELLOW}⚠️  ${service_name} may still be starting (checked ${max_attempts} times)${NC}"
    return 1
}

# Ask user which deployment option
echo -e "${YELLOW}Select deployment option:${NC}"
echo "1) Full Docker deployment (database + services) - Recommended"
echo "2) Services only (assumes DB/Redis running locally)"
echo "3) Database only (for local service development)"
read -p "Enter choice [1-3]: " choice

case $choice in
    1)
        echo -e "${YELLOW}🚀 Starting full Docker deployment...${NC}"
        echo ""
        
        # Stop any existing containers
        echo -e "${YELLOW}🛑 Stopping existing containers...${NC}"
        docker-compose -f docker-compose.dev-full.yml down 2>/dev/null || true
        
        # Start all services
        echo -e "${YELLOW}🐳 Starting all services...${NC}"
        docker-compose -f docker-compose.dev-full.yml up --build -d
        
        # Wait for database to be ready
        echo -e "${YELLOW}⏳ Waiting for database to be ready...${NC}"
        sleep 10
        
        # Check services
        echo ""
        echo -e "${YELLOW}🔍 Checking service health...${NC}"
        check_service "PostgreSQL" "http://localhost:5432" || true
        check_service "Identity Service" "http://localhost:8081/actuator/health"
        check_service "Content Service" "http://localhost:8082/actuator/health"
        check_service "Student Service" "http://localhost:8083/actuator/health"
        check_service "Payment Service" "http://localhost:8084/actuator/health"
        check_service "Gateway Service" "http://localhost:8080/actuator/health"
        ;;
        
    2)
        echo -e "${YELLOW}🚀 Starting services only...${NC}"
        echo ""
        
        # Check if database is accessible
        echo -e "${YELLOW}🔍 Checking database connection...${NC}"
        if ! docker exec edudron-postgres-local pg_isready -U edudron >/dev/null 2>&1 && \
           ! pg_isready -h localhost -U edudron -d edudron >/dev/null 2>&1; then
            echo -e "${RED}❌ Database is not accessible. Please start database first:${NC}"
            echo "   docker-compose -f docker-compose.db-only.yml up -d"
            exit 1
        fi
        
        # Stop existing service containers
        echo -e "${YELLOW}🛑 Stopping existing service containers...${NC}"
        docker-compose -f docker-compose.dev.yml down 2>/dev/null || true
        
        # Start services
        echo -e "${YELLOW}🐳 Starting services...${NC}"
        docker-compose -f docker-compose.dev.yml up --build -d
        
        # Wait and check
        sleep 15
        echo ""
        echo -e "${YELLOW}🔍 Checking service health...${NC}"
        check_service "Identity Service" "http://localhost:8081/actuator/health"
        check_service "Content Service" "http://localhost:8082/actuator/health"
        check_service "Student Service" "http://localhost:8083/actuator/health"
        check_service "Payment Service" "http://localhost:8084/actuator/health"
        check_service "Gateway Service" "http://localhost:8080/actuator/health"
        ;;
        
    3)
        echo -e "${YELLOW}🚀 Starting database and Redis only...${NC}"
        echo ""
        
        # Stop existing containers
        docker-compose -f docker-compose.db-only.yml down 2>/dev/null || true
        
        # Start database and Redis
        docker-compose -f docker-compose.db-only.yml up -d
        
        echo -e "${GREEN}✅ Database and Redis started${NC}"
        echo ""
        echo -e "${YELLOW}📋 Connection details:${NC}"
        echo "   PostgreSQL: localhost:5432"
        echo "   Database: edudron"
        echo "   Username: edudron"
        echo "   Password: edudron"
        echo "   Redis: localhost:6379"
        ;;
        
    *)
        echo -e "${RED}❌ Invalid choice${NC}"
        exit 1
        ;;
esac

# Display service information
echo ""
echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Deployment Complete!                  ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
echo ""

if [ "$choice" != "3" ]; then
    echo -e "${YELLOW}📋 Service URLs:${NC}"
    echo "   • Gateway:      http://localhost:8080"
    echo "   • Identity:     http://localhost:8081"
    echo "   • Content:      http://localhost:8082"
    echo "   • Student:      http://localhost:8083"
    echo "   • Payment:      http://localhost:8084"
    echo ""
    echo -e "${YELLOW}📚 API Documentation:${NC}"
    echo "   • Identity API:  http://localhost:8081/swagger-ui.html"
    echo "   • Content API: http://localhost:8082/swagger-ui.html"
    echo "   • Student API:  http://localhost:8083/swagger-ui.html"
    echo "   • Payment API:  http://localhost:8084/swagger-ui.html"
    echo ""
fi

echo -e "${YELLOW}🛠️  Useful Commands:${NC}"
if [ "$choice" = "1" ]; then
    echo "   • View logs:     docker-compose -f docker-compose.dev-full.yml logs -f"
    echo "   • Stop all:      docker-compose -f docker-compose.dev-full.yml down"
    echo "   • Restart:       docker-compose -f docker-compose.dev-full.yml restart"
elif [ "$choice" = "2" ]; then
    echo "   • View logs:     docker-compose -f docker-compose.dev.yml logs -f"
    echo "   • Stop services: docker-compose -f docker-compose.dev.yml down"
    echo "   • Restart:       docker-compose -f docker-compose.dev.yml restart"
else
    echo "   • View logs:     docker-compose -f docker-compose.db-only.yml logs -f"
    echo "   • Stop:          docker-compose -f docker-compose.db-only.yml down"
fi

if [ "$choice" != "3" ]; then
    echo "   • Create admin:  ./scripts/create-super-admin.sh"
fi

echo ""
echo -e "${GREEN}Happy coding! 🚀${NC}"


