param(
    [Parameter(Mandatory=$true)]
    [string]$Repo
)

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

$source = Join-Path $Here "backend"
$target = Join-Path $Repo "backend"

if (!(Test-Path $target)) {
    throw "Backend path not found: $target"
}

Copy-Item -Path (Join-Path $source "*") -Destination $target -Recurse -Force
Write-Host "Persian extraction v3 applied successfully." -ForegroundColor Green
