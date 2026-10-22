param location string

var suffix = uniqueString(resourceGroup().id)

// A private registry for our container image.
resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: 'acr${suffix}'
  location: location
  sku: { name: 'Basic' }
  properties: { adminUserEnabled: false }
}

// The app's own identity. Azure trusts it, so no passwords are needed.
resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'id-${suffix}'
  location: location
}

// Allow that identity to pull images from the registry.
resource canPullImages 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, identity.id, 'AcrPull')
  scope: registry
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
  }
}

// Stores the traces. Application Insights is the place to look at them.
resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-${suffix}'
  location: location
  properties: { sku: { name: 'PerGB2018' }, retentionInDays: 30 }
}

resource insights 'Microsoft.Insights/components@2020-02-02' = {
  name: 'appi-${suffix}'
  location: location
  kind: 'web'
  properties: { Application_Type: 'web', WorkspaceResourceId: logs.id }
}

// The OpenTelemetry settings below are a preview feature, so this uses a preview API version.
resource environment 'Microsoft.App/managedEnvironments@2024-10-02-preview' = {
  name: 'cae-${suffix}'
  location: location
  properties: {
    workloadProfiles: [{ name: 'Consumption', workloadProfileType: 'Consumption' }]
    // Forward the app's OpenTelemetry traces to Application Insights.
    appInsightsConfiguration: { connectionString: insights.properties.ConnectionString }
    openTelemetryConfiguration: { tracesConfiguration: { destinations: ['appInsights'] } }
  }
}

// Microsoft Foundry, hosting the AI model. API keys are switched off.
resource foundry 'Microsoft.CognitiveServices/accounts@2025-06-01' = {
  name: 'ai-${suffix}'
  location: location
  kind: 'AIServices'
  sku: { name: 'S0' }
  properties: {
    customSubDomainName: 'ai-${suffix}'
    allowProjectManagement: true
    disableLocalAuth: true
  }
}

// The model. Capacity 30 = at most 30 requests and 30,000 tokens a minute, which also caps the cost.
resource model 'Microsoft.CognitiveServices/accounts/deployments@2025-06-01' = {
  parent: foundry
  name: 'gpt-6-luna'
  sku: { name: 'GlobalStandard', capacity: 30 }
  properties: {
    model: { format: 'OpenAI', name: 'gpt-6-luna', version: '2026-09-22' }
  }
}

// Allow the app's identity to call the model ("Foundry User" role).
resource canUseModel 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(foundry.id, identity.id, 'Foundry User')
  scope: foundry
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '53ca6127-db72-4b80-b1b0-d745d6d5456d')
  }
}

resource app 'Microsoft.App/containerApps@2025-01-01' = {
  name: 'ca-${suffix}'
  location: location
  tags: { 'azd-service-name': 'web' }
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${identity.id}': {} }
  }
  properties: {
    environmentId: environment.id
    workloadProfileName: 'Consumption'
    configuration: {
      ingress: { external: true, targetPort: 8080 }
      registries: [{ server: registry.properties.loginServer, identity: identity.id }]
    }
    template: {
      containers: [
        {
          name: 'web'
          // azd replaces this placeholder with our image when it deploys.
          image: 'mcr.microsoft.com/azuredocs/containerapps-helloworld:latest'
          // Loading the AI library takes CPU: with 1 vCPU, the first answer comes about twice as fast as with 0.5.
          resources: { cpu: json('1.0'), memory: '2Gi' }
          // Tell the app where the model is, and which identity to sign in with.
          env: [
            { name: 'AZURE_OPENAI_ENDPOINT', value: foundry.properties.endpoints['OpenAI Language Model Instance API'] }
            { name: 'AZURE_CLIENT_ID', value: identity.properties.clientId }
          ]
        }
      ]
      // Scale to zero when nobody is using it, and never above one copy.
      scale: { minReplicas: 0, maxReplicas: 1 }
    }
  }
  dependsOn: [canPullImages, canUseModel, model]
}

output registryEndpoint string = registry.properties.loginServer
output url string = 'https://${app.properties.configuration.ingress.fqdn}'
