#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REGISTRY="${DOCKER_REGISTRY:-edudron}"
PUSH_IMAGES="${PUSH_IMAGES:-false}"
VERSIONS_FILE="versions.json"
SERVICES=("gateway" "identity" "content" "student" "payment")

# Check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}Error: Docker daemon is not running${NC}"
        exit 1
    fi
}

# Function to read version from versions.json
get_version() {
    local service=$1
    if [ -f "$VERSIONS_FILE" ]; then
        if command -v jq &> /dev/null; then
            jq -r ".\"${service}\"" "$VERSIONS_FILE" 2>/dev/null || echo "0.1.0"
        else
            grep -o "\"${service}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$VERSIONS_FILE" | sed -E 's/.*"([^"]+)"[[:space:]]*:[[:space:]]*"([^"]+)".*/\2/' || echo "0.1.0"
        fi
    else
        echo "0.1.0"
    fi
}

# Function to build a service
build_service() {
    local service=$1
    local dockerfile_path="${service}/Dockerfile"
    
    if [ ! -f "$dockerfile_path" ]; then
        echo -e "${YELLOW}Warning: Dockerfile not found at $dockerfile_path, skipping${NC}"
        return 0
    fi
    
    local version=$(get_version "$service")
    local git_commit=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
    local build_date=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    echo -e "${GREEN}Building ${service} service...${NC}"
    echo -e "  ${BLUE}Version:${NC} ${version}"
    
    docker build \
        --platform linux/amd64 \
        --build-arg VERSION="${version}" \
        --build-arg BUILD_DATE="${build_date}" \
        --build-arg GIT_COMMIT="${git_commit}" \
        --build-arg SERVICE_NAME="${service}" \
        -f "${dockerfile_path}" \
        -t "${REGISTRY}/${service}:${version}" \
        -t "${REGISTRY}/${service}:latest" \
        .
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully built ${service}:${version}${NC}\n"
        return 0
    else
        echo -e "${RED}✗ Failed to build ${service}${NC}\n"
        return 1
    fi
}

# Function to build all services
build_all() {
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Building all EduDron services${NC}"
    echo -e "${GREEN}========================================${NC}\n"
    
    check_docker
    
    local failed_services=()
    local success_count=0
    
    for service in "${SERVICES[@]}"; do
        if build_service "$service"; then
            ((success_count++))
        else
            failed_services+=("$service")
        fi
    done
    
    echo -e "${GREEN}========================================${NC}"
    if [ ${#failed_services[@]} -eq 0 ]; then
        echo -e "${GREEN}All services built successfully!${NC}"
    else
        echo -e "${YELLOW}Build completed with errors${NC}"
        echo -e "${RED}Failed services: ${failed_services[*]}${NC}"
    fi
    echo -e "${GREEN}========================================${NC}\n"
}

# Main script
case "${1:-all}" in
    all)
        build_all
        ;;
    *)
        if [[ " ${SERVICES[@]} " =~ " ${1} " ]]; then
            check_docker
            build_service "$1"
        else
            echo -e "${RED}Unknown service: $1${NC}"
            echo -e "Available services: ${SERVICES[*]}"
            exit 1
        fi
        ;;
esac

