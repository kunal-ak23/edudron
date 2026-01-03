#!/bin/bash

# Deploy Azure Infrastructure for EduDron
# Usage: ./azure/scripts/deploy-infrastructure.sh --config <config-file-path>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
INFRASTRUCTURE_DIR="$SCRIPT_DIR/../infrastructure"
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

print_info "Deploying Azure infrastructure for EduDron"
print_info "  Resource Group: ${RESOURCE_GROUP}"
print_info "  Location: ${LOCATION}"
print_info "  Environment: ${ENVIRONMENT}"

# Create resource group if it doesn't exist
if az group show --name "$RESOURCE_GROUP" &>/dev/null; then
    print_info "Resource group '$RESOURCE_GROUP' already exists"
else
    print_info "Creating resource group: $RESOURCE_GROUP"
    az group create \
        --name "$RESOURCE_GROUP" \
        --location "$LOCATION" \
        --output none
    print_success "Resource group created"
fi

# Check if Bicep file exists
BICEP_FILE="$INFRASTRUCTURE_DIR/main.bicep"
if [ ! -f "$BICEP_FILE" ]; then
    print_warning "Bicep infrastructure file not found: $BICEP_FILE"
    print_info "Creating basic infrastructure using Azure CLI..."
    
    # Create Key Vault
    print_info "Creating Key Vault: $KEY_VAULT_NAME"
    if ! az keyvault show --name "$KEY_VAULT_NAME" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
        print_info "This may take a minute..."
        if az keyvault create \
            --name "$KEY_VAULT_NAME" \
            --resource-group "$RESOURCE_GROUP" \
            --location "$LOCATION" \
            --output none; then
            print_success "Key Vault created"
        else
            print_error "Failed to create Key Vault"
            exit 1
        fi
    else
        print_info "Key Vault already exists"
    fi
    
    # Create Container Apps Environment
    print_info "Creating Container Apps Environment: $ENVIRONMENT_NAME"
    if ! az containerapp env show --name "$ENVIRONMENT_NAME" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
        print_info "This may take several minutes..."
        if az containerapp env create \
            --name "$ENVIRONMENT_NAME" \
            --resource-group "$RESOURCE_GROUP" \
            --location "$LOCATION" \
            --output none; then
            print_success "Container Apps Environment created"
        else
            print_error "Failed to create Container Apps Environment"
            exit 1
        fi
    else
        print_info "Container Apps Environment already exists"
    fi
    
    print_warning "Basic infrastructure created. For full infrastructure (PostgreSQL, Redis, etc.),"
    print_warning "please create Bicep templates in $INFRASTRUCTURE_DIR"
    print_info "You can deploy PostgreSQL and Redis separately or use Bicep templates"
    
else
    # Deploy using Bicep
    print_info "Deploying infrastructure using Bicep..."
    
    # Get PostgreSQL password
    read -sp "PostgreSQL admin password: " POSTGRES_PASSWORD
    echo ""
    
    DEPLOYMENT_NAME="edudron-infrastructure-$(date +%Y%m%d-%H%M%S)"
    
    az deployment group create \
        --resource-group "$RESOURCE_GROUP" \
        --template-file "$BICEP_FILE" \
        --parameters \
            location="$LOCATION" \
            environment="$ENVIRONMENT" \
            resourcePrefix="edudron" \
            postgresAdminPassword="$POSTGRES_PASSWORD" \
        --name "$DEPLOYMENT_NAME" \
        --output none
    
    print_success "Infrastructure deployed successfully"
fi

print_success "Infrastructure deployment complete!"
print_info "Next steps:"
print_info "  1. Run setup-secrets.sh to configure Key Vault secrets"
print_info "  2. Deploy services using deploy-*.sh scripts"

