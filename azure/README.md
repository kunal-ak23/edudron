# EduDron Azure Deployment

This directory contains scripts and configuration files for deploying EduDron to Azure Container Apps.

## Structure

```
azure/
├── config/              # Environment configuration files
│   ├── dev.env.example  # Template for development environment
│   └── services-config.json  # Service configuration (ports, resources, etc.)
├── infrastructure/       # Bicep templates for Azure resources
│   ├── main.bicep       # Main infrastructure template
│   └── modules/         # Reusable Bicep modules
├── scripts/             # Deployment scripts
│   ├── deploy-infrastructure.sh  # Deploy Azure infrastructure
│   ├── deploy-identity.sh       # Deploy identity service
│   ├── deploy-gateway.sh        # Deploy gateway service
│   ├── deploy-content.sh        # Deploy content service
│   ├── deploy-student.sh        # Deploy student service
│   └── deploy-payment.sh        # Deploy payment service
└── docs/                # Additional documentation
```

## Quick Start

### 1. Configure Environment

Copy the example config file and update with your values:

```bash
cd azure/config
cp dev.env.example dev.env
# Edit dev.env with your Azure and Docker Hub credentials
```

### 2. Deploy Infrastructure

Deploy Azure resources (PostgreSQL, Redis, Key Vault, Container Apps Environment):

```bash
cd azure/scripts
./deploy-infrastructure.sh --config ../config/dev.env
```

### 3. Setup Secrets

Configure secrets in Azure Key Vault:

```bash
cd azure/scripts
./setup-secrets.sh --config ../config/dev.env
```

### 4. Deploy Services

Deploy each service (order doesn't matter):

```bash
# Deploy backend services
./deploy-identity.sh --config ../config/dev.env
./deploy-content.sh --config ../config/dev.env
./deploy-student.sh --config ../config/dev.env
./deploy-payment.sh --config ../config/dev.env

# Deploy gateway service
./deploy-gateway.sh --config ../config/dev.env
```

### 5. Configure Gateway Routes

After all services are deployed, configure the gateway to route to backend services:

```bash
./configure-gateway-routes.sh --config ../config/dev.env
```

This script will:
- Discover all deployed services dynamically
- Get their internal Azure Container Apps URLs
- Update the gateway with correct service URLs
- Can be run anytime to update routes if services are added/removed

## Configuration Files

### dev.env

Contains Azure resource configuration:
- Resource group name
- Location
- Environment name
- Key Vault name
- Container registry credentials

### services-config.json

Contains service-specific configuration:
- Port numbers
- CPU and memory allocation
- Replica counts
- Ingress settings
- Environment variables

## Deployment Scripts

All deployment scripts follow the same pattern:

```bash
./deploy-<service>.sh --config <config-file-path>
```

The `--config` parameter can be:
- A path to a `.env` file (e.g., `../config/dev.env`)
- A directory path (defaults to `../config/dev.env`)

## Services

### Identity Service
- Port: 8081
- Handles authentication and user management
- Requires JWT secret from Key Vault

### Gateway Service
- Port: 8080
- External ingress enabled
- Routes requests to backend services

### Content Service
- Port: 8082
- Manages courses and content

### Student Service
- Port: 8083
- Handles enrollments and progress tracking

### Payment Service
- Port: 8084
- Processes payments and subscriptions

## Prerequisites

- Azure CLI installed and configured
- Docker Hub account (or Azure Container Registry)
- Docker images built and pushed to registry
- Appropriate Azure permissions

## See Also

- [DEV_DEPLOYMENT.md](../../DEV_DEPLOYMENT.md) - Local development deployment guide
- [README.md](../../README.md) - Project overview

