#!/bin/bash
# Sets up Azure Blob Storage lifecycle management policies
# - Proctoring photos/videos: deleted after 90 days
#
# Usage:
#   bash azure/scripts/setup-storage-lifecycle.sh <storage-account-name> <resource-group>
#
# Example:
#   bash azure/scripts/setup-storage-lifecycle.sh edudrondevstorage edudron-dev-rg

set -euo pipefail

STORAGE_ACCOUNT="${1:?Usage: $0 <storage-account-name> <resource-group>}"
RESOURCE_GROUP="${2:?Usage: $0 <storage-account-name> <resource-group>}"

echo "Setting up lifecycle policies for storage account: $STORAGE_ACCOUNT"

# Create lifecycle policy JSON
cat > /tmp/lifecycle-policy.json << 'POLICY'
{
  "rules": [
    {
      "enabled": true,
      "name": "delete-proctoring-photos-after-180-days",
      "type": "Lifecycle",
      "definition": {
        "actions": {
          "baseBlob": {
            "delete": {
              "daysAfterModificationGreaterThan": 180
            }
          }
        },
        "filters": {
          "blobTypes": ["blockBlob"],
          "prefixMatch": ["proctoring-photos/"]
        }
      }
    }
  ]
}
POLICY

echo "Applying lifecycle policy..."
az storage account management-policy create \
  --account-name "$STORAGE_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --policy @/tmp/lifecycle-policy.json

echo ""
echo "Lifecycle policies applied:"
echo "  - Proctoring photos (proctoring-photos/*): auto-delete after 180 days"
echo ""
echo "To verify:"
echo "  az storage account management-policy show --account-name $STORAGE_ACCOUNT --resource-group $RESOURCE_GROUP"

rm /tmp/lifecycle-policy.json
