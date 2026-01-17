@description('Azure Storage Account for media files')
param location string = resourceGroup().location
param storageAccountName string
param containerName string = 'edudron-media'
param accessTier string = 'Hot'
param skuName string = 'Standard_LRS'
param allowBlobPublicAccess bool = false
param minimumTlsVersion string = 'TLS1_2'

resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: storageAccountName
  location: location
  kind: 'StorageV2'
  sku: {
    name: skuName
  }
  properties: {
    accessTier: accessTier
    supportsHttpsTrafficOnly: true
    minimumTlsVersion: minimumTlsVersion
    allowBlobPublicAccess: allowBlobPublicAccess
    networkAcls: {
      defaultAction: 'Allow'
      bypass: 'AzureServices'
    }
  }
}

resource blobService 'Microsoft.Storage/storageAccounts/blobServices@2023-01-01' = {
  parent: storageAccount
  name: 'default'
  properties: {
    defaultServiceVersion: '2021-04-10' // Required for proper HTTP range request support (Accept-Ranges header)
    cors: {
      corsRules: [
        {
          allowedOrigins: ['*'] // Configure specific origins in production
          allowedMethods: ['GET', 'HEAD', 'OPTIONS']
          allowedHeaders: ['*']
          exposedHeaders: ['Accept-Ranges', 'Content-Range', 'Content-Length', 'ETag']
          maxAgeInSeconds: 3600
        }
      ]
    }
  }
}

resource container 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  parent: blobService
  name: containerName
  properties: {
    publicAccess: 'None'
  }
}

output id string = storageAccount.id
output name string = storageAccount.name
output primaryEndpoints object = storageAccount.properties.primaryEndpoints
output connectionString string = 'DefaultEndpointsProtocol=https;AccountName=${storageAccount.name};AccountKey=${storageAccount.listKeys().keys[0].value};EndpointSuffix=core.windows.net'
output accountName string = storageAccount.name
output containerName string = containerName
output baseUrl string = 'https://${storageAccount.name}.blob.core.windows.net'


