param([string]$RepoRoot=$PSScriptRoot)
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
Write-Host "========================================================================"
Write-Host " SAKHTYAR DEVELOPMENT STARTUP"
Write-Host "========================================================================"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "tools/bootstrap/Start-SakhtYar.ps1") -RepoRoot $RepoRoot
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Write-Host "[OK] Environment ready. Starting backend..."
Set-Location (Join-Path $RepoRoot "backend")
& mvn spring-boot:run -pl app -am
exit $LASTEXITCODE