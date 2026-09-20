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

Copy-Item -Path (Join-Path $Here ".env.example") -Destination (Join-Path $Repo ".env.example") -Force
Copy-Item -Path (Join-Path $Here "docker-compose.yml") -Destination (Join-Path $Repo "docker-compose.yml") -Force

Write-Host "Identity & Access Control v1 applied." -ForegroundColor Green
Write-Host "Run backend: mvn clean test" -ForegroundColor Cyan
Write-Host "Run frontend: npm run build" -ForegroundColor Cyan
