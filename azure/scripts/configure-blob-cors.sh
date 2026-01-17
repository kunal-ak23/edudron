#!/bin/bash

# Configure CORS for Azure Blob Storage to enable video seeking
# This script configures CORS rules to expose Accept-Ranges header to browsers

set -e

STORAGE_ACCOUNT_NAME="${1:-edudrondevoutyk5wc2mji}"

if [ -z "$STORAGE_ACCOUNT_NAME" ]; then
  echo "Usage: $0 <storage-account-name>"
  echo "Example: $0 edudrondevoutyk5wc2mji"
  exit 1
fi

echo "Configuring CORS for Azure Blob Storage: $STORAGE_ACCOUNT_NAME"
echo ""

# Configure CORS using Azure CLI
az storage cors add \
  --services b \
  --methods GET HEAD OPTIONS \
  --origins "*" \
  --allowed-headers "*" \
  --exposed-headers "Accept-Ranges,Content-Range,Content-Length,ETag" \
  --max-age 3600 \
  --account-name "$STORAGE_ACCOUNT_NAME" \
  --only-show-errors

echo ""
echo "✅ CORS configuration applied!"
echo ""
echo "To verify, check the response headers when loading a video:"
echo "  - Accept-Ranges: bytes should be present"
echo "  - Access-Control-Expose-Headers should include Accept-Ranges"
echo ""
echo "Note: It may take a few minutes for changes to propagate."
