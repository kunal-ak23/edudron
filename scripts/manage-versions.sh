#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
VERSIONS_FILE="versions.json"
SERVICES=("gateway" "identity" "content" "student" "payment")

# Check if jq is available
HAS_JQ=false
if command -v jq &> /dev/null; then
    HAS_JQ=true
fi

# Function to validate version format
validate_version() {
    local version=$1
    if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo -e "${RED}Error: Invalid version format. Use semantic versioning (MAJOR.MINOR.PATCH)${NC}"
        return 1
    fi
    return 0
}

# Function to get version
get_version() {
    local service=$1
    if [ ! -f "$VERSIONS_FILE" ]; then
        echo "0.1.0"
        return
    fi
    
    if [ "$HAS_JQ" = true ]; then
        jq -r ".\"${service}\" // \"0.1.0\"" "$VERSIONS_FILE" 2>/dev/null
    else
        grep -o "\"${service}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$VERSIONS_FILE" | sed -E 's/.*"([^"]+)"[[:space:]]*:[[:space:]]*"([^"]+)".*/\2/' || echo "0.1.0"
    fi
}

# Function to set version
set_version() {
    local service=$1
    local version=$2
    
    if ! validate_version "$version"; then
        return 1
    fi
    
    if [ "$HAS_JQ" = true ]; then
        local temp_file=$(mktemp)
        jq ".\"${service}\" = \"${version}\"" "$VERSIONS_FILE" > "$temp_file"
        mv "$temp_file" "$VERSIONS_FILE"
    else
        # Fallback using sed
        local temp_file=$(mktemp)
        if grep -q "\"${service}\"" "$VERSIONS_FILE"; then
            sed -E "s/\"${service}\"[[:space:]]*:[[:space:]]*\"[^\"]*\"/\"${service}\": \"${version}\"/" "$VERSIONS_FILE" > "$temp_file"
        else
            # Add new service
            sed -E "s/}$/  \"${service}\": \"${version}\"\n}/" "$VERSIONS_FILE" > "$temp_file"
        fi
        mv "$temp_file" "$VERSIONS_FILE"
    fi
}

# Function to bump version
bump_version() {
    local service=$1
    local bump_type=$2
    local current_version=$(get_version "$service")
    
    IFS='.' read -ra VERSION_PARTS <<< "$current_version"
    local major=${VERSION_PARTS[0]}
    local minor=${VERSION_PARTS[1]}
    local patch=${VERSION_PARTS[2]}
    
    case "$bump_type" in
        patch)
            patch=$((patch + 1))
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        *)
            echo -e "${RED}Error: Invalid bump type. Use patch, minor, or major${NC}"
            return 1
            ;;
    esac
    
    local new_version="${major}.${minor}.${patch}"
    set_version "$service" "$new_version"
    echo -e "${GREEN}${service}: ${current_version} → ${new_version}${NC}"
}

# Function to list all versions
list_versions() {
    echo -e "${GREEN}Service versions:${NC}"
    for service in "${SERVICES[@]}"; do
        local version=$(get_version "$service")
        echo -e "  ${service}: ${version}"
    done
}

# Main script
case "${1:-help}" in
    get)
        if [ -z "$2" ]; then
            echo -e "${RED}Error: Service name required${NC}"
            exit 1
        fi
        get_version "$2"
        ;;
    set)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo -e "${RED}Error: Service name and version required${NC}"
            exit 1
        fi
        set_version "$2" "$3"
        ;;
    bump)
        if [ -z "$2" ] || [ -z "$3" ]; then
            echo -e "${RED}Error: Service name and bump type required${NC}"
            exit 1
        fi
        bump_version "$2" "$3"
        ;;
    list)
        list_versions
        ;;
    help|--help|-h)
        echo "EduDron Version Management"
        echo ""
        echo "Usage: $0 [command] [service] [version|bump-type]"
        echo ""
        echo "Commands:"
        echo "  get <service>              Get version for a service"
        echo "  set <service> <version>     Set version for a service"
        echo "  bump <service> <type>       Bump version (patch|minor|major)"
        echo "  list                        List all service versions"
        echo ""
        echo "Examples:"
        echo "  $0 get gateway"
        echo "  $0 set gateway 0.2.0"
        echo "  $0 bump gateway patch"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        exit 1
        ;;
esac

