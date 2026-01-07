@description('Azure Database for PostgreSQL Flexible Server')
param location string = resourceGroup().location
param serverName string
param administratorLogin string = 'edudron_admin'
param administratorLoginPassword string
param skuName string = 'Standard_B2s'
param tier string = 'Burstable'
param storageSizeGB int = 32
param version string = '16'
param enablePublicAccess bool = true
param allowedIpAddresses array = []

resource postgresServer 'Microsoft.DBforPostgreSQL/flexibleServers@2023-06-01-preview' = {
  name: serverName
  location: location
  sku: {
    name: skuName
    tier: tier
  }
  properties: {
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorLoginPassword
    version: version
    storage: {
      storageSizeGB: storageSizeGB
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    network: {
      publicNetworkAccess: enablePublicAccess ? 'Enabled' : 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
    maintenanceWindow: {
      customWindow: 'Disabled'
      dayOfWeek: 0
      startHour: 0
      startMinute: 0
    }
  }
}

// Create database
resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-06-01-preview' = {
  parent: postgresServer
  name: 'edudron'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// Firewall rules for public access
resource firewallRules 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-06-01-preview' = [for (ip, i) in allowedIpAddresses: {
  parent: postgresServer
  name: 'AllowIP_${i}'
  properties: {
    startIpAddress: ip
    endIpAddress: ip
  }
}]

// Allow Azure services
resource allowAzureServices 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-06-01-preview' = {
  parent: postgresServer
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

output id string = postgresServer.id
output name string = postgresServer.name
output fqdn string = postgresServer.properties.fullyQualifiedDomainName
output connectionString string = 'jdbc:postgresql://${postgresServer.properties.fullyQualifiedDomainName}:5432/edudron'
output host string = postgresServer.properties.fullyQualifiedDomainName
output port int = 5432
output databaseName string = 'edudron'
output username string = administratorLogin


