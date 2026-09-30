param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 100) -ForegroundColor DarkCyan
    Write-Host (" " + $Text) -ForegroundColor Cyan
    Write-Host ("=" * 100) -ForegroundColor DarkCyan
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Backup-File([string]$RelativePath) {
    $src = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path $src)) {
        throw "Expected file not found: $RelativePath"
    }
    $dst = Join-Path $BackupDir $RelativePath
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dst) | Out-Null
    Copy-Item $src $dst -Force
}

function Replace-Once([string]$Path, [string]$Old, [string]$New, [string]$AlreadyMarker) {
    $content = [System.IO.File]::ReadAllText($Path)

    if ($AlreadyMarker -and $content.Contains($AlreadyMarker)) {
        Write-Host "SKIP   $($Path.Replace($RepoRoot + '\','')) (already fixed)" -ForegroundColor Yellow
        return
    }

    $normalized = $content.Replace("`r`n", "`n")
    $oldN = $Old.Replace("`r`n", "`n")
    $newN = $New.Replace("`r`n", "`n")

    if (-not $normalized.Contains($oldN)) {
        throw "Expected patch context not found in $Path. Stopping instead of guessing."
    }

    $normalized = $normalized.Replace($oldN, $newN)
    Write-Utf8NoBom $Path $normalized
    Write-Host "PATCH  $($Path.Replace($RepoRoot + '\',''))" -ForegroundColor Green
}

Write-Step "SakhtYar auth fix - expired access token must return 401, not 403"

$RepoRoot = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($RepoRoot)) {
    throw "Run this script from inside the SakhtYar repository."
}
$RepoRoot = $RepoRoot.Trim()
Set-Location $RepoRoot

$currentBranch = (& git branch --show-current).Trim()
if ($currentBranch -ne "v2") {
    throw "Current branch is '$currentBranch'. Expected 'v2'."
}

$securityRel = "backend/modules/identity/src/main/java/com/sakhtyar/identity/config/SecurityConfig.java"
$casePageRel = "frontend/src/pages/CaseDetailPage.tsx"

# Allow Phase 5.11 uncommitted work, but protect the two files this fix edits.
$targetDiff = @(& git diff --name-only -- $securityRel $casePageRel)
if ($LASTEXITCODE -ne 0) { throw "Could not inspect target files." }
if ($targetDiff.Count -gt 0) {
    throw "One of the target files already has uncommitted edits. Stopping to avoid overwriting user work:`n$($targetDiff -join "`n")"
}

$securityPath = Join-Path $RepoRoot $securityRel
$casePagePath = Join-Path $RepoRoot $casePageRel

Write-Step "1/5 - Backup"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $RepoRoot ".local\patch-backups\auth-401-refresh-fix-$stamp"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
Backup-File $securityRel
Backup-File $casePageRel
Write-Host "Backup: $BackupDir" -ForegroundColor Green

Write-Step "2/5 - Fixing Spring Security unauthenticated response"

Replace-Once $securityPath @'
import org.springframework.http.HttpMethod;
'@ @'
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
'@ 'import org.springframework.http.HttpStatus;'

Replace-Once $securityPath @'
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
'@ @'
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
'@ 'import org.springframework.security.web.authentication.HttpStatusEntryPoint;'

Replace-Once $securityPath @'
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
'@ @'
        http
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                        )
                )
                .csrf(csrf -> csrf
'@ '.authenticationEntryPoint('

Write-Step "3/5 - Making Case Detail show the real API error"

Replace-Once $casePagePath @'
import { api } from '../api/client'
'@ @'
import { ApiError, api } from '../api/client'
'@ 'import { ApiError, api }'

Replace-Once $casePagePath @'
  if (
    caseQuery.isError ||
    !caseQuery.data
  ) {
    return (
      <Alert
        type="error"
        showIcon
        message="پرونده پیدا نشد."
      />
    )
  }
'@ @'
  if (
    caseQuery.isError ||
    !caseQuery.data
  ) {
    const error = caseQuery.error

    let message = 'دریافت اطلاعات پرونده ناموفق بود.'

    if (error instanceof ApiError) {
      if (error.status === 403) {
        message = 'شما مجوز دسترسی به این پرونده را ندارید.'
      } else if (error.status === 404) {
        message = 'پرونده پیدا نشد.'
      } else if (error.status === 401) {
        message = 'نشست کاربری منقضی شده است. لطفاً دوباره وارد شوید.'
      } else {
        message = error.message
      }
    }

    return (
      <Alert
        type="error"
        showIcon
        message={message}
      />
    )
  }
'@ "نشست کاربری منقضی شده است."

Write-Step "4/5 - Backend tests"

if (-not $SkipTests) {
    Push-Location (Join-Path $RepoRoot "backend")
    try {
        & mvn -pl modules/identity -am test
        if ($LASTEXITCODE -ne 0) {
            throw "Identity/backend tests failed."
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Backend tests skipped by -SkipTests." -ForegroundColor Yellow
}

Write-Step "5/5 - Frontend build"

Push-Location (Join-Path $RepoRoot "frontend")
try {
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed."
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "Changed files:" -ForegroundColor Cyan
& git status --short

Write-Host ""
Write-Host "Fix completed." -ForegroundColor Green
Write-Host "Why this fixes the bug:" -ForegroundColor Yellow
Write-Host "- expired/missing access token now returns HTTP 401"
Write-Host "- frontend api client already refreshes access tokens on HTTP 401"
Write-Host "- genuine authorization failures remain HTTP 403"
Write-Host "- CaseDetail no longer reports every API failure as 'case not found'"
Write-Host ""
Write-Host "After applying: restart backend + frontend dev server, then test opening a case." -ForegroundColor Yellow
