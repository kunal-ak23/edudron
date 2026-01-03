#!/bin/bash

# Build and push Docker images for Azure deployment
# Usage: ./azure/scripts/build-and-push-images.sh --config <config-file-path>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_DIR="$SCRIPT_DIR/../config"

# Parse command line arguments
ENV_FILE=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --config)
            if [[ "$2" == *.env ]]; then
                ENV_FILE="$2"
            else
                CONFIG_DIR="$2"
            fi
            shift 2
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
done

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Load environment variables
if [ -z "$ENV_FILE" ]; then
    ENV_FILE="$CONFIG_DIR/dev.env"
fi
if [ ! -f "$ENV_FILE" ]; then
    print_error "Environment file not found: $ENV_FILE"
    print_info "Copy dev.env.example to dev.env and update with your values"
    exit 1
fi

source "$ENV_FILE"

# Check if Docker Hub credentials are set
if [ "$CONTAINER_REGISTRY_SERVER" = "your-docker-hub-username" ] || [ -z "$CONTAINER_REGISTRY_SERVER" ]; then
    print_error "Docker Hub username not configured in $ENV_FILE"
    print_info "Please set CONTAINER_REGISTRY_SERVER to your Docker Hub username"
    exit 1
fi

if [ "$CONTAINER_REGISTRY_PASSWORD" = "your-docker-hub-token-or-password" ] || [ -z "$CONTAINER_REGISTRY_PASSWORD" ]; then
    print_error "Docker Hub password/token not configured in $ENV_FILE"
    print_info "Please set CONTAINER_REGISTRY_PASSWORD to your Docker Hub access token"
    exit 1
fi

# Check Docker is running
if ! docker info >/dev/null 2>&1; then
    print_error "Docker is not running"
    exit 1
fi

# Login to Docker Hub
print_info "Logging in to Docker Hub..."
echo "$CONTAINER_REGISTRY_PASSWORD" | docker login -u "$CONTAINER_REGISTRY_USERNAME" --password-stdin docker.io
if [ $? -eq 0 ]; then
    print_success "Logged in to Docker Hub"
else
    print_error "Failed to login to Docker Hub"
    exit 1
fi

# Change to project root
cd "$PROJECT_ROOT"

# Set registry for build scripts
export DOCKER_REGISTRY="$CONTAINER_REGISTRY_SERVER"

# Build all images
print_info "Building Docker images..."
print_info "Registry: $CONTAINER_REGISTRY_SERVER"
./scripts/build-docker-images.sh all

# Push all images
print_info "Pushing Docker images to Docker Hub..."
./scripts/push-docker-images.sh all

print_success "All images built and pushed successfully!"
print_info "Images are now available at:"
print_info "  docker.io/$CONTAINER_REGISTRY_SERVER/gateway:0.1.0"
print_info "  docker.io/$CONTAINER_REGISTRY_SERVER/identity:0.1.0"
print_info "  docker.io/$CONTAINER_REGISTRY_SERVER/content:0.1.0"
print_info "  docker.io/$CONTAINER_REGISTRY_SERVER/student:0.1.0"
print_info "  docker.io/$CONTAINER_REGISTRY_SERVER/payment:0.1.0"

