#!/bin/bash

# Deploy Content Service to Azure Container Apps
# Usage: ./azure/scripts/deploy-content.sh --config <config-file-path>

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
SERVICE="content"
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
PORT=$(jq -r ".services.${SERVICE}.port" "$SERVICES_CONFIG" 2>/dev/null || echo "8082")
CPU=$(jq -r ".services.${SERVICE}.cpu" "$SERVICES_CONFIG" 2>/dev/null || echo "0.5")
MEMORY=$(jq -r ".services.${SERVICE}.memory" "$SERVICES_CONFIG" 2>/dev/null || echo "1.0Gi")
MIN_REPLICAS=$(jq -r ".services.${SERVICE}.minReplicas" "$SERVICES_CONFIG" 2>/dev/null || echo "1")
MAX_REPLICAS=$(jq -r ".services.${SERVICE}.maxReplicas" "$SERVICES_CONFIG" 2>/dev/null || echo "5")
INGRESS_EXTERNAL=$(jq -r ".services.${SERVICE}.ingressExternal" "$SERVICES_CONFIG" 2>/dev/null || echo "false")

print_info "Deploying ${SERVICE} service"
print_info "  Version: ${VERSION}"
print_info "  Image: ${IMAGE}"
print_info "  App Name: ${APP_NAME}"
print_info "  Resource Group: ${RESOURCE_GROUP}"

# Get Container Apps Environment FQDN for gateway URL
ENV_FQDN=$(az containerapp env show \
    --name "$ENVIRONMENT_NAME" \
    --resource-group "$RESOURCE_GROUP" \
    --query "properties.defaultDomain" -o tsv 2>/dev/null || echo "")

if [ -z "$ENV_FQDN" ]; then
    print_error "Could not get Container Apps Environment FQDN"
    exit 1
fi

# Build gateway URL using internal FQDN format with HTTPS
GATEWAY_URL="https://gateway-${ENVIRONMENT}.internal.${ENV_FQDN}"

print_info "Gateway URL: ${GATEWAY_URL}"

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
    
    # Create app with system-assigned managed identity
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
        --system-assigned \
        --output none; then
        print_error "Failed to create container app"
        exit 1
    fi
    
    sleep 2
    
    # Get principal ID after creation
    PRINCIPAL_ID=$(az containerapp show \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --query "identity.principalId" -o tsv 2>/dev/null || echo "")
    
    if [ -n "$PRINCIPAL_ID" ] && [ "$PRINCIPAL_ID" != "null" ]; then
        print_info "Granting Key Vault access to managed identity..."
        az keyvault set-policy \
            --name "$KEY_VAULT_NAME" \
            --object-id "$PRINCIPAL_ID" \
            --secret-permissions get list \
            --output none 2>/dev/null || print_warning "Could not set Key Vault policy"
    fi
    
    # Register secrets
    print_info "Registering Key Vault secrets..."
    az containerapp secret set \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --secrets \
            "db-host=keyvaultref:${KEY_VAULT_URL}/secrets/db-host,identityref:system" \
            "db-name=keyvaultref:${KEY_VAULT_URL}/secrets/db-name,identityref:system" \
            "db-username=keyvaultref:${KEY_VAULT_URL}/secrets/db-username,identityref:system" \
            "db-password=keyvaultref:${KEY_VAULT_URL}/secrets/db-password,identityref:system" \
            "redis-host=keyvaultref:${KEY_VAULT_URL}/secrets/redis-host,identityref:system" \
            "redis-port=keyvaultref:${KEY_VAULT_URL}/secrets/redis-port,identityref:system" \
            "redis-password=keyvaultref:${KEY_VAULT_URL}/secrets/redis-password,identityref:system" \
            "jwt-secret=keyvaultref:${KEY_VAULT_URL}/secrets/jwt-secret,identityref:system" \
            "appinsights-connection-string=keyvaultref:${KEY_VAULT_URL}/secrets/appinsights-connection-string,identityref:system" \
            "azure-openai-endpoint=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-ENDPOINT,identityref:system" \
            "azure-openai-api-key=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-API-KEY,identityref:system" \
            "azure-openai-deployment-name=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-DEPLOYMENT-NAME,identityref:system" \
            "azure-openai-api-version=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-API-VERSION,identityref:system" \
            "azure-storage-connection-string=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-CONNECTION-STRING,identityref:system" \
            "azure-storage-account-name=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-ACCOUNT-NAME,identityref:system" \
            "azure-storage-container-name=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-CONTAINER-NAME,identityref:system" \
            "azure-storage-base-url=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-BASE-URL,identityref:system" \
        --output none 2>/dev/null || print_warning "Some secrets may already be registered"
    
    # Set environment variables
    print_info "Setting environment variables..."
    az containerapp update \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --set-env-vars \
            "SPRING_PROFILES_ACTIVE=production" \
            "CONTENT_SERVICE_PORT=${PORT}" \
            "GATEWAY_URL=$GATEWAY_URL" \
            "REDIS_SSL=true" \
            "DB_HOST=secretref:db-host" \
            "DB_PORT=5432" \
            "DB_NAME=secretref:db-name" \
            "DB_USERNAME=secretref:db-username" \
            "DB_PASSWORD=secretref:db-password" \
            "DB_SSLMODE=require" \
            "REDIS_HOST=secretref:redis-host" \
            "REDIS_PORT=secretref:redis-port" \
            "REDIS_PASSWORD=secretref:redis-password" \
            "JWT_SECRET=secretref:jwt-secret" \
            "APPLICATIONINSIGHTS_CONNECTION_STRING=secretref:appinsights-connection-string" \
            "AZURE_OPENAI_ENDPOINT=secretref:azure-openai-endpoint" \
            "AZURE_OPENAI_API_KEY=secretref:azure-openai-api-key" \
            "AZURE_OPENAI_DEPLOYMENT_NAME=secretref:azure-openai-deployment-name" \
            "AZURE_OPENAI_API_VERSION=secretref:azure-openai-api-version" \
            "AZURE_STORAGE_CONNECTION_STRING=secretref:azure-storage-connection-string" \
            "AZURE_STORAGE_ACCOUNT_NAME=secretref:azure-storage-account-name" \
            "AZURE_STORAGE_CONTAINER_NAME=secretref:azure-storage-container-name" \
            "AZURE_STORAGE_BASE_URL=secretref:azure-storage-base-url" \
        --output none
    
    print_success "Container app created successfully"
else
    print_info "Container app exists. Updating..."
    
    # Ensure managed identity exists and has Key Vault access
    PRINCIPAL_ID=$(az containerapp show \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --query "identity.principalId" -o tsv 2>/dev/null || echo "")
    
    if [ -z "$PRINCIPAL_ID" ] || [ "$PRINCIPAL_ID" = "null" ]; then
        print_info "Enabling system-assigned managed identity..."
        az containerapp identity assign \
            --name "$APP_NAME" \
            --resource-group "$RESOURCE_GROUP" \
            --system-assigned \
            --output none
        
        PRINCIPAL_ID=$(az containerapp show \
            --name "$APP_NAME" \
            --resource-group "$RESOURCE_GROUP" \
            --query "identity.principalId" -o tsv 2>/dev/null || echo "")
    fi
    
    if [ -n "$PRINCIPAL_ID" ] && [ "$PRINCIPAL_ID" != "null" ]; then
        print_info "Granting Key Vault access to managed identity..."
        az keyvault set-policy \
            --name "$KEY_VAULT_NAME" \
            --object-id "$PRINCIPAL_ID" \
            --secret-permissions get list \
            --output none 2>/dev/null || print_warning "Could not set Key Vault policy"
    fi
    
    # Register secrets (if not already registered)
    print_info "Registering Key Vault secrets..."
    az containerapp secret set \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --secrets \
            "db-host=keyvaultref:${KEY_VAULT_URL}/secrets/db-host,identityref:system" \
            "db-name=keyvaultref:${KEY_VAULT_URL}/secrets/db-name,identityref:system" \
            "db-username=keyvaultref:${KEY_VAULT_URL}/secrets/db-username,identityref:system" \
            "db-password=keyvaultref:${KEY_VAULT_URL}/secrets/db-password,identityref:system" \
            "redis-host=keyvaultref:${KEY_VAULT_URL}/secrets/redis-host,identityref:system" \
            "redis-port=keyvaultref:${KEY_VAULT_URL}/secrets/redis-port,identityref:system" \
            "redis-password=keyvaultref:${KEY_VAULT_URL}/secrets/redis-password,identityref:system" \
            "jwt-secret=keyvaultref:${KEY_VAULT_URL}/secrets/jwt-secret,identityref:system" \
            "appinsights-connection-string=keyvaultref:${KEY_VAULT_URL}/secrets/appinsights-connection-string,identityref:system" \
            "azure-openai-endpoint=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-ENDPOINT,identityref:system" \
            "azure-openai-api-key=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-API-KEY,identityref:system" \
            "azure-openai-deployment-name=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-DEPLOYMENT-NAME,identityref:system" \
            "azure-openai-api-version=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-OPENAI-API-VERSION,identityref:system" \
            "azure-storage-connection-string=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-CONNECTION-STRING,identityref:system" \
            "azure-storage-account-name=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-ACCOUNT-NAME,identityref:system" \
            "azure-storage-container-name=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-CONTAINER-NAME,identityref:system" \
            "azure-storage-base-url=keyvaultref:${KEY_VAULT_URL}/secrets/AZURE-STORAGE-BASE-URL,identityref:system" \
        --output none 2>/dev/null || print_warning "Some secrets may already be registered"
    
    # Update image and environment variables
    az containerapp update \
        --name "$APP_NAME" \
        --resource-group "$RESOURCE_GROUP" \
        --image "$IMAGE" \
        --set-env-vars \
            "SPRING_PROFILES_ACTIVE=production" \
            "CONTENT_SERVICE_PORT=${PORT}" \
            "GATEWAY_URL=$GATEWAY_URL" \
            "REDIS_SSL=true" \
            "DB_HOST=secretref:db-host" \
            "DB_PORT=5432" \
            "DB_NAME=secretref:db-name" \
            "DB_USERNAME=secretref:db-username" \
            "DB_PASSWORD=secretref:db-password" \
            "DB_SSLMODE=require" \
            "REDIS_HOST=secretref:redis-host" \
            "REDIS_PORT=secretref:redis-port" \
            "REDIS_PASSWORD=secretref:redis-password" \
            "JWT_SECRET=secretref:jwt-secret" \
            "APPLICATIONINSIGHTS_CONNECTION_STRING=secretref:appinsights-connection-string" \
            "AZURE_OPENAI_ENDPOINT=secretref:azure-openai-endpoint" \
            "AZURE_OPENAI_API_KEY=secretref:azure-openai-api-key" \
            "AZURE_OPENAI_DEPLOYMENT_NAME=secretref:azure-openai-deployment-name" \
            "AZURE_OPENAI_API_VERSION=secretref:azure-openai-api-version" \
            "AZURE_STORAGE_CONNECTION_STRING=secretref:azure-storage-connection-string" \
            "AZURE_STORAGE_ACCOUNT_NAME=secretref:azure-storage-account-name" \
            "AZURE_STORAGE_CONTAINER_NAME=secretref:azure-storage-container-name" \
            "AZURE_STORAGE_BASE_URL=secretref:azure-storage-base-url" \
        --output none
    
    print_success "Container app updated successfully"
fi

print_success "Deployment complete!"
print_info "Check logs: az containerapp logs show --name $APP_NAME --resource-group $RESOURCE_GROUP --follow"

