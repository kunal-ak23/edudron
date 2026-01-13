#!/bin/bash

# Publish edudron packages to GitHub Packages
# Usage: ./publish.sh [shared-utils|ui-components|all]

set -e

GITHUB_TOKEN=${GITHUB_TOKEN:-""}

if [ -z "$GITHUB_TOKEN" ]; then
    echo "❌ Error: GITHUB_TOKEN environment variable is not set"
    echo "   Get a token from: https://github.com/settings/tokens"
    echo "   Required scope: write:packages"
    exit 1
fi

# Export token for npm
export GITHUB_TOKEN

publish_package() {
    local package_name=$1
    local package_dir=$2
    
    echo "📦 Publishing $package_name..."
    cd "$package_dir"
    
    # Build the package
    echo "🔨 Building $package_name..."
    npm run build
    
    # Publish to GitHub Packages
    echo "📤 Publishing $package_name to GitHub Packages..."
    npm publish
    
    echo "✅ Successfully published $package_name"
    cd - > /dev/null
}

case "${1:-all}" in
    shared-utils)
        publish_package "@kunal-ak23/edudron-shared-utils" "shared-utils"
        ;;
    ui-components)
        publish_package "@kunal-ak23/edudron-ui-components" "ui-components"
        ;;
    all)
        publish_package "@kunal-ak23/edudron-shared-utils" "shared-utils"
        publish_package "@kunal-ak23/edudron-ui-components" "ui-components"
        echo ""
        echo "🎉 All packages published successfully!"
        echo ""
        echo "Next steps:"
        echo "1. Update app package.json files to use:"
        echo "   \"@kunal-ak23/edudron-shared-utils\": \"^1.0.0\""
        echo "   \"@kunal-ak23/edudron-ui-components\": \"^1.0.0\""
        echo "2. Update all import statements in code from:"
        echo "   '@edudron/shared-utils' → '@kunal-ak23/edudron-shared-utils'"
        echo "   '@edudron/ui-components' → '@kunal-ak23/edudron-ui-components'"
        echo "3. Make sure GITHUB_TOKEN is set in Vercel environment variables"
        ;;
    *)
        echo "Usage: $0 [shared-utils|ui-components|all]"
        exit 1
        ;;
esac

