param(
    [Parameter(Mandatory=$true)]
    [string]$Repo
)

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
Copy-Item `
  -Path (Join-Path $Here "frontend\*") `
  -Destination (Join-Path $Repo "frontend") `
  -Recurse `
  -Force

Write-Host "Property dropdowns applied." -ForegroundColor Green
