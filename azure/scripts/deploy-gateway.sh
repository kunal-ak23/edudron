#!/bin/bash

# Deploy Gateway Service to Azure Container Apps
# Usage: ./azure/scripts/deploy-gateway.sh --config <config-file-path>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Parse command line arguments
CONFIG_DIR="$SCRIPT_DIR/../config"
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

# Service configuration
SERVICE="gateway"
APP_NAME="${SERVICE}-${ENVIRONMENT}"
VERSIONS_FILE="$PROJECT_ROOT/versions.json"
SERVICES_CONFIG="$CONFIG_DIR/services-config.json"

# Get version
if [ ! -f "$VERSIONS_FILE" ]; then
    print_error "Versions file not found: $VERSIONS_FILE"
    exit 1
fi

VERSION=$(jq -r ".${SERVICE}" "$VERSIONS_FILE" 2>/dev/null || echo "0.1.0")
IMAGE="${CONTAINER_REGISTRY_SERVER}/edudron-${SERVICE}:${VERSION}"

# Get service config
PORT=$(jq -r ".services.${SERVICE}.port" "$SERVICES_CONFIG" 2>/dev/null || echo "8080")
CPU=$(jq -r ".services.${SERVICE}.cpu" "$SERVICES_CONFIG" 2>/dev/null || echo "1.0")
MEMORY=$(jq -r ".services.${SERVICE}.memory" "$SERVICES_CONFIG" 2>/dev/null || echo "2.0Gi")
MIN_REPLICAS=$(jq -r ".services.${SERVICE}.minReplicas" "$SERVICES_CONFIG" 2>/dev/null || echo "2")
MAX_REPLICAS=$(jq -r ".services.${SERVICE}.maxReplicas" "$SERVICES_CONFIG" 2>/dev/null || echo "10")
INGRESS_EXTERNAL=$(jq -r ".services.${SERVICE}.ingressExternal" "$SERVICES_CONFIG" 2>/dev/null || echo "true")

print_info "Deploying ${SERVICE} service"
print_info "  Version: ${VERSION}"
print_info "  Image: ${IMAGE}"
print_info "  App Name: ${APP_NAME}"
print_info "  Resource Group: ${RESOURCE_GROUP}"

# Get Container Apps Environment FQDN for service URLs
ENV_FQDN=$(az containerapp env show \
    --name "$ENVIRONMENT_NAME" \
    --resource-group "$RESOURCE_GROUP" \
    --query "properties.defaultDomain" -o tsv 2>/dev/null || echo "")

if [ -z "$ENV_FQDN" ]; then
    print_error "Could not get Container Apps Environment FQDN"
    exit 1
fi

# Build service URLs using internal FQDN format with HTTPS (port handled by ingress)
IDENTITY_URL="https://identity-${ENVIRONMENT}.internal.${ENV_FQDN}"
CONTENT_URL="https://content-${ENVIRONMENT}.internal.${ENV_FQDN}"
STUDENT_URL="https://student-${ENVIRONMENT}.internal.${ENV_FQDN}"
PAYMENT_URL="https://payment-${ENVIRONMENT}.internal.${ENV_FQDN}"

print_info "Service URLs:"
print_info "  IDENTITY_SERVICE_URL: ${IDENTITY_URL}"
print_info "  CONTENT_SERVICE_URL: ${CONTENT_URL}"
print_info "  STUDENT_SERVICE_URL: ${STUDENT_URL}"
print_info "  PAYMENT_SERVICE_URL: ${PAYMENT_URL}"

# Check if app exists
APP_EXISTS=$(az containerapp show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query "name" -o tsv 2>/dev/null || echo "")

if [ -z "$APP_EXISTS" ]; then
    print_info "Container app does not exist. Creating new app..."
    
    # Resolve environment ID
    print_info "Resolving Container Apps Environment..."
    ENVIRONMENT_ID=$(az containerapp env show \
        --name "$ENVIRONMENT_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --query "id" -o tsv 2>/dev/null || echo "")
    
    if [ -z "$ENVIRONMENT_ID" ]; then
        print_error "Container Apps Environment '$ENVIRONMENT_NAME' not found in resource group '$RESOURCE_GROUP'"
        exit 1
    fi
    
    # Create app first without environment variables
    print_info "Creating container app..."
    if ! az containerapp create \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --environment "$ENVIRONMENT_ID" \
        --image "$IMAGE" \
        --target-port "$PORT" \
        --ingress $([ "$INGRESS_EXTERNAL" = "true" ] && echo "external" || echo "internal") \
        --cpu "$CPU" \
        --memory "$MEMORY" \
        --min-replicas "$MIN_REPLICAS" \
        --max-replicas "$MAX_REPLICAS" \
        --registry-server docker.io \
        --registry-username "$CONTAINER_REGISTRY_USERNAME" \
        --registry-password "$CONTAINER_REGISTRY_PASSWORD" \
        --output none; then
        print_error "Failed to create container app"
        exit 1
    fi
    
    # Verify app was created
    sleep 2
    if ! az containerapp show --name "$APP_NAME" --resource-group "$RESOURCE_GROUP" --query "name" -o tsv &>/dev/null; then
        print_error "Container app was not created successfully"
        exit 1
    fi
    
    # Set environment variables after creation
    print_info "Setting environment variables..."
    az containerapp update \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --set-env-vars \
            "SPRING_PROFILES_ACTIVE=production" \
            "GATEWAY_CONNECT_TIMEOUT=30000" \
            "GATEWAY_RESPONSE_TIMEOUT=600s" \
            "IDENTITY_SERVICE_URL=$IDENTITY_URL" \
            "CONTENT_SERVICE_URL=$CONTENT_URL" \
            "STUDENT_SERVICE_URL=$STUDENT_URL" \
            "PAYMENT_SERVICE_URL=$PAYMENT_URL" \
        --output none
    
    print_success "Container app created successfully"
else
    print_info "Container app exists. Updating..."
    
    az containerapp update \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --image "$IMAGE" \
        --set-env-vars \
            "SPRING_PROFILES_ACTIVE=production" \
            "GATEWAY_CONNECT_TIMEOUT=30000" \
            "GATEWAY_RESPONSE_TIMEOUT=600s" \
            "IDENTITY_SERVICE_URL=$IDENTITY_URL" \
            "CONTENT_SERVICE_URL=$CONTENT_URL" \
            "STUDENT_SERVICE_URL=$STUDENT_URL" \
            "PAYMENT_SERVICE_URL=$PAYMENT_URL" \
        --output none
    
    print_success "Container app updated successfully"
fi

print_success "Deployment complete!"
print_info "Check logs: az containerapp logs show --name $APP_NAME --resource-group $RESOURCE_GROUP --follow"

