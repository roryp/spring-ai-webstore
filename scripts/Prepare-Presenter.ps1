param([switch]$WarmAzure, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if ($WarmAzure -and $Offline) { throw 'Choose online warming or offline preparation, not both.' }
if (-not $Offline) {
    git -C $root fetch --tags origin
    if ($LASTEXITCODE -ne 0) { throw 'Could not fetch the published checkpoint tags.' }
}
$commits = @{}
foreach ($phase in 1..4) {
    $commits[$phase] = git -C $root rev-parse "stage-$phase^{commit}"
    if ($LASTEXITCODE -ne 0) { throw "Publish stage-$phase before preparing the event." }
}
foreach ($phase in 1..4) {
    $path = Join-Path $root ".demo\stage-$phase"
    if (Test-Path -LiteralPath $path) {
        $actual = git -C $path rev-parse HEAD
        if ($LASTEXITCODE -ne 0 -or $actual -ne $commits[$phase]) {
            throw "An unexpected directory or checkout exists at $path. It was not overwritten."
        }
        $dirty = git -C $path status --porcelain
        if ($dirty) { throw "Preserving modified checkpoint files in $path. Review them before presenting." }
    } else {
        git -C $root worktree add --detach $path "stage-$phase"
        if ($LASTEXITCODE -ne 0) { throw "Could not prepare stage-$phase." }
    }
}
if ($IsWindows) {
    $javaHome = Split-Path (Split-Path (Get-Command java -ErrorAction Stop).Source)
    if (Test-Path (Join-Path $javaHome 'bin\javac.exe')) { $env:JAVA_HOME = $javaHome }
}
Push-Location (Join-Path $root '.demo\stage-1')
try {
    $arguments = @('-B','-ntp','verify')
    if ($Offline) { $arguments += '-o' }
    if ($IsWindows) { & .\mvnw.cmd @arguments } else { & ./mvnw @arguments }
    if ($LASTEXITCODE -ne 0) { throw 'The immutable local checkpoint failed to build or test.' }
} finally {
    Pop-Location
}
$links = Get-Content (Join-Path $root 'docs\demo-links.json') -Raw | ConvertFrom-Json
if ($WarmAzure) {
    Push-Location $root
    try {
        $subscription = azd env get-value AZURE_SUBSCRIPTION_ID
        if ($LASTEXITCODE -ne 0) { throw 'Select the prepared azd environment first.' }
        $group = azd env get-value AZURE_RESOURCE_GROUP
        if ($LASTEXITCODE -ne 0) { throw 'No prepared resource group in this environment.' }
        foreach ($phase in 2..4) {
            az containerapp update --subscription $subscription --resource-group $group `
                --name "ca-trail-stage-$phase" --min-replicas 1 --output none
            if ($LASTEXITCODE -ne 0) { throw "Could not keep phase $phase warm." }
            $url = $links."phase$phase"
            $health = Invoke-RestMethod "$url/actuator/health/readiness" -TimeoutSec 120
            if ($health.status -ne 'UP') { throw "Phase $phase is not ready." }
        }
        & (Join-Path $PSScriptRoot 'Test-Demo.ps1') -BaseUrl $links.phase3 -Phase 3
        & (Join-Path $PSScriptRoot 'Test-Demo.ps1') -BaseUrl $links.phase4 -Phase 4
    } finally {
        Pop-Location
    }
    Write-Host 'Apps now have one minimum replica. Idle charges continue until you restore zero.'
    Write-Host 'Allow telemetry ingestion, open an existing trace, and verify portal sign-in BEFORE the event.'
}
Write-Host 'Prepared four immutable code folders under .demo. No live branch switching is needed.'
Write-Host 'In a separate terminal, run .\scripts\Start-Local.ps1 and leave that terminal open.'
Write-Host "Local catalog: $($links.local)"
Write-Host "Offline backups: $($links.backups)"
Write-Host "Azure catalog: $($links.phase2)"
Write-Host "AI catalog: $($links.phase3)"
Write-Host "Observable AI: $($links.phase4)"
