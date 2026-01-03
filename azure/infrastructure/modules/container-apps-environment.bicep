@description('Container Apps Environment with Log Analytics integration')
param location string = resourceGroup().location
param environmentName string = 'edudron-env'
param logAnalyticsWorkspaceId string
param vnetEnabled bool = false
param vnetResourceId string = ''
param infrastructureSubnetResourceId string = ''

// Extract workspace name from resource ID
var workspaceName = last(split(logAnalyticsWorkspaceId, '/'))

resource logAnalyticsWorkspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' existing = {
  name: workspaceName
}

resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: environmentName
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalyticsWorkspace.properties.customerId
        sharedKey: logAnalyticsWorkspace.listKeys().primarySharedKey
      }
    }
    vnetConfiguration: vnetEnabled ? {
      infrastructureSubnetId: infrastructureSubnetResourceId
      internal: false
      dockerBridgeCidr: '10.0.0.1/16'
      platformReservedCidr: '10.0.1.0/24'
      platformReservedDnsIP: '10.0.1.2'
    } : null
  }
}

output id string = containerAppsEnvironment.id
output name string = containerAppsEnvironment.name
output defaultDomain string = containerAppsEnvironment.properties.defaultDomain
output staticIp string = containerAppsEnvironment.properties.staticIp

