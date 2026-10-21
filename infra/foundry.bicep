param location string
param modelName string = 'gpt-6-luna'
param modelVersion string = '2026-09-22'
@minValue(1)
param modelCapacity int = 100

var suffix = uniqueString(subscription().id, resourceGroup().name)

resource account 'Microsoft.CognitiveServices/accounts@2025-06-01' = {
  name: 'ai-trail-${suffix}'
  location: location
  kind: 'AIServices'
  sku: { name: 'S0' }
  identity: { type: 'SystemAssigned' }
  properties: {
    allowProjectManagement: true
    customSubDomainName: 'ai-trail-${suffix}'
    disableLocalAuth: true
    publicNetworkAccess: 'Enabled'
  }
}

resource project 'Microsoft.CognitiveServices/accounts/projects@2025-06-01' = {
  parent: account
  name: 'trail-store'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    displayName: 'Trail Store workshop'
    description: 'A small inventory assistant, hosted in Azure Container Apps.'
  }
}

resource model 'Microsoft.CognitiveServices/accounts/deployments@2025-06-01' = {
  parent: account
  name: 'chat'
  sku: { name: 'GlobalStandard', capacity: modelCapacity }
  properties: {
    model: { format: 'OpenAI', name: modelName, version: modelVersion }
    versionUpgradeOption: 'NoAutoUpgrade'
  }
  // Foundry account child operations must not race during initial provisioning.
  dependsOn: [project]
}

output accountName string = account.name
output accountId string = account.id
output projectId string = project.id
output modelEndpoint string = uri(account.properties.endpoints['OpenAI Language Model Instance API'], '/openai/v1')
output projectEndpoint string = 'https://${account.name}.services.ai.azure.com/api/projects/${project.name}'
output deploymentName string = model.name
