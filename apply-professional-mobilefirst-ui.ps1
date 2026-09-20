param(
    [Parameter(Mandatory=$true)]
    [string]$Repo
)

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

foreach ($folder in @("frontend", "readMe")) {
    $source = Join-Path $Here $folder
    if (Test-Path $source) {
        $target = Join-Path $Repo $folder
        Copy-Item -Path (Join-Path $source "*") -Destination $target -Recurse -Force
    }
}

Write-Host "Professional Mobile-First UI applied." -ForegroundColor Green
Write-Host "Run: cd frontend; npm run build" -ForegroundColor Cyan
