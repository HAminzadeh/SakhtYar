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
    [System.IO.File]::WriteAllText(
        $Path,
        $Content,
        [System.Text.UTF8Encoding]::new($false)
    )
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

Write-Step "SakhtYar auth fix v2 - finish partial 403/401 patch safely"

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

$securityPath = Join-Path $RepoRoot $securityRel
$casePagePath = Join-Path $RepoRoot $casePageRel

foreach ($p in @($securityPath, $casePagePath)) {
    if (-not (Test-Path $p)) {
        throw "Expected file not found: $p"
    }
}

Write-Step "1/5 - Backing up the current partially patched files"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupDir = Join-Path $RepoRoot ".local\patch-backups\auth-401-refresh-fix-v2-$stamp"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

Backup-File $securityRel
Backup-File $casePageRel

Write-Host "Backup: $BackupDir" -ForegroundColor Green

Write-Step "2/5 - Verifying / completing backend 401 configuration"

$security = [System.IO.File]::ReadAllText($securityPath)

if (-not $security.Contains("import org.springframework.http.HttpStatus;")) {
    $needle = "import org.springframework.http.HttpMethod;"
    if (-not $security.Contains($needle)) {
        throw "Could not find HttpMethod import in SecurityConfig.java."
    }

    $security = $security.Replace(
        $needle,
        "import org.springframework.http.HttpMethod;`nimport org.springframework.http.HttpStatus;"
    )
}

if (-not $security.Contains("import org.springframework.security.web.authentication.HttpStatusEntryPoint;")) {
    $needle = "import org.springframework.security.web.SecurityFilterChain;"
    if (-not $security.Contains($needle)) {
        throw "Could not find SecurityFilterChain import in SecurityConfig.java."
    }

    $security = $security.Replace(
        $needle,
        "import org.springframework.security.web.SecurityFilterChain;`nimport org.springframework.security.web.authentication.HttpStatusEntryPoint;"
    )
}

if (-not $security.Contains("new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)")) {
    $needle = ".cors(Customizer.withDefaults())"
    if (-not $security.Contains($needle)) {
        throw "Could not find CORS configuration anchor in SecurityConfig.java."
    }

    $replacement = @'
.cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                        )
                )
'@

    $security = $security.Replace($needle, $replacement.TrimEnd("`r","`n"))
}

Write-Utf8NoBom $securityPath $security
Write-Host "OK     backend security now returns 401 for unauthenticated protected requests." -ForegroundColor Green

Write-Step "3/5 - Finishing CaseDetailPage error handling"

$casePage = [System.IO.File]::ReadAllText($casePagePath)

# The first fix script may already have changed only the import.
if (-not $casePage.Contains("import { ApiError, api } from '../api/client'")) {
    $oldImport = "import { api } from '../api/client'"
    if (-not $casePage.Contains($oldImport)) {
        throw "Could not find API client import in CaseDetailPage.tsx."
    }

    $casePage = $casePage.Replace(
        $oldImport,
        "import { ApiError, api } from '../api/client'"
    )
}

if ($casePage.Contains("نشست کاربری منقضی شده است. لطفاً دوباره وارد شوید.")) {
    Write-Host "SKIP   CaseDetailPage already has detailed API error handling." -ForegroundColor Yellow
} else {
    # Match only the error branch between the case query check and `const item`.
    # This is intentionally whitespace-tolerant so formatter/layout differences do not break the patch.
    $pattern = '(?s)\s*if\s*\(\s*caseQuery\.isError\s*\|\|\s*!caseQuery\.data\s*\)\s*\{.*?\}\s*(?=const\s+item\s*=\s*caseQuery\.data)'
    $regex = [System.Text.RegularExpressions.Regex]::new($pattern)
    $matches = $regex.Matches($casePage)

    if ($matches.Count -ne 1) {
        throw "Could not uniquely locate the CaseDetail error branch. Match count: $($matches.Count). Stopping instead of guessing."
    }

    $replacement = @'

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

'@

    $casePage = $regex.Replace($casePage, $replacement, 1)
    Write-Utf8NoBom $casePagePath $casePage
    Write-Host "PATCH  CaseDetailPage.tsx detailed API error handling applied." -ForegroundColor Green
}

Write-Step "4/5 - Running focused backend tests"

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

Write-Step "5/5 - Building frontend"

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
Write-Host "Fix v2 completed successfully." -ForegroundColor Green
Write-Host ""
Write-Host "Now restart backend + frontend, sign in once, then open the same case." -ForegroundColor Yellow
Write-Host "Expected behavior:" -ForegroundColor Yellow
Write-Host "- expired/invalid access token -> 401 -> client automatically refreshes -> request retries"
Write-Host "- real permission failure -> 403"
Write-Host "- missing case -> actual missing-case message instead of masking every error"
