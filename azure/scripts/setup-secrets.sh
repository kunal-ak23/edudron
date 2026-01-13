#!/bin/bash

# Setup secrets in Azure Key Vault for EduDron
# Usage: ./azure/scripts/setup-secrets.sh --config <config-file-path>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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

# Check prerequisites
if ! command -v az &> /dev/null; then
    print_error "Azure CLI is not installed"
    exit 1
fi

if ! az account show &> /dev/null; then
    print_error "Not logged in to Azure. Please run 'az login'"
    exit 1
fi

print_info "Setting up secrets in Key Vault: $KEY_VAULT_NAME"
print_info "Resource Group: $RESOURCE_GROUP"

# Check if Key Vault exists
if ! az keyvault show --name "$KEY_VAULT_NAME" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
    print_error "Key Vault '$KEY_VAULT_NAME' not found in resource group '$RESOURCE_GROUP'"
    print_info "Please deploy infrastructure first using deploy-infrastructure.sh"
    exit 1
fi

# Function to grant Key Vault access to current user
grant_key_vault_access() {
    print_info "Granting Key Vault access to current user..."
    
    # Try multiple methods to get user identity
    local current_user_object_id=""
    local current_user_upn=""
    
    # Method 1: Get object ID from signed-in user
    current_user_object_id=$(az ad signed-in-user show --query id -o tsv 2>/dev/null)
    
    # Method 2: Get UPN (User Principal Name) as fallback
    if [ -z "$current_user_object_id" ]; then
        current_user_upn=$(az account show --query user.name -o tsv 2>/dev/null)
    fi
    
    # Grant access using object ID (preferred)
    if [ -n "$current_user_object_id" ]; then
        if az keyvault set-policy \
            --name "$KEY_VAULT_NAME" \
            --object-id "$current_user_object_id" \
            --secret-permissions get list set delete \
            --output none 2>/dev/null; then
            print_success "Key Vault access granted (using object ID)"
            return 0
        fi
    fi
    
    # Fallback: Grant access using UPN
    if [ -n "$current_user_upn" ]; then
        if az keyvault set-policy \
            --name "$KEY_VAULT_NAME" \
            --upn "$current_user_upn" \
            --secret-permissions get list set delete \
            --output none 2>/dev/null; then
            print_success "Key Vault access granted (using UPN)"
            return 0
        fi
    fi
    
    # Last resort: Try with current account
    local account_name=$(az account show --query user.name -o tsv 2>/dev/null)
    if [ -n "$account_name" ]; then
        if az keyvault set-policy \
            --name "$KEY_VAULT_NAME" \
            --upn "$account_name" \
            --secret-permissions get list set delete \
            --output none 2>/dev/null; then
            print_success "Key Vault access granted"
            return 0
        fi
    fi
    
    print_warning "Could not automatically grant Key Vault access"
    print_info "Please grant access manually using one of these commands:"
    echo ""
    if [ -n "$current_user_object_id" ]; then
        echo "  az keyvault set-policy --name $KEY_VAULT_NAME --object-id $current_user_object_id --secret-permissions get list set delete"
    fi
    if [ -n "$current_user_upn" ]; then
        echo "  az keyvault set-policy --name $KEY_VAULT_NAME --upn $current_user_upn --secret-permissions get list set delete"
    fi
    if [ -n "$account_name" ]; then
        echo "  az keyvault set-policy --name $KEY_VAULT_NAME --upn $account_name --secret-permissions get list set delete"
    fi
    echo ""
    return 1
}

# Grant access before proceeding
if ! grant_key_vault_access; then
    print_error "Cannot proceed without Key Vault access. Please grant access manually and try again."
    exit 1
fi

# Function to set secret
set_secret() {
    local secret_name=$1
    local secret_value=$2
    local prompt_message=$3
    
    if [ -z "$secret_value" ]; then
        read -sp "$prompt_message: " secret_value
        echo ""
    fi
    
    # Strip leading/trailing whitespace and newlines
    # Use printf to preserve the value exactly and remove newlines/carriage returns
    secret_value=$(printf '%s' "$secret_value" | tr -d '\n\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    
    if [ -n "$secret_value" ]; then
        print_info "Setting secret: $secret_name"
        # Value has already been stripped of newlines above
        az keyvault secret set \
            --vault-name "$KEY_VAULT_NAME" \
            --name "$secret_name" \
            --value "$secret_value" \
            --output none
        print_success "Secret '$secret_name' set successfully"
    else
        print_warning "Skipping secret '$secret_name' (empty value)"
    fi
}

# Get database connection details
print_info "Configuring database secrets..."
read -p "PostgreSQL host (or press Enter to auto-detect): " DB_HOST_INPUT
if [ -z "$DB_HOST_INPUT" ]; then
    DB_HOST=$(az postgres flexible-server list --resource-group "$RESOURCE_GROUP" --query "[0].fullyQualifiedDomainName" -o tsv 2>/dev/null || echo "")
    if [ -z "$DB_HOST" ]; then
        read -p "PostgreSQL host: " DB_HOST
    fi
else
    DB_HOST="$DB_HOST_INPUT"
fi

read -p "Database name [edudron]: " DB_NAME
DB_NAME="${DB_NAME:-edudron}"

read -p "Database username [edudron]: " DB_USERNAME
DB_USERNAME="${DB_USERNAME:-edudron}"

read -sp "Database password: " DB_PASSWORD
echo ""
# Strip newlines from password
DB_PASSWORD=$(echo -n "$DB_PASSWORD" | tr -d '\n\r')

# Set database secrets
set_secret "db-host" "$DB_HOST" ""
set_secret "db-name" "$DB_NAME" ""
set_secret "db-username" "$DB_USERNAME" ""
set_secret "db-password" "$DB_PASSWORD" ""

# Get Redis connection details
print_info "Configuring Redis secrets..."
read -p "Redis host (or press Enter to auto-detect): " REDIS_HOST_INPUT
if [ -z "$REDIS_HOST_INPUT" ]; then
    REDIS_HOST=$(az redis list --resource-group "$RESOURCE_GROUP" --query "[0].hostName" -o tsv 2>/dev/null || echo "")
    if [ -z "$REDIS_HOST" ]; then
        read -p "Redis host: " REDIS_HOST
    fi
else
    REDIS_HOST="$REDIS_HOST_INPUT"
fi

read -p "Redis port [6380]: " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6380}"

read -sp "Redis password (or press Enter if none): " REDIS_PASSWORD
echo ""
# Strip newlines from password
REDIS_PASSWORD=$(echo -n "$REDIS_PASSWORD" | tr -d '\n\r')

# Set Redis secrets
set_secret "redis-host" "$REDIS_HOST" ""
set_secret "redis-port" "$REDIS_PORT" ""
set_secret "redis-password" "$REDIS_PASSWORD" ""

# JWT Secret
print_info "Configuring JWT secret..."
read -sp "JWT secret (or press Enter to generate): " JWT_SECRET
echo ""
if [ -z "$JWT_SECRET" ]; then
    JWT_SECRET=$(openssl rand -base64 32 | tr -d '\n\r')
    print_info "Generated JWT secret"
else
    # Strip newlines from manually entered JWT secret
    JWT_SECRET=$(echo -n "$JWT_SECRET" | tr -d '\n\r')
fi
set_secret "jwt-secret" "$JWT_SECRET" ""

# Application Insights
print_info "Configuring Application Insights..."
APPINSIGHTS_CONNECTION_STRING=$(az monitor app-insights component show \
    --app "$(az monitor app-insights component list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv 2>/dev/null || echo "")" \
    --resource-group "$RESOURCE_GROUP" \
    --query "connectionString" -o tsv 2>/dev/null || echo "")

if [ -z "$APPINSIGHTS_CONNECTION_STRING" ]; then
    read -p "Application Insights connection string: " APPINSIGHTS_CONNECTION_STRING
fi

if [ -n "$APPINSIGHTS_CONNECTION_STRING" ]; then
    set_secret "appinsights-connection-string" "$APPINSIGHTS_CONNECTION_STRING" ""
fi

# Azure Storage Configuration
print_info "Configuring Azure Storage secrets..."

# Try to auto-detect storage account from infrastructure deployment
print_info "Attempting to auto-detect Azure Storage Account from infrastructure..."
STORAGE_ACCOUNT=$(az storage account list --resource-group "$RESOURCE_GROUP" --query "[0].name" -o tsv 2>/dev/null || echo "")

# Check if values are already exported from dev.env
if [ -n "$AZURE_STORAGE_CONNECTION_STRING" ]; then
    print_info "Using Azure Storage Connection String from dev.env"
    AZURE_STORAGE_CONNECTION_STRING_INPUT="$AZURE_STORAGE_CONNECTION_STRING"
elif [ -n "$STORAGE_ACCOUNT" ]; then
    # Auto-fetch connection string from infrastructure-deployed storage account
    print_info "Found storage account from infrastructure: $STORAGE_ACCOUNT"
    print_info "Auto-fetching connection string..."
    AZURE_STORAGE_CONNECTION_STRING_INPUT=$(az storage account show-connection-string \
        --name "$STORAGE_ACCOUNT" \
        --resource-group "$RESOURCE_GROUP" \
        --query "connectionString" -o tsv 2>/dev/null || echo "")
    
    if [ -n "$AZURE_STORAGE_CONNECTION_STRING_INPUT" ]; then
        print_success "Successfully retrieved connection string from storage account"
    else
        print_warning "Could not retrieve connection string automatically"
        # Try to get from file
        AZURE_STORAGE_CONNECTION_STRING_INPUT=$(grep "^export AZURE_STORAGE_CONNECTION_STRING=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
            grep "^AZURE_STORAGE_CONNECTION_STRING=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "")
        
        if [ -z "$AZURE_STORAGE_CONNECTION_STRING_INPUT" ]; then
            read -p "Azure Storage Connection String (or press Enter to skip): " AZURE_STORAGE_CONNECTION_STRING_INPUT
        fi
    fi
else
    # Try to get from file
    AZURE_STORAGE_CONNECTION_STRING_INPUT=$(grep "^export AZURE_STORAGE_CONNECTION_STRING=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
        grep "^AZURE_STORAGE_CONNECTION_STRING=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "")
    
    if [ -z "$AZURE_STORAGE_CONNECTION_STRING_INPUT" ]; then
        print_warning "No storage account found in resource group. You may need to deploy infrastructure first."
        read -p "Azure Storage Connection String (or press Enter to skip): " AZURE_STORAGE_CONNECTION_STRING_INPUT
    fi
fi

# Get storage account name (for managed identity option)
if [ -n "$AZURE_STORAGE_ACCOUNT_NAME" ]; then
    AZURE_STORAGE_ACCOUNT_NAME_INPUT="$AZURE_STORAGE_ACCOUNT_NAME"
    print_info "Using storage account name from dev.env: $AZURE_STORAGE_ACCOUNT_NAME_INPUT"
elif [ -n "$STORAGE_ACCOUNT" ]; then
    AZURE_STORAGE_ACCOUNT_NAME_INPUT="$STORAGE_ACCOUNT"
    print_info "Auto-detected storage account name from infrastructure: $AZURE_STORAGE_ACCOUNT_NAME_INPUT"
else
    AZURE_STORAGE_ACCOUNT_NAME_INPUT=$(grep "^export AZURE_STORAGE_ACCOUNT_NAME=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
        grep "^AZURE_STORAGE_ACCOUNT_NAME=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "")
    
    if [ -z "$AZURE_STORAGE_ACCOUNT_NAME_INPUT" ]; then
        read -p "Azure Storage Account Name (or press Enter to skip): " AZURE_STORAGE_ACCOUNT_NAME_INPUT
    fi
fi

# Get container name (defaults to infrastructure value)
if [ -n "$AZURE_STORAGE_CONTAINER_NAME" ]; then
    AZURE_STORAGE_CONTAINER_NAME_INPUT="$AZURE_STORAGE_CONTAINER_NAME"
    print_info "Using container name from dev.env: $AZURE_STORAGE_CONTAINER_NAME_INPUT"
else
    AZURE_STORAGE_CONTAINER_NAME_INPUT=$(grep "^export AZURE_STORAGE_CONTAINER_NAME=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
        grep "^AZURE_STORAGE_CONTAINER_NAME=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "edudron-media")
    
    if [ "$AZURE_STORAGE_CONTAINER_NAME_INPUT" = "edudron-media" ]; then
        print_info "Using default container name from infrastructure: $AZURE_STORAGE_CONTAINER_NAME_INPUT"
    else
        read -p "Azure Storage Container Name [${AZURE_STORAGE_CONTAINER_NAME_INPUT}]: " AZURE_STORAGE_CONTAINER_NAME_INPUT_OVERRIDE
        AZURE_STORAGE_CONTAINER_NAME_INPUT="${AZURE_STORAGE_CONTAINER_NAME_INPUT_OVERRIDE:-${AZURE_STORAGE_CONTAINER_NAME_INPUT}}"
    fi
fi

# Get base URL (auto-generate from account name)
if [ -n "$AZURE_STORAGE_BASE_URL" ]; then
    AZURE_STORAGE_BASE_URL_INPUT="$AZURE_STORAGE_BASE_URL"
    print_info "Using base URL from dev.env: $AZURE_STORAGE_BASE_URL_INPUT"
elif [ -n "$AZURE_STORAGE_ACCOUNT_NAME_INPUT" ]; then
    AZURE_STORAGE_BASE_URL_INPUT="https://${AZURE_STORAGE_ACCOUNT_NAME_INPUT}.blob.core.windows.net"
    print_info "Auto-generated base URL from account name: $AZURE_STORAGE_BASE_URL_INPUT"
else
    AZURE_STORAGE_BASE_URL_INPUT=$(grep "^export AZURE_STORAGE_BASE_URL=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
        grep "^AZURE_STORAGE_BASE_URL=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "")
    
    if [ -z "$AZURE_STORAGE_BASE_URL_INPUT" ]; then
        read -p "Azure Storage Base URL (or press Enter to skip): " AZURE_STORAGE_BASE_URL_INPUT
    fi
fi

# Strip newlines from values
AZURE_STORAGE_CONNECTION_STRING_INPUT=$(echo -n "$AZURE_STORAGE_CONNECTION_STRING_INPUT" | tr -d '\n\r')
AZURE_STORAGE_ACCOUNT_NAME_INPUT=$(echo -n "$AZURE_STORAGE_ACCOUNT_NAME_INPUT" | tr -d '\n\r')

# Set Azure Storage secrets (only if values are provided)
if [ -n "$AZURE_STORAGE_CONNECTION_STRING_INPUT" ]; then
    set_secret "AZURE-STORAGE-CONNECTION-STRING" "$AZURE_STORAGE_CONNECTION_STRING_INPUT" ""
fi

if [ -n "$AZURE_STORAGE_ACCOUNT_NAME_INPUT" ]; then
    set_secret "AZURE-STORAGE-ACCOUNT-NAME" "$AZURE_STORAGE_ACCOUNT_NAME_INPUT" ""
fi

if [ -n "$AZURE_STORAGE_CONTAINER_NAME_INPUT" ]; then
    set_secret "AZURE-STORAGE-CONTAINER-NAME" "$AZURE_STORAGE_CONTAINER_NAME_INPUT" ""
fi

if [ -n "$AZURE_STORAGE_BASE_URL_INPUT" ]; then
    set_secret "AZURE-STORAGE-BASE-URL" "$AZURE_STORAGE_BASE_URL_INPUT" ""
fi

# Azure OpenAI Configuration
print_info "Configuring Azure OpenAI secrets..."

# Check if values are already exported from dev.env
if [ -n "$AZURE_OPENAI_ENDPOINT" ]; then
    print_info "Using Azure OpenAI Endpoint from dev.env"
    AZURE_OPENAI_ENDPOINT_INPUT="$AZURE_OPENAI_ENDPOINT"
else
    read -p "Azure OpenAI Endpoint (or press Enter to read from dev.env): " AZURE_OPENAI_ENDPOINT_INPUT
    if [ -z "$AZURE_OPENAI_ENDPOINT_INPUT" ]; then
        # Try to get from file (handles both exported and non-exported)
        AZURE_OPENAI_ENDPOINT_INPUT=$(grep "^export AZURE_OPENAI_ENDPOINT=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
            grep "^AZURE_OPENAI_ENDPOINT=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "")
        if [ -z "$AZURE_OPENAI_ENDPOINT_INPUT" ]; then
            read -p "Azure OpenAI Endpoint: " AZURE_OPENAI_ENDPOINT_INPUT
        fi
    fi
fi

if [ -n "$AZURE_OPENAI_API_KEY" ]; then
    print_info "Using Azure OpenAI API Key from dev.env"
    AZURE_OPENAI_API_KEY_INPUT="$AZURE_OPENAI_API_KEY"
else
    read -sp "Azure OpenAI API Key (or press Enter to read from dev.env): " AZURE_OPENAI_API_KEY_INPUT
    echo ""
    if [ -z "$AZURE_OPENAI_API_KEY_INPUT" ]; then
        # Try to get from file (handles both exported and non-exported)
        AZURE_OPENAI_API_KEY_INPUT=$(grep "^export AZURE_OPENAI_API_KEY=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
            grep "^AZURE_OPENAI_API_KEY=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "")
        if [ -z "$AZURE_OPENAI_API_KEY_INPUT" ]; then
            read -sp "Azure OpenAI API Key: " AZURE_OPENAI_API_KEY_INPUT
            echo ""
        fi
    fi
fi

if [ -n "$AZURE_OPENAI_DEPLOYMENT_NAME" ]; then
    AZURE_OPENAI_DEPLOYMENT_NAME_INPUT="$AZURE_OPENAI_DEPLOYMENT_NAME"
else
    AZURE_OPENAI_DEPLOYMENT_NAME_INPUT=$(grep "^export AZURE_OPENAI_DEPLOYMENT_NAME=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
        grep "^AZURE_OPENAI_DEPLOYMENT_NAME=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "gpt-5.2-chat")
    read -p "Azure OpenAI Deployment Name [${AZURE_OPENAI_DEPLOYMENT_NAME_INPUT}]: " AZURE_OPENAI_DEPLOYMENT_NAME_INPUT_OVERRIDE
    AZURE_OPENAI_DEPLOYMENT_NAME_INPUT="${AZURE_OPENAI_DEPLOYMENT_NAME_INPUT_OVERRIDE:-${AZURE_OPENAI_DEPLOYMENT_NAME_INPUT}}"
fi

if [ -n "$AZURE_OPENAI_API_VERSION" ]; then
    AZURE_OPENAI_API_VERSION_INPUT="$AZURE_OPENAI_API_VERSION"
else
    AZURE_OPENAI_API_VERSION_INPUT=$(grep "^export AZURE_OPENAI_API_VERSION=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || \
        grep "^AZURE_OPENAI_API_VERSION=" "$ENV_FILE" 2>/dev/null | cut -d'=' -f2- | tr -d '"' | tr -d "'" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' || echo "2025-04-01-preview")
    read -p "Azure OpenAI API Version [${AZURE_OPENAI_API_VERSION_INPUT}]: " AZURE_OPENAI_API_VERSION_INPUT_OVERRIDE
    AZURE_OPENAI_API_VERSION_INPUT="${AZURE_OPENAI_API_VERSION_INPUT_OVERRIDE:-${AZURE_OPENAI_API_VERSION_INPUT}}"
fi

# Assign to variables for secret setting
AZURE_OPENAI_ENDPOINT="$AZURE_OPENAI_ENDPOINT_INPUT"
AZURE_OPENAI_API_KEY="$AZURE_OPENAI_API_KEY_INPUT"
AZURE_OPENAI_DEPLOYMENT_NAME="$AZURE_OPENAI_DEPLOYMENT_NAME_INPUT"
AZURE_OPENAI_API_VERSION="$AZURE_OPENAI_API_VERSION_INPUT"

# Strip newlines from values
AZURE_OPENAI_ENDPOINT=$(echo -n "$AZURE_OPENAI_ENDPOINT" | tr -d '\n\r')
AZURE_OPENAI_API_KEY=$(echo -n "$AZURE_OPENAI_API_KEY" | tr -d '\n\r')

# Set Azure OpenAI secrets (only if values are provided)
if [ -n "$AZURE_OPENAI_ENDPOINT" ]; then
    set_secret "AZURE-OPENAI-ENDPOINT" "$AZURE_OPENAI_ENDPOINT" ""
fi

if [ -n "$AZURE_OPENAI_API_KEY" ]; then
    set_secret "AZURE-OPENAI-API-KEY" "$AZURE_OPENAI_API_KEY" ""
fi

if [ -n "$AZURE_OPENAI_DEPLOYMENT_NAME" ]; then
    set_secret "AZURE-OPENAI-DEPLOYMENT-NAME" "$AZURE_OPENAI_DEPLOYMENT_NAME" ""
fi

if [ -n "$AZURE_OPENAI_API_VERSION" ]; then
    set_secret "AZURE-OPENAI-API-VERSION" "$AZURE_OPENAI_API_VERSION" ""
fi

print_success "All secrets configured successfully!"
print_info "Key Vault: $KEY_VAULT_NAME"
print_info "Resource Group: $RESOURCE_GROUP"

