targetScope = 'subscription'

@minLength(1)
@maxLength(24)
param environmentName string
param location string
param resourceGroupName string = 'rg-${environmentName}'
@allowed([2, 3, 4])
param phase int = 2
param webAppExists bool = false
@minValue(0)
@maxValue(1)
param minReplicas int = 0

resource group 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: resourceGroupName
  location: location
  tags: { purpose: 'trail-store-workshop' }
}

module host './host.bicep' = {
  name: '${environmentName}-host'
  scope: group
  params: { location: location }
}

resource previousApp 'Microsoft.App/containerApps@2025-01-01' existing = if (webAppExists) {
  name: 'ca-${environmentName}'
  scope: group
}

module web './web.bicep' = {
  name: '${environmentName}-web'
  scope: group
  params: {
    environmentName: environmentName
    location: location
    phase: phase
    environmentId: host.outputs.environmentId
    registryName: host.outputs.registryName
    image: webAppExists ? previousApp!.properties.template.containers[0].image : 'mcr.microsoft.com/azuredocs/containerapps-helloworld:latest'
    minReplicas: minReplicas
  }
}

output AZURE_RESOURCE_GROUP string = group.name
output AZURE_CONTAINER_REGISTRY_ENDPOINT string = host.outputs.registryEndpoint
output AZURE_CONTAINER_REGISTRY_NAME string = host.outputs.registryName
output AZURE_CONTAINER_APPS_ENVIRONMENT_ID string = host.outputs.environmentId
output SERVICE_WEB_RESOURCE_NAME string = web.outputs.name
output SERVICE_WEB_RESOURCE_ID string = web.outputs.id
output SERVICE_WEB_URI string = web.outputs.uri
output AZURE_CLIENT_ID string = web.outputs.clientId
