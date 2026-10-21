$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
Push-Location $root
try {
    $endpoint = azd env get-value AZURE_AI_PROJECT_ENDPOINT
    if ($LASTEXITCODE -ne 0) { throw 'No Foundry project endpoint in the selected azd environment.' }
    $subscription = azd env get-value AZURE_SUBSCRIPTION_ID
    if ($LASTEXITCODE -ne 0) { throw 'No subscription in the selected azd environment.' }
    $token = az account get-access-token --subscription $subscription --resource https://ai.azure.com --query accessToken --output tsv
    if ($LASTEXITCODE -ne 0) { throw 'Azure sign-in is required for Foundry registration.' }
    $headers = @{Authorization="Bearer $token"; 'Foundry-Features'='ExternalAgents=V1Preview'}
    $uri = "$($endpoint.TrimEnd('/'))/agents/trail-store-assistant?api-version=v1"
    $existing = Invoke-RestMethod $uri -Headers $headers -SkipHttpErrorCheck -StatusCodeVariable lookupStatus
    if ($lookupStatus -eq 200) {
        $record = $existing
        if ($record.versions.latest.definition.kind -ne 'external' -or
            $record.versions.latest.definition.otel_agent_id -ne 'trail-store-assistant') {
            throw 'An incompatible registration already exists. It has not been changed.'
        }
        Write-Host 'The matching external-agent registration already exists.'
    } elseif ($lookupStatus -eq 404) {
        $body = @{
            description='Read-only Spring AI inventory assistant hosted in Container Apps.'
            definition=@{kind='external'; otel_agent_id='trail-store-assistant'}
        } | ConvertTo-Json -Depth 5
        $record = Invoke-RestMethod "$($endpoint.TrimEnd('/'))/agents/trail-store-assistant/versions?api-version=v1" `
            -Headers $headers -Method Post -ContentType application/json -Body $body
        Write-Host "Registered $($record.name). The application remains hosted in Container Apps."
    } else {
        throw "Foundry registration lookup failed with HTTP $lookupStatus."
    }
} finally {
    Pop-Location
}
