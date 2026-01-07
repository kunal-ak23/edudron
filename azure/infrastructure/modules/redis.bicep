@description('Azure Cache for Redis')
param location string = resourceGroup().location
param cacheName string
param skuName string = 'Standard'
param family string = 'C'
param capacity int = 1
param enableNonSslPort bool = false
param minimumTlsVersion string = '1.2'
param redisVersion string = '6.0'

resource redisCache 'Microsoft.Cache/redis@2023-08-01' = {
  name: cacheName
  location: location
  properties: {
    sku: {
      name: skuName
      family: family
      capacity: capacity
    }
    enableNonSslPort: enableNonSslPort
    minimumTlsVersion: minimumTlsVersion
    redisVersion: redisVersion
    redisConfiguration: {
      'maxmemory-reserved': '102'
      'maxmemory-delta': '102'
      'maxmemory-policy': 'allkeys-lru'
    }
  }
}

output id string = redisCache.id
output name string = redisCache.name
output hostName string = redisCache.properties.hostName
output port int = 6380
output sslPort int = redisCache.properties.sslPort
output accessKeys object = {
  primaryKey: redisCache.listKeys().primaryKey
  secondaryKey: redisCache.listKeys().secondaryKey
}
output connectionString string = '${redisCache.properties.hostName}:${redisCache.properties.sslPort},ssl=true,password=${redisCache.listKeys().primaryKey}'


