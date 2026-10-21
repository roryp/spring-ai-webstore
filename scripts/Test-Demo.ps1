param(
    [Parameter(Mandatory)][uri]$BaseUrl,
    [Parameter(Mandatory)][ValidateSet(3,4)][int]$Phase,
    [switch]$SaveCapture
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if (-not $BaseUrl.Host.EndsWith('.azurecontainerapps.io') -and $BaseUrl.Host -notin @('localhost','127.0.0.1')) {
    throw 'Use the approved Azure Container Apps URL or a loopback endpoint, not an arbitrary host.'
}
if ($BaseUrl.Scheme -ne 'https' -and $BaseUrl.Host -notin @('localhost','127.0.0.1')) {
    throw 'The workshop access code must only be sent over HTTPS.'
}
$url = $BaseUrl.AbsoluteUri.TrimEnd('/')
$code = (Get-Content -LiteralPath (Join-Path $root '.azure\workshop-access.txt') -Raw).Trim()
$health = Invoke-RestMethod "$url/actuator/health/readiness" -TimeoutSec 120
if ($health.status -ne 'UP') { throw 'The app is not ready.' }
$status = Invoke-RestMethod "$url/api/assistant/status" -TimeoutSec 30
if (-not $status.enabled) { throw 'AI is not enabled.' }
$products = Invoke-RestMethod "$url/api/products" -TimeoutSec 30
if ($products.Count -ne 12) { throw 'The workshop expects twelve products.' }
$anonymous = Invoke-WebRequest "$url/api/assistant/messages" -Method Post -ContentType application/json `
    -Body '{"message":"Find a jacket"}' -SkipHttpErrorCheck -TimeoutSec 30
if ($anonymous.StatusCode -ne 401) { throw 'The AI endpoint must reject anonymous calls.' }
$questions = @(
    'Which waterproof jackets under $150 are in stock?',
    'Is JKT-003 available? Show an in-stock alternative.',
    'Show two in-stock items for a rainy day hike, with their prices and SKUs.'
)
$answers = foreach ($question in $questions) {
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $reply = Invoke-RestMethod "$url/api/assistant/messages" -Method Post `
        -Headers @{'X-Workshop-Code'=$code} -ContentType application/json `
        -Body (@{message=$question} | ConvertTo-Json) -TimeoutSec 40
    $clock.Stop()
    if ([string]::IsNullOrWhiteSpace($reply.reply)) { throw 'The model returned an empty answer.' }
    $skus = @([regex]::Matches($reply.reply, '[A-Z]{3}-\d{3}') | ForEach-Object Value | Sort-Object -Unique)
    if ($skus.Count -eq 0) { throw 'The answer did not cite an inventory SKU.' }
    foreach ($sku in $skus) {
        if ($sku -notin $products.sku) { throw "The model mentioned an unknown SKU: $sku" }
    }
    if ($question -eq $questions[0] -and (
        'JKT-001' -notin $skus -or 'JKT-002' -notin $skus -or
        $reply.reply -notmatch '\b99(?:\.00)?\b' -or $reply.reply -notmatch '\b139(?:\.00)?\b')) {
        throw 'The jacket answer did not contain both expected SKUs and prices.'
    }
    if ($question -eq $questions[1] -and (
        $reply.reply -notmatch '(?i)out of stock|unavailable|not (currently )?(available|in stock)' -or
        ('JKT-001' -notin $skus -and 'JKT-002' -notin $skus))) {
        throw 'The assistant must identify the out-of-stock jacket AND offer a real in-stock alternative.'
    }
    if ($question -eq $questions[2] -and ($skus.Count -lt 2 -or 'JKT-003' -in $skus)) {
        throw 'The rainy-hike answer must cite at least two in-stock products.'
    }
    $traceId = if ($reply.PSObject.Properties['traceId']) { $reply.traceId } else { $null }
    if ($Phase -eq 4 -and $traceId -notmatch '^[a-f0-9]{32}$') { throw 'Phase 4 must return a real trace ID.' }
    [pscustomobject]@{
        question=$question; reply=$reply.reply; requestId=$reply.requestId
        traceId=$traceId; elapsedMs=$clock.ElapsedMilliseconds
    }
}
$capture = [pscustomobject]@{
    source='Live preflight capture, not a simulated answer'
    capturedAt=[DateTimeOffset]::UtcNow.ToString('o')
    phase=$Phase
    url=$url
    answers=@($answers)
}
if ($SaveCapture) {
    $path = Join-Path $root "docs\media\stage-$Phase-answers.json"
    [IO.File]::WriteAllText($path, ($capture | ConvertTo-Json -Depth 8))
    Write-Host "Saved a credential-free live capture to $path"
}
$capture | ConvertTo-Json -Depth 8
