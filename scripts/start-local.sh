#!/bin/bash

# Start EduDron locally (database in Docker, services locally)
# This is faster for development

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}🚀 Starting EduDron Local Development${NC}"
echo ""

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Note: This script assumes you have PostgreSQL and Redis running locally
# If you want to use Docker for databases, uncomment the lines below:
# docker-compose -f docker-compose.db-only.yml up -d
# sleep 10

echo -e "${YELLOW}ℹ️  Using local PostgreSQL and Redis instances${NC}"
echo -e "${YELLOW}   If you need Docker databases, use: docker-compose -f docker-compose.db-only.yml up -d${NC}"

# Check service health
echo -e "${YELLOW}🔍 Checking database health...${NC}"

# Note: For local development, PostgreSQL and Redis should be running locally
# This script uses docker-compose.db-only.yml only if you want Docker databases
# Otherwise, ensure your local PostgreSQL and Redis are running

# Check if using Docker databases
if docker ps | grep -q edudron-postgres-local; then
    # Check PostgreSQL in Docker
    if docker exec edudron-postgres-local pg_isready -U edudron -d edudron >/dev/null 2>&1; then
        echo -e "${GREEN}✅ PostgreSQL (Docker) is ready${NC}"
    else
        echo -e "${RED}❌ PostgreSQL (Docker) is not ready${NC}"
        exit 1
    fi
    
    # Check Redis in Docker
    if docker exec edudron-redis-local redis-cli ping >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Redis (Docker) is ready${NC}"
    else
        echo -e "${RED}❌ Redis (Docker) is not ready${NC}"
        exit 1
    fi
else
    echo -e "${YELLOW}ℹ️  Using local PostgreSQL and Redis instances${NC}"
    echo -e "${YELLOW}   Ensure they are running on localhost:5432 and localhost:6379${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Database services are ready!${NC}"
echo ""
echo -e "${YELLOW}📋 Database URLs:${NC}"
echo "• PostgreSQL: localhost:5432 (edudron) - ensure it's running locally"
echo "• Redis: localhost:6379 - ensure it's running locally"
echo ""
echo -e "${YELLOW}🚀 Now start your services locally:${NC}"
echo ""
echo "Option 1: Use the service management script:"
echo "   ./scripts/edudron.sh start"
echo ""
echo "Option 2: Start services individually:"
echo "   ./gradlew :identity:bootRun"
echo "   ./gradlew :content:bootRun"
echo "   ./gradlew :student:bootRun"
echo "   ./gradlew :payment:bootRun"
echo "   ./gradlew :gateway:bootRun"
echo ""
echo -e "${YELLOW}🛠️  Useful Commands:${NC}"
echo "• Stop database: docker-compose -f docker-compose.db-only.yml down"
echo "• View database logs: docker-compose -f docker-compose.db-only.yml logs -f"
echo "• Create super admin: ./scripts/create-super-admin.sh"
echo ""
echo -e "${GREEN}Happy coding! 🚀${NC}"

