param environmentName string
param location string
param phase int
param revision string
param environmentId string
param registryName string
param image string
param minReplicas int

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: registryName
}

resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'id-${environmentName}'
  location: location
}

resource pull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, identity.id, 'AcrPull')
  scope: registry
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
  }
}

resource app 'Microsoft.App/containerApps@2025-01-01' = {
  name: 'ca-${environmentName}'
  location: location
  tags: {
    'azd-env-name': environmentName
    'azd-service-name': 'web'
    phase: string(phase)
  }
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${identity.id}': {} }
  }
  properties: {
    environmentId: environmentId
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        allowInsecure: false
        transport: 'auto'
      }
      registries: [{ server: registry.properties.loginServer, identity: identity.id }]
    }
    template: {
      terminationGracePeriodSeconds: 30
      containers: [
        {
          name: 'web'
          image: image
          resources: { cpu: json('0.5'), memory: '1Gi' }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'azure' }
            { name: 'STORE_PHASE', value: string(phase) }
            { name: 'BUILD_REVISION', value: revision }
            { name: 'AZURE_CLIENT_ID', value: identity.properties.clientId }
          ]
          probes: [
            {
              type: 'Startup'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              periodSeconds: 5
              failureThreshold: 30
              timeoutSeconds: 2
            }
            {
              type: 'Liveness'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              periodSeconds: 15
              failureThreshold: 3
              timeoutSeconds: 2
            }
            {
              type: 'Readiness'
              httpGet: { path: '/actuator/health/readiness', port: 8080 }
              periodSeconds: 5
              failureThreshold: 3
              timeoutSeconds: 2
            }
          ]
        }
      ]
      scale: { minReplicas: minReplicas, maxReplicas: 1 }
    }
  }
  dependsOn: [pull]
}

output name string = app.name
output id string = app.id
output uri string = 'https://${app.properties.configuration.ingress.fqdn}'
output clientId string = identity.properties.clientId
output principalId string = identity.properties.principalId
