#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="$SCRIPT_DIR/../config"

# Function to print colored output
print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Parse command line arguments
ENV_FILE=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --config)
            # If it ends with .env, treat as file path, otherwise as directory
            if [[ "$2" == *.env ]]; then
                ENV_FILE="$2"
            else
                CONFIG_DIR="$2"
            fi
            shift 2
            ;;
        *)
            print_error "Unknown option: $1"
            echo "Usage: $0 [--config <config-file>]"
            exit 1
            ;;
    esac
done

# Load environment variables from config file
if [ -z "$ENV_FILE" ]; then
    ENV_FILE="$CONFIG_DIR/dev.env"
fi

if [ -f "$ENV_FILE" ]; then
    print_info "Loading configuration from: $ENV_FILE"
    source "$ENV_FILE"
    print_success "Configuration loaded successfully"
else
    print_warning "Environment file not found: $ENV_FILE"
    print_info "Using default values or environment variables"
fi

# Set defaults if not provided
RESOURCE_GROUP="${RESOURCE_GROUP:-edudron-dev-rg}"
ENVIRONMENT="${ENVIRONMENT:-dev}"

# List of services for edudron
SERVICES="identity content student payment"

# Function to check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    if ! command -v az &> /dev/null; then
        print_error "Azure CLI is not installed"
        exit 1
    fi
    
    if ! az account show &> /dev/null; then
        print_error "Not logged in to Azure. Please run 'az login'"
        exit 1
    fi
    
    if ! command -v jq &> /dev/null; then
        print_error "jq is not installed. Please install jq to parse JSON"
        exit 1
    fi
    
    print_success "Prerequisites check passed"
}

# Function to get Container Apps Environment domain
get_environment_domain() {
    local gateway_name="gateway${ENVIRONMENT:+-${ENVIRONMENT}}"
    
    # Get the Container Apps Environment ID from the gateway
    local env_id=$(az containerapp show \
        --name "$gateway_name" \
        --resource-group "$RESOURCE_GROUP" \
        --query "properties.environmentId" \
        -o tsv 2>/dev/null)
    
    if [ -z "$env_id" ]; then
        print_error "Could not retrieve Container Apps Environment ID"
        return 1
    fi
    
    # Extract environment name and resource group from the ID
    # Format: /subscriptions/{sub}/resourceGroups/{rg}/providers/Microsoft.App/managedEnvironments/{env-name}
    local env_name=$(echo "$env_id" | sed 's|.*/managedEnvironments/||')
    local env_rg=$(echo "$env_id" | sed 's|.*/resourceGroups/||' | sed 's|/providers/.*||')
    
    # Get the environment domain (try the environment's resource group first, then fallback to current resource group)
    local domain=$(az containerapp env show \
        --name "$env_name" \
        --resource-group "$env_rg" \
        --query "properties.defaultDomain" \
        -o tsv 2>/dev/null)
    
    # Fallback to current resource group if not found
    if [ -z "$domain" ] && [ "$env_rg" != "$RESOURCE_GROUP" ]; then
        domain=$(az containerapp env show \
            --name "$env_name" \
            --resource-group "$RESOURCE_GROUP" \
            --query "properties.defaultDomain" \
            -o tsv 2>/dev/null)
    fi
    
    echo "$domain"
}

# Function to get service port from config
get_service_port() {
    local service=$1
    local config_file="$CONFIG_DIR/services-config.json"
    
    if [ ! -f "$config_file" ]; then
        print_error "Services config file not found: $config_file"
        return 1
    fi
    
    jq -r ".services.${service}.port // empty" "$config_file" 2>/dev/null
}

# Function to get service internal URL dynamically from Azure
get_service_internal_url() {
    local service=$1
    local app_name="${service}${ENVIRONMENT:+-${ENVIRONMENT}}"
    local port=$2
    local env_domain=$3
    
    if [ -z "$port" ] || [ -z "$env_domain" ]; then
        print_error "Missing port or environment domain for service: $service"
        return 1
    fi
    
    # Construct internal URL: https://{app-name}.internal.{environment-domain}
    # Using internal FQDN format with HTTPS (port handled by ingress)
    echo "https://${app_name}.internal.${env_domain}"
}

# Function to get service public FQDN (for display purposes)
get_service_fqdn() {
    local service=$1
    local app_name="${service}${ENVIRONMENT:+-${ENVIRONMENT}}"
    
    az containerapp show \
        --name "$app_name" \
        --resource-group "$RESOURCE_GROUP" \
        --query "properties.configuration.ingress.fqdn" \
        -o tsv 2>/dev/null
}

# Function to update gateway configuration
update_gateway_routes() {
    print_info "Configuring gateway routes..."
    
    local gateway_name="gateway${ENVIRONMENT:+-${ENVIRONMENT}}"
    
    # Check if gateway exists
    if ! az containerapp show --name "$gateway_name" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
        print_error "Gateway Container App not found: $gateway_name"
        exit 1
    fi
    
    # Get gateway FQDN
    local gateway_fqdn=$(get_service_fqdn "gateway")
    print_info "Gateway FQDN: $gateway_fqdn"
    
    # Get Container Apps Environment domain (needed for internal URLs)
    print_info "Discovering Container Apps Environment domain..."
    local env_domain=$(get_environment_domain)
    if [ -z "$env_domain" ]; then
        print_error "Could not retrieve Container Apps Environment domain"
        exit 1
    fi
    print_success "Environment domain: $env_domain"
    echo ""
    
    # Update environment variables with service URLs
    print_info "Discovering service internal URLs dynamically..."
    
    local env_vars=()
    local failed_services=()
    
    # Discover and add service URLs dynamically
    for service in $SERVICES; do
        local app_name="${service}${ENVIRONMENT:+-${ENVIRONMENT}}"
        
        # Check if service exists
        if ! az containerapp show --name "$app_name" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
            print_warning "Service '$app_name' not found, skipping..."
            failed_services+=("$service")
            continue
        fi
        
        # Get port from config
        local port=$(get_service_port "$service")
        if [ -z "$port" ]; then
            print_warning "Could not determine port for service '$service', skipping..."
            failed_services+=("$service")
            continue
        fi
        
        # Construct internal URL dynamically
        local service_url=$(get_service_internal_url "$service" "$port" "$env_domain")
        if [ -z "$service_url" ]; then
            print_warning "Could not construct URL for service '$service', skipping..."
            failed_services+=("$service")
            continue
        fi
        
        # Convert service name to env var name (e.g., identity -> IDENTITY_SERVICE_URL)
        local env_var_name=$(echo "$service" | tr '[:lower:]' '[:upper:]')"_SERVICE_URL"
        
        env_vars+=("${env_var_name}=${service_url}")
        print_info "  $service -> $service_url"
    done
    
    echo ""
    
    # Update gateway with new environment variables
    if [ ${#env_vars[@]} -gt 0 ]; then
        print_info "Updating gateway environment variables..."
        az containerapp update \
            --name "$gateway_name" \
            --resource-group "$RESOURCE_GROUP" \
            --set-env-vars "${env_vars[@]}" \
            --output none
        
        print_success "Gateway routes configured with ${#env_vars[@]} service URLs"
    else
        print_error "No service URLs to configure"
        exit 1
    fi
    
    if [ ${#failed_services[@]} -gt 0 ]; then
        print_warning "Failed to configure ${#failed_services[@]} service(s): ${failed_services[*]}"
    fi
    
    echo ""
    # Display gateway information
    print_info "Gateway Information:"
    echo "  Name: $gateway_name"
    echo "  FQDN: $gateway_fqdn"
    echo "  URL: https://$gateway_fqdn"
    echo "  Environment Domain: $env_domain"
    echo ""
    print_info "Configured Service Routes:"
    for service in $SERVICES; do
        if [[ " ${failed_services[@]} " =~ " ${service} " ]]; then
            continue
        fi
        local port=$(get_service_port "$service")
        local service_url=$(get_service_internal_url "$service" "$port" "$env_domain")
        echo "  $service -> $service_url"
    done
}

# Main execution
main() {
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}EduDron Gateway Routes Configuration${NC}"
    echo -e "${GREEN}========================================${NC}\n"
    
    if [ -n "$ENV_FILE" ]; then
        print_info "Using config file: $ENV_FILE"
    fi
    print_info "Resource Group: $RESOURCE_GROUP"
    print_info "Environment: $ENVIRONMENT"
    echo ""
    
    check_prerequisites
    update_gateway_routes
    
    echo -e "\n${GREEN}========================================${NC}"
    print_success "Gateway routes configuration completed!"
    echo -e "${GREEN}========================================${NC}\n"
    
    print_info "Gateway is ready to route requests to backend services"
}

# Run main function
main "$@"


