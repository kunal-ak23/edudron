targetScope = 'resourceGroup'

@description('Azure region for deployment')
param location string = resourceGroup().location

@description('Environment name (dev, staging, prod)')
param environment string = 'dev'

@description('Prefix for resource names')
param resourcePrefix string = 'edudron'

@description('PostgreSQL administrator login')
param postgresAdminLogin string = 'edudron_admin'

@description('PostgreSQL administrator password (will be stored in Key Vault)')
@secure()
param postgresAdminPassword string

@description('PostgreSQL SKU name')
param postgresSkuName string = 'Standard_B2s'

@description('PostgreSQL tier')
param postgresTier string = 'Burstable'

@description('PostgreSQL storage size in GB')
param postgresStorageSizeGB int = 32

@description('Redis SKU name')
param redisSkuName string = 'Standard'

@description('Redis family')
param redisFamily string = 'C'

@description('Redis capacity')
param redisCapacity int = 1

@description('Enable VNet for Container Apps Environment')
param enableVNet bool = false

@description('Allowed IP addresses for PostgreSQL firewall')
param allowedPostgresIPs array = []

var resourceSuffix = environment == 'prod' ? '' : '-${environment}'
var uniqueSuffix = take(uniqueString(resourceGroup().id), 4)
var rgNameShort = replace(replace(resourceGroup().name, '-rg', ''), '-RG', '')
var postgresServerName = '${toLower(resourcePrefix)}${resourceSuffix}-${toLower(rgNameShort)}-${uniqueSuffix}-postgres'
var redisCacheName = '${resourcePrefix}${resourceSuffix}-${uniqueSuffix}-redis'
var keyVaultName = '${resourcePrefix}${resourceSuffix}-${uniqueSuffix}-kv'
var storageAccountPrefix = '${toLower(resourcePrefix)}${replace(resourceSuffix, '-', '')}'
var storageAccountUnique = take(uniqueString(resourceGroup().id), 12)
var storageAccountName = '${storageAccountPrefix}${storageAccountUnique}'
var appInsightsName = '${resourcePrefix}${resourceSuffix}-appinsights'
var logAnalyticsWorkspaceName = '${resourcePrefix}${resourceSuffix}-logs'
var containerAppsEnvironmentName = '${resourcePrefix}${resourceSuffix}-env'

// Application Insights and Log Analytics
module appInsights 'modules/application-insights.bicep' = {
  name: 'appInsights'
  params: {
    location: location
    appInsightsName: appInsightsName
    logAnalyticsWorkspaceName: logAnalyticsWorkspaceName
  }
}

// Container Apps Environment
module containerAppsEnvironment 'modules/container-apps-environment.bicep' = {
  name: 'containerAppsEnvironment'
  params: {
    location: location
    environmentName: containerAppsEnvironmentName
    logAnalyticsWorkspaceId: appInsights.outputs.logAnalyticsWorkspaceId
    vnetEnabled: enableVNet
  }
}

// PostgreSQL Database
module postgresql 'modules/postgresql.bicep' = {
  name: 'postgresql'
  params: {
    location: location
    serverName: postgresServerName
    administratorLogin: postgresAdminLogin
    administratorLoginPassword: postgresAdminPassword
    skuName: postgresSkuName
    tier: postgresTier
    storageSizeGB: postgresStorageSizeGB
    enablePublicAccess: true
    allowedIpAddresses: allowedPostgresIPs
  }
}

// Redis Cache
module redis 'modules/redis.bicep' = {
  name: 'redis'
  params: {
    location: location
    cacheName: redisCacheName
    skuName: redisSkuName
    family: redisFamily
    capacity: redisCapacity
  }
}

// Key Vault
module keyVault 'modules/key-vault.bicep' = {
  name: 'keyVault'
  params: {
    location: location
    vaultName: keyVaultName
    objectIds: []
  }
}

// Storage Account
module storage 'modules/storage.bicep' = {
  name: 'storage'
  params: {
    location: location
    storageAccountName: storageAccountName
    containerName: 'edudron-media'
    allowBlobPublicAccess: false
  }
}

// Outputs
output containerAppsEnvironmentId string = containerAppsEnvironment.outputs.id
output containerAppsEnvironmentName string = containerAppsEnvironment.outputs.name
output containerAppsEnvironmentDefaultDomain string = containerAppsEnvironment.outputs.defaultDomain

output postgresHost string = postgresql.outputs.host
output postgresPort int = postgresql.outputs.port
output postgresDatabaseName string = postgresql.outputs.databaseName
output postgresUsername string = postgresql.outputs.username
output postgresConnectionString string = postgresql.outputs.connectionString

output redisHostName string = redis.outputs.hostName
output redisPort int = redis.outputs.port
output redisSslPort int = redis.outputs.sslPort

output keyVaultId string = keyVault.outputs.id
output keyVaultName string = keyVault.outputs.name
output keyVaultUri string = keyVault.outputs.vaultUri

output storageAccountName string = storage.outputs.name
output storageConnectionString string = storage.outputs.connectionString
output storageBaseUrl string = storage.outputs.baseUrl

output appInsightsConnectionString string = appInsights.outputs.connectionString
output appInsightsInstrumentationKey string = appInsights.outputs.instrumentationKey
output logAnalyticsWorkspaceId string = appInsights.outputs.logAnalyticsWorkspaceId

