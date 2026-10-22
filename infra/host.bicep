param location string
param enableTelemetry bool = false

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

resource insights 'Microsoft.Insights/components@2020-02-02' = if (enableTelemetry) {
  name: 'appi-trail-${suffix}'
  location: location
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logs.id
    IngestionMode: 'LogAnalytics'
    publicNetworkAccessForIngestion: 'Enabled'
    publicNetworkAccessForQuery: 'Enabled'
  }
}

resource environment 'Microsoft.App/managedEnvironments@2024-10-02-preview' = {
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
    ...(enableTelemetry ? {
      appInsightsConfiguration: { connectionString: insights!.properties.ConnectionString }
      openTelemetryConfiguration: {
        tracesConfiguration: { destinations: ['appInsights'] }
      }
    } : {})
  }
}

output environmentId string = environment.id
output registryName string = registry.name
output registryEndpoint string = registry.properties.loginServer
output workspaceId string = logs.id
output applicationInsightsId string = enableTelemetry ? insights!.id : ''
output applicationInsightsName string = enableTelemetry ? insights!.name : ''
@secure()
output applicationInsightsConnectionString string = enableTelemetry ? insights!.properties.ConnectionString : ''
