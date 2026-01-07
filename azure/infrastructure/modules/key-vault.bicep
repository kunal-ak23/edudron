@description('Azure Key Vault for secrets management')
param location string = resourceGroup().location
param vaultName string
param tenantId string = subscription().tenantId
param objectIds array = []
param enableSoftDelete bool = true
param enablePurgeProtection bool = false

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: vaultName
  location: location
  properties: {
    tenantId: tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enabledForDeployment: false
    enabledForTemplateDeployment: true
    enabledForDiskEncryption: false
    enableSoftDelete: enableSoftDelete
    accessPolicies: [for objectId in objectIds: {
      tenantId: tenantId
      objectId: objectId
      permissions: {
        keys: ['all']
        secrets: ['all']
        certificates: ['all']
      }
    }]
    networkAcls: {
      defaultAction: 'Allow'
      bypass: 'AzureServices'
    }
    publicNetworkAccess: 'Enabled'
  }
}

output id string = keyVault.id
output name string = keyVault.name
output vaultUri string = keyVault.properties.vaultUri


