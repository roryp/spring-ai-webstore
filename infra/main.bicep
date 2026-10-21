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
param aiLocation string = location
param developerPrincipalId string = ''
@secure()
param workshopAccessCodeHash string = ''
param modelName string = 'gpt-6-luna'
param modelVersion string = '2026-09-22'
@minValue(1)
param modelCapacity int = 100

resource group 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: resourceGroupName
  location: location
  tags: { purpose: 'trail-store-workshop' }
}

module host './host.bicep' = {
  name: '${environmentName}-host'
  scope: group
  params: { location: location, enableTelemetry: phase == 4 }
}

resource previousApp 'Microsoft.App/containerApps@2025-01-01' existing = if (webAppExists) {
  name: 'ca-${environmentName}'
  scope: group
}

module foundry './foundry.bicep' = if (phase >= 3) {
  name: '${environmentName}-foundry'
  scope: group
  params: {
    location: aiLocation
    modelName: modelName
    modelVersion: modelVersion
    modelCapacity: modelCapacity
  }
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
    modelEndpoint: phase >= 3 ? foundry!.outputs.modelEndpoint : ''
    deploymentName: phase >= 3 ? foundry!.outputs.deploymentName : ''
    workshopAccessCodeHash: workshopAccessCodeHash
  }
}

module aiAccess './ai-access.bicep' = if (phase >= 3) {
  name: '${environmentName}-ai-access'
  scope: group
  params: {
    accountName: foundry!.outputs.accountName
    appPrincipalId: web.outputs.principalId
    developerPrincipalId: developerPrincipalId
  }
}

module telemetryConnection './telemetry-connection.bicep' = if (phase == 4) {
  name: '${environmentName}-telemetry-connection'
  scope: group
  params: {
    accountName: foundry!.outputs.accountName
    insightsId: host.outputs.applicationInsightsId
    connectionString: host.outputs.applicationInsightsConnectionString
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
output AZURE_OPENAI_ENDPOINT string = phase >= 3 ? foundry!.outputs.modelEndpoint : ''
output AZURE_OPENAI_DEPLOYMENT string = phase >= 3 ? foundry!.outputs.deploymentName : ''
output AZURE_AI_PROJECT_ENDPOINT string = phase >= 3 ? foundry!.outputs.projectEndpoint : ''
output AZURE_AI_ACCOUNT_NAME string = phase >= 3 ? foundry!.outputs.accountName : ''
output APPLICATIONINSIGHTS_RESOURCE_ID string = host.outputs.applicationInsightsId
output APPLICATIONINSIGHTS_NAME string = host.outputs.applicationInsightsName
