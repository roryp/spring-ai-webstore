param accountName string
param insightsId string
@secure()
param connectionString string

resource account 'Microsoft.CognitiveServices/accounts@2025-06-01' existing = {
  name: accountName
}

resource project 'Microsoft.CognitiveServices/accounts/projects@2025-06-01' existing = {
  parent: account
  name: 'trail-store'
}

resource connection 'Microsoft.CognitiveServices/accounts/projects/connections@2025-04-01-preview' = {
  parent: project
  name: 'workshop-observability'
  properties: {
    category: 'AppInsights'
    target: insightsId
    authType: 'ApiKey'
    isSharedToAll: true
    credentials: { key: connectionString }
    metadata: { ApiType: 'Azure', ResourceId: insightsId }
  }
}
