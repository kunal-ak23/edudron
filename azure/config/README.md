# Configuration Files

This directory contains configuration files for Azure deployments.

## Files

### dev.env.example

Template for environment configuration. Copy this to `dev.env` and update with your values:

```bash
cp dev.env.example dev.env
```

Required variables:
- `RESOURCE_GROUP` - Azure resource group name
- `LOCATION` - Azure region (e.g., centralindia)
- `ENVIRONMENT` - Environment name (dev, staging, prod)
- `ENVIRONMENT_NAME` - Container Apps Environment name
- `KEY_VAULT_NAME` - Azure Key Vault name
- `CONTAINER_REGISTRY_SERVER` - Docker Hub username or ACR name
- `CONTAINER_REGISTRY_USERNAME` - Registry username
- `CONTAINER_REGISTRY_PASSWORD` - Registry password/token

### services-config.json

Service configuration including:
- Port numbers
- CPU and memory allocation
- Replica counts (min/max)
- Ingress settings (external/internal)
- Default environment variables

You can customize these values per environment by creating environment-specific config files.

## Usage

All deployment scripts accept a `--config` parameter:

```bash
# Use default config (config/dev.env)
./deploy-identity.sh

# Use specific config file
./deploy-identity.sh --config ../config/dev.env

# Use config directory (will look for dev.env)
./deploy-identity.sh --config ../config
```


