param(
    [Parameter(Mandatory=$true)]
    [string]$Repo
)

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

foreach ($folder in @("backend", "frontend", "readMe")) {
    $source = Join-Path $Here $folder
    if (Test-Path $source) {
        $target = Join-Path $Repo $folder
        Copy-Item -Path (Join-Path $source "*") -Destination $target -Recurse -Force
    }
}

Write-Host "Property Flexible JSONB patch applied successfully." -ForegroundColor Green
Write-Host "Now run: backend -> mvn clean test; frontend -> npm run build" -ForegroundColor Cyan
