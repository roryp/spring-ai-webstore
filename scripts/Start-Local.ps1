param([ValidateRange(1024,65535)][int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$jar = Join-Path $root '.demo\stage-1\target\trail-store-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw 'Run Prepare-Presenter.ps1 before the event. This launcher never builds on stage.'
}
if ($IsWindows -and (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Ignore)) {
    throw "Port $Port is already in use. The existing process has not been stopped."
}
$media = [uri]((Resolve-Path (Join-Path $root 'docs\media')).Path + [IO.Path]::DirectorySeparatorChar)
Write-Host "Local stage-1 catalog: http://127.0.0.1:$Port"
Write-Host "Recorded fallbacks: http://127.0.0.1:$Port/backups.html"
Write-Host 'Leave this terminal running. Ctrl+C stops this local app.'
& java -jar $jar "--server.port=$Port" --server.address=127.0.0.1 `
    "--spring.web.resources.static-locations=classpath:/static/,$($media.AbsoluteUri)"
if ($LASTEXITCODE -ne 0) { throw "Local app exited with code $LASTEXITCODE." }
