param location string

var suffix = uniqueString(subscription().id, resourceGroup().name)

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-trail-${suffix}'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
    workspaceCapping: { dailyQuotaGb: 1 }
  }
}

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: 'acrtrail${suffix}'
  location: location
  sku: { name: 'Basic' }
  properties: { adminUserEnabled: false }
}

resource environment 'Microsoft.App/managedEnvironments@2025-01-01' = {
  name: 'cae-trail-${suffix}'
  location: location
  properties: {
    zoneRedundant: false
    workloadProfiles: [{ name: 'Consumption', workloadProfileType: 'Consumption' }]
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

output environmentId string = environment.id
output registryName string = registry.name
output registryEndpoint string = registry.properties.loginServer
output workspaceId string = logs.id
