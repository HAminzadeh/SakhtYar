$ErrorActionPreference = "Stop"

$root = Get-Location
$frontend = Join-Path $root "frontend"
$assistantPath = Join-Path $frontend "src\features\case\AssistantPanel.tsx"

if (-not (Test-Path (Join-Path $root ".git"))) {
    throw "Run this script from the SakhtYar repository root."
}

if (-not (Test-Path $assistantPath)) {
    throw "AssistantPanel.tsx was not found: $assistantPath"
}

function Read-Utf8([string]$Path) {
    [System.IO.File]::ReadAllText(
        $Path,
        (New-Object System.Text.UTF8Encoding($false))
    )
}

function Write-Utf8([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        (New-Object System.Text.UTF8Encoding($false))
    )
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " SakhtYar V5.6 Hotfix - AssistantPanel JSX" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $root ".local\patch-backups\v5.6-hotfix-$stamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

Copy-Item `
    $assistantPath `
    (Join-Path $backupRoot "AssistantPanel.tsx") `
    -Force

Write-Host ""
Write-Host "[1/4] Repairing malformed JSX..." -ForegroundColor Cyan

$content = Read-Utf8 $assistantPath

$badExact = 'return (`r`n    <Stack spacing={2} className="sakhtyar-assistant-v56">'
$good = @'
return (
    <Stack spacing={2} className="sakhtyar-assistant-v56">
'@

$changed = $false

if ($content.Contains($badExact)) {
    $content = $content.Replace($badExact, $good.TrimEnd())
    $changed = $true
}

# Fallback: catch the same corruption even if spacing differs.
if (-not $changed) {
    $pattern = 'return\s*\(`r`n\s*<Stack\s+spacing=\{2\}\s+className="sakhtyar-assistant-v56">'
    $replacement = @'
return (
    <Stack spacing={2} className="sakhtyar-assistant-v56">
'@
    $newContent = [regex]::Replace(
        $content,
        $pattern,
        $replacement.TrimEnd(),
        1
    )

    if ($newContent -ne $content) {
        $content = $newContent
        $changed = $true
    }
}

# Extra cleanup: literal PowerShell newline markers must never remain in TSX.
$content = $content.Replace('`r`n', "`r`n")

Write-Utf8 $assistantPath $content

$verification = Read-Utf8 $assistantPath

if ($verification -match 'return\s*\(\s*<Stack\s+spacing=\{2\}\s+className="sakhtyar-assistant-v56">') {
    Write-Host "AssistantPanel JSX repaired." -ForegroundColor Green
} else {
    throw "Could not verify the repaired AssistantPanel return block."
}

if ($verification.Contains('`r`n')) {
    throw "Literal PowerShell newline marker is still present in AssistantPanel.tsx."
}

Write-Host ""
Write-Host "[2/4] Clearing Vite build cache..." -ForegroundColor Cyan

foreach ($path in @(
    (Join-Path $frontend "node_modules\.vite"),
    (Join-Path $frontend "dist")
)) {
    if (Test-Path $path) {
        Remove-Item $path -Recurse -Force
        Write-Host "Removed: $path" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "[3/4] Building frontend..." -ForegroundColor Cyan

Push-Location $frontend
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
Write-Host "[4/4] Validating git diff..." -ForegroundColor Cyan

git diff --check
if ($LASTEXITCODE -ne 0) {
    throw "git diff --check failed."
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " DONE - V5.6 AssistantPanel hotfix applied" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next:"
Write-Host "  cd D:\ChatGPT_Projects\SakhtYar\source\frontend"
Write-Host "  npm run dev"
Write-Host ""
Write-Host "Then press Ctrl + F5 in the browser."
Write-Host ""
Write-Host "No Docker volume was touched. No Git commit or push was performed." -ForegroundColor DarkGray
