param([switch]$Rotate)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$path = Join-Path $root '.azure\workshop-access.txt'
if ((Test-Path -LiteralPath $path) -and -not $Rotate) {
    $code = (Get-Content -LiteralPath $path -Raw).Trim()
} else {
    New-Item -ItemType Directory -Path (Join-Path $root '.azure') -Force | Out-Null
    $code = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
    [IO.File]::WriteAllText($path, $code)
}
if ($code -notmatch '^[a-f0-9]{64}$') {
    throw "The workshop code file is not a valid generated code: $path"
}
$hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($code))).ToLowerInvariant()
Push-Location $root
try {
    azd env set WORKSHOP_ACCESS_CODE_HASH $hash
    if ($LASTEXITCODE -ne 0) { throw 'Could not configure the workshop access-code hash.' }
} finally {
    Pop-Location
}
Write-Host "Workshop code saved privately in $path (gitignored). Only its hash is deployed."
Write-Host 'Share it only with participants. Re-provision both AI phases after rotating it.'
