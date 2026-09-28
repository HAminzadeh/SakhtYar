$ErrorActionPreference = "Stop"

$root = Get-Location

if (-not (Test-Path (Join-Path $root ".git"))) {
    throw "Run this script from the SakhtYar repository root."
}

$branch = (git branch --show-current).Trim()
if ($branch -ne "v2") {
    throw "Current branch is '$branch'. Expected branch: v2"
}

Write-Host ""
Write-Host "=== SakhtYar pre-push cleanup & validation ===" -ForegroundColor Cyan

# Remove only temporary one-shot patch scripts created during this work.
$tempScripts = @(
    "apply-foundation-04-antd-migration.ps1",
    "apply-foundation-04-antd-migration-v2.ps1",
    "fix-foundation-04-antd-cloud-upload.ps1",
    "fix-foundation-04-antd-typescript.ps1",
    "fix-foundation-04-antd-icons-final.ps1",
    "configure-spring-managed-local-env.ps1",
    "fix-spring-local-minio-image.ps1",
    "apply-sakhtyar-spring-managed-local-stack.ps1",
    "fix-minio-community-pinned-build.ps1",
    "fix-minio-dockerfile-continuation.ps1",
    "fix-minio-aistor-current-build.ps1",
    "finalize-minio-official-image.ps1",
    "fix-sakhtyar-idempotent-local-bootstrap.ps1",
    "fix-minio-quay-community.ps1",
    "verify-and-apply-minio-community.ps1",
    "verify-and-apply-minio-community-v2.ps1",
    "cache-sakhtyar-docker-images-local.ps1",
    "cache-sakhtyar-docker-images-local-v2.ps1",
    "cache-sakhtyar-docker-images-local-v3.ps1",
    "cache-sakhtyar-docker-images-local-v4.ps1",
    "install-sakhtyar-smart-local-bootstrap.ps1",
    "apply-java-native-local-bootstrap.ps1",
    "fix-sakhtyar-application-bootstrap-wiring.ps1"
)

foreach ($name in $tempScripts) {
    $path = Join-Path $root $name
    if (Test-Path $path) {
        Remove-Item $path -Force
        Write-Host "Removed temp script: $name" -ForegroundColor DarkGray
    }
}

# Ensure local Docker image cache never gets committed.
$gitignorePath = Join-Path $root ".gitignore"
$ignoreEntry = "/.local/docker-images/"

if (-not (Test-Path $gitignorePath)) {
    Set-Content -Path $gitignorePath -Value $ignoreEntry -Encoding UTF8
} else {
    $gitignore = Get-Content $gitignorePath -Raw
    if ($gitignore -notmatch [regex]::Escape($ignoreEntry)) {
        Add-Content -Path $gitignorePath -Value "`r`n$ignoreEntry"
    }
}

# If accidentally tracked/staged, untrack only from Git index; keep local files.
git rm -r --cached --ignore-unmatch .local/docker-images *> $null 2>&1

# Do not commit generated TypeScript incremental build metadata.
if (git ls-files --error-unmatch frontend/tsconfig.app.tsbuildinfo *> $null 2>&1) {
    git restore -- frontend/tsconfig.app.tsbuildinfo 2>$null
}

Write-Host ""
Write-Host "Checking whitespace..." -ForegroundColor Cyan
git diff --check
if ($LASTEXITCODE -ne 0) {
    throw "git diff --check failed."
}

Write-Host "Checking for remaining MUI imports..." -ForegroundColor Cyan
$muiMatches = git grep -n "@mui/" -- frontend 2>$null
if ($LASTEXITCODE -eq 0 -and $muiMatches) {
    Write-Host $muiMatches -ForegroundColor Red
    throw "MUI imports still exist under frontend."
}
Write-Host "No MUI imports found." -ForegroundColor Green

Write-Host ""
Write-Host "Building frontend..." -ForegroundColor Cyan
Push-Location (Join-Path $root "frontend")
try {
    npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed."
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Compiling backend..." -ForegroundColor Cyan
Push-Location (Join-Path $root "backend")
try {
    mvn -q -pl app -am -DskipTests compile
    if ($LASTEXITCODE -ne 0) {
        throw "Backend compile failed."
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Git status before staging:" -ForegroundColor Cyan
git status --short

Write-Host ""
Write-Host "Staging changes..." -ForegroundColor Cyan
git add -A

Write-Host ""
Write-Host "Staged changes:" -ForegroundColor Cyan
git diff --cached --stat
Write-Host ""
git status --short

$answer = Read-Host "`nCommit and push these changes to origin/v2? (y/N)"
if ($answer -notin @("y", "Y", "yes", "YES", "Yes")) {
    Write-Host "Stopped before commit/push. Staged changes were left intact." -ForegroundColor Yellow
    exit 0
}

$commitMessage = "feat(local): add Java-native bootstrap and finalize Ant Design migration"

Write-Host ""
Write-Host "Creating commit..." -ForegroundColor Cyan
git commit -m $commitMessage
if ($LASTEXITCODE -ne 0) {
    throw "git commit failed."
}

Write-Host ""
Write-Host "Pushing origin/v2..." -ForegroundColor Cyan
git push origin v2
if ($LASTEXITCODE -ne 0) {
    throw "git push failed."
}

Write-Host ""
Write-Host "DONE." -ForegroundColor Green
Write-Host "Changes pushed to origin/v2." -ForegroundColor Green
git log -1 --oneline
