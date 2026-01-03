#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
REGISTRY="${DOCKER_REGISTRY:-kunalms}"
IMAGE_PREFIX="${IMAGE_PREFIX:-edudron-}"
PUSH_IMAGES="${PUSH_IMAGES:-true}"
VERSIONS_FILE="versions.json"
SERVICES=("gateway" "identity" "content" "student" "payment")

# Check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        echo -e "${RED}Error: Docker daemon is not running${NC}"
        echo -e "${YELLOW}Please start Docker Desktop or the Docker daemon and try again.${NC}"
        echo ""
        echo -e "${BLUE}To start Docker Desktop:${NC}"
        echo "  - macOS: Open Docker Desktop application"
        echo "  - Linux: Run 'sudo systemctl start docker'"
        echo "  - Windows: Open Docker Desktop application"
        exit 1
    fi
}

# Check if logged into Docker registry
check_docker_login() {
    # Extract registry host from REGISTRY
    local registry_host="$REGISTRY"
    if [[ "$REGISTRY" == *"/"* ]]; then
        registry_host=$(echo "$REGISTRY" | cut -d'/' -f1)
    fi
    
    # For Docker Hub (docker.io), check if credentials exist
    if [[ "$registry_host" == "docker.io" ]] || [[ "$registry_host" == *"docker.io"* ]]; then
        if [ ! -f "$HOME/.docker/config.json" ] || ! grep -q "auth" "$HOME/.docker/config.json" 2>/dev/null; then
            echo -e "${YELLOW}Warning: May not be logged into Docker Hub${NC}"
            echo -e "${BLUE}To login: docker login${NC}"
            echo -e "${YELLOW}Push will fail if not authenticated${NC}\n"
        fi
    fi
}

# Function to read version from versions.json
get_version() {
    local service=$1
    if [ -f "$VERSIONS_FILE" ]; then
        # Use jq if available, otherwise use grep/sed
        if command -v jq &> /dev/null; then
            jq -r ".\"${service}\"" "$VERSIONS_FILE" 2>/dev/null || echo "0.1.0"
        else
            grep -o "\"${service}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$VERSIONS_FILE" | sed -E 's/.*"([^"]+)"[[:space:]]*:[[:space:]]*"([^"]+)".*/\2/' || echo "0.1.0"
        fi
    else
        echo -e "${YELLOW}Warning: ${VERSIONS_FILE} not found, using default version 0.1.0${NC}"
        echo "0.1.0"
    fi
}

# Function to push a service image
push_service() {
    local service=$1
    local version=$2
    local image_name="${IMAGE_PREFIX}${service}"
    
    echo -e "${BLUE}Pushing ${image_name}:${version}...${NC}"
    
    # Push versioned tag
    if docker push "${REGISTRY}/${image_name}:${version}" 2>&1 | tee /tmp/docker_push.log; then
        echo -e "${GREEN}✓ Pushed ${REGISTRY}/${image_name}:${version}${NC}"
    else
        local push_error=$(cat /tmp/docker_push.log 2>/dev/null || echo "")
        echo -e "${RED}✗ Failed to push ${REGISTRY}/${image_name}:${version}${NC}"
        
        # Check for common errors
        if echo "$push_error" | grep -q "denied: requested access to the resource is denied"; then
            echo -e "${YELLOW}Authentication error detected.${NC}"
            echo -e "${BLUE}Possible solutions:${NC}"
            echo -e "  1. Login to Docker Hub: ${YELLOW}docker login${NC}"
            echo -e "  2. Use correct registry: ${YELLOW}DOCKER_REGISTRY=kunalms $0 ${service}${NC}"
            echo -e "  3. Check if registry/organization exists: ${YELLOW}${REGISTRY}${NC}"
        elif echo "$push_error" | grep -q "unauthorized"; then
            echo -e "${YELLOW}Unauthorized. Please login: ${BLUE}docker login${NC}"
        fi
        rm -f /tmp/docker_push.log
        return 1
    fi
    
    # Push latest tag
    if docker push "${REGISTRY}/${image_name}:latest" 2>&1 | tee /tmp/docker_push.log; then
        echo -e "${GREEN}✓ Pushed ${REGISTRY}/${image_name}:latest${NC}"
        rm -f /tmp/docker_push.log
    else
        local push_error=$(cat /tmp/docker_push.log 2>/dev/null || echo "")
        echo -e "${RED}✗ Failed to push ${REGISTRY}/${image_name}:latest${NC}"
        
        # Check for common errors
        if echo "$push_error" | grep -q "denied: requested access to the resource is denied"; then
            echo -e "${YELLOW}Authentication error detected.${NC}"
            echo -e "${BLUE}Possible solutions:${NC}"
            echo -e "  1. Login to Docker Hub: ${YELLOW}docker login${NC}"
            echo -e "  2. Use correct registry: ${YELLOW}DOCKER_REGISTRY=kunalms $0 ${service}${NC}"
        fi
        rm -f /tmp/docker_push.log
        return 1
    fi
    
    return 0
}

# Function to build a service
build_service() {
    local service=$1
    local dockerfile_path="${service}/Dockerfile"
    
    if [ ! -f "$dockerfile_path" ]; then
        echo -e "${RED}Error: Dockerfile not found at $dockerfile_path${NC}"
        return 1
    fi
    
    local version=$(get_version "$service")
    local git_commit=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
    local build_date=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local image_name="${IMAGE_PREFIX}${service}"
    
    echo -e "${GREEN}Building ${service} service...${NC}"
    echo -e "  ${BLUE}Version:${NC} ${version}"
    echo -e "  ${BLUE}Git Commit:${NC} ${git_commit}"
    echo -e "  ${BLUE}Build Date:${NC} ${build_date}"
    echo -e "  ${BLUE}Registry:${NC} ${REGISTRY}"
    echo -e "  ${BLUE}Image:${NC} ${REGISTRY}/${image_name}:${version}"
    if [ "$PUSH_IMAGES" = "true" ]; then
        echo -e "  ${BLUE}Will push to:${NC} ${REGISTRY}/${image_name}:${version} and ${REGISTRY}/${image_name}:latest"
    fi
    
    docker build \
        --platform linux/amd64 \
        --build-arg VERSION="${version}" \
        --build-arg BUILD_DATE="${build_date}" \
        --build-arg GIT_COMMIT="${git_commit}" \
        --build-arg SERVICE_NAME="${service}" \
        -f "${dockerfile_path}" \
        -t "${REGISTRY}/${image_name}:${version}" \
        -t "${REGISTRY}/${image_name}:latest" \
        .
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully built ${service}:${version}${NC}"
        
        # Push images if enabled
        if [ "$PUSH_IMAGES" = "true" ]; then
            echo ""
            if push_service "$service" "$version"; then
                echo -e "${GREEN}✓ Successfully pushed ${service}:${version}${NC}\n"
            else
                echo -e "${YELLOW}⚠ Build succeeded but push failed for ${service}${NC}\n"
                return 1
            fi
        else
            echo -e "${YELLOW}⚠ Push skipped (set PUSH_IMAGES=true to enable)${NC}\n"
        fi
        
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
    echo -e "${BLUE}Registry:${NC} ${REGISTRY}"
    echo -e "${BLUE}Image Prefix:${NC} ${IMAGE_PREFIX}"
    echo -e "${BLUE}Versions File:${NC} ${VERSIONS_FILE}"
    echo -e "${BLUE}Push Images:${NC} ${PUSH_IMAGES}\n"
    
    if [ "$PUSH_IMAGES" = "true" ]; then
        check_docker_login
    fi
    
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
        echo -e "${GREEN}Built: ${success_count}/${#SERVICES[@]} services${NC}"
    else
        echo -e "${YELLOW}Build completed with errors${NC}"
        echo -e "${GREEN}Success: ${success_count}/${#SERVICES[@]} services${NC}"
        echo -e "${RED}Failed: ${#failed_services[@]} services${NC}"
        echo -e "${RED}Failed services: ${failed_services[*]}${NC}"
    fi
    echo -e "${GREEN}========================================${NC}\n"
    
    if [ ${#failed_services[@]} -gt 0 ]; then
        return 1
    fi
}

# Function to show image info
show_image_info() {
    local service=$1
    local version=$(get_version "$service")
    local image_name="${IMAGE_PREFIX}${service}"
    local image="${REGISTRY}/${image_name}:${version}"
    
    if docker image inspect "$image" &>/dev/null; then
        echo -e "\n${GREEN}Image: ${image}${NC}"
        echo -e "${BLUE}Created:${NC} $(docker image inspect "$image" --format='{{.Created}}')"
        echo -e "${BLUE}Size:${NC} $(docker image inspect "$image" --format='{{.Size}}' | numfmt --to=iec-i --suffix=B 2>/dev/null || docker image inspect "$image" --format='{{.Size}}')"
        echo -e "${BLUE}Labels:${NC}"
        docker image inspect "$image" --format='{{range $k, $v := .Config.Labels}}{{printf "  %s=%s\n" $k $v}}{{end}}'
    else
        echo -e "${RED}Image ${image} not found${NC}"
        echo -e "${YELLOW}Build the image first: ./scripts/build-docker-images.sh ${service}${NC}"
        return 1
    fi
}

# Function to list all services
list_services() {
    echo -e "${GREEN}Available services:${NC}"
    for service in "${SERVICES[@]}"; do
        local version=$(get_version "$service")
        local dockerfile_path="${service}/Dockerfile"
        if [ -f "$dockerfile_path" ]; then
            echo -e "  ${GREEN}✓${NC} ${service} (version: ${version})"
        else
            echo -e "  ${RED}✗${NC} ${service} (Dockerfile missing)"
        fi
    done
}

# Function to show usage
show_usage() {
    echo -e "${GREEN}Usage:${NC} $0 [command] [service-name]"
    echo ""
    echo -e "${BLUE}Commands:${NC}"
    echo "  all              Build all services"
    echo "  <service-name>   Build specific service"
    echo "  list             List all available services"
    echo "  info <service>   Show image information"
    echo "  help             Show this help message"
    echo ""
    echo -e "${BLUE}Examples:${NC}"
    echo "  $0 all                    # Build all services"
    echo "  $0 gateway                # Build gateway service"
    echo "  $0 list                   # List all services"
    echo "  $0 info gateway           # Show gateway image info"
    echo ""
    echo -e "${BLUE}Environment Variables:${NC}"
    echo "  DOCKER_REGISTRY           Registry prefix (default: kunalms)"
    echo "  IMAGE_PREFIX               Image name prefix (default: edudron-)"
    echo "  PUSH_IMAGES               Push images after building (default: true)"
    echo ""
    echo -e "${BLUE}Examples with custom registry:${NC}"
    echo "  DOCKER_REGISTRY=myregistry $0 all"
    echo "  IMAGE_PREFIX=myapp- $0 all  # Use custom prefix"
    echo "  PUSH_IMAGES=false $0 all   # Build without pushing"
}

# Main script
case "${1:-help}" in
    all)
        check_docker
        if [ "$PUSH_IMAGES" = "true" ]; then
            check_docker_login
        fi
        build_all
        ;;
    list)
        list_services
        ;;
    info)
        if [ -z "$2" ]; then
            echo -e "${RED}Error: Service name required${NC}"
            echo -e "Usage: $0 info <service-name>"
            echo -e "Available services: ${SERVICES[*]}"
            exit 1
        fi
        if [[ ! " ${SERVICES[@]} " =~ " ${2} " ]]; then
            echo -e "${RED}Error: Unknown service: $2${NC}"
            echo -e "Available services: ${SERVICES[*]}"
            exit 1
        fi
        show_image_info "$2"
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        if [[ " ${SERVICES[@]} " =~ " ${1} " ]]; then
            check_docker
            if [ "$PUSH_IMAGES" = "true" ]; then
                check_docker_login
            fi
            build_service "$1"
        else
            echo -e "${RED}Error: Unknown command or service: $1${NC}"
            echo ""
            show_usage
            exit 1
        fi
        ;;
esac
