targetScope = 'subscription'

param environmentName string
param location string

resource group 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: 'rg-${environmentName}'
  location: location
  tags: { 'azd-env-name': environmentName }
}

module app 'app.bicep' = {
  name: 'app'
  scope: group
  params: { location: location }
}

output AZURE_RESOURCE_GROUP string = group.name
output AZURE_CONTAINER_REGISTRY_ENDPOINT string = app.outputs.registryEndpoint
output WEBSITE_URL string = app.outputs.url
