param accountName string
param appPrincipalId string
param developerPrincipalId string

resource account 'Microsoft.CognitiveServices/accounts@2025-06-01' existing = {
  name: accountName
}

var foundryUser = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '53ca6127-db72-4b80-b1b0-d745d6d5456d')

resource appAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(account.id, appPrincipalId, foundryUser)
  scope: account
  properties: {
    principalId: appPrincipalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: foundryUser
  }
}

resource developerAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(developerPrincipalId)) {
  name: guid(account.id, developerPrincipalId, foundryUser)
  scope: account
  properties: {
    principalId: developerPrincipalId
    principalType: 'User'
    roleDefinitionId: foundryUser
  }
}
