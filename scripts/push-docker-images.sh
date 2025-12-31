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
VERSIONS_FILE="versions.json"
SERVICES=("gateway" "identity" "content" "student" "payment")

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

# Function to push a service
push_service() {
    local service=$1
    local version=$(get_version "$service")
    local image_version="${REGISTRY}/${service}:${version}"
    local image_latest="${REGISTRY}/${service}:latest"
    
    echo -e "${GREEN}Pushing ${service} service...${NC}"
    echo -e "  ${BLUE}Version:${NC} ${version}"
    
    # Check if image exists
    if ! docker image inspect "$image_version" &>/dev/null; then
        echo -e "${RED}Error: Image ${image_version} not found locally${NC}"
        echo -e "${YELLOW}Build the image first: ./scripts/build-docker-images.sh ${service}${NC}"
        return 1
    fi
    
    # Push versioned image
    if docker push "$image_version"; then
        echo -e "${GREEN}✓ Pushed ${image_version}${NC}"
    else
        echo -e "${RED}✗ Failed to push ${image_version}${NC}"
        return 1
    fi
    
    # Push latest tag
    if docker push "$image_latest"; then
        echo -e "${GREEN}✓ Pushed ${image_latest}${NC}"
    else
        echo -e "${RED}✗ Failed to push ${image_latest}${NC}"
        return 1
    fi
    
    echo ""
    return 0
}

# Function to push all services
push_all() {
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Pushing all EduDron services${NC}"
    echo -e "${GREEN}========================================${NC}\n"
    echo -e "${BLUE}Registry:${NC} ${REGISTRY}\n"
    
    for service in "${SERVICES[@]}"; do
        push_service "$service"
    done
    
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Push completed!${NC}"
    echo -e "${GREEN}========================================${NC}\n"
}

# Main script
case "${1:-all}" in
    all)
        push_all
        ;;
    *)
        if [[ " ${SERVICES[@]} " =~ " ${1} " ]]; then
            push_service "$1"
        else
            echo -e "${RED}Unknown service: $1${NC}"
            echo -e "Available services: ${SERVICES[*]}"
            exit 1
        fi
        ;;
esac

