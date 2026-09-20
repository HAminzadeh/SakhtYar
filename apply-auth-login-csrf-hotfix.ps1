param(
    [Parameter(Mandatory=$true)]
    [string]$Repo
)
$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
Copy-Item `
  -Path (Join-Path $Here "backend\*") `
  -Destination (Join-Path $Repo "backend") `
  -Recurse `
  -Force
Write-Host "Auth Login CSRF Hotfix applied." -ForegroundColor Green
