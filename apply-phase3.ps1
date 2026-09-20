param(
    [Parameter(Mandatory=$true)]
    [string]$Repo
)

$ErrorActionPreference = "Stop"
$Overlay = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo = (Resolve-Path $Repo).Path

foreach ($top in @("backend", "frontend", "readMe")) {
    $sourceRoot = Join-Path $Overlay $top
    if (-not (Test-Path $sourceRoot)) { continue }
    Get-ChildItem -Path $sourceRoot -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($Overlay.Length).TrimStart('\\','/')
        $destination = Join-Path $Repo $relative
        $destinationDir = Split-Path -Parent $destination
        New-Item -ItemType Directory -Force -Path $destinationDir | Out-Null
        Copy-Item -Force $_.FullName $destination
        Write-Host "Applied $relative"
    }
}

Write-Host "Phase 3 overlay applied to $Repo"
Write-Host "Next: cd backend; mvn clean test"
Write-Host "Then: cd frontend; npm run build"
