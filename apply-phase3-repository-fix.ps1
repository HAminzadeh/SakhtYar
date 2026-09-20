param(
    [Parameter(Mandatory = $true)]
    [string]$Repo
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo = (Resolve-Path $Repo).Path

foreach ($folder in @("backend", "frontend", "readMe")) {
    $source = Join-Path $Root $folder
    if (Test-Path $source) {
        $target = Join-Path $Repo $folder
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item -Path (Join-Path $source "*") -Destination $target -Recurse -Force
    }
}

Write-Host "Phase 3 repository fix applied successfully." -ForegroundColor Green
Write-Host "Base commit reviewed: f9c4c20c7a1b377a3def7bfc0931800581ada025"
Write-Host "Next: build backend, build frontend, restart both services."
