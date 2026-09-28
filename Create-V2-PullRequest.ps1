#requires -Version 7.0

<#
.SYNOPSIS
    بررسی Branch V2 و ساخت Pull Request به main برای پروژه SakhtYar

.DESCRIPTION
    این اسکریپت:
      1. ابزارهای لازم را بررسی می‌کند
      2. وضعیت Git را بررسی می‌کند
      3. آخرین تغییرات origin را دریافت می‌کند
      4. V2 را به‌روز می‌کند
      5. Build و Testهای Maven را اجرا می‌کند
      6. فایل‌ها را برای Secretهای احتمالی بررسی می‌کند
      7. Migration و Configurationها را گزارش می‌دهد
      8. Conflict احتمالی با main را تست می‌کند
      9. V2 را Push می‌کند
     10. Pull Request از V2 به main ایجاد می‌کند

.USAGE
    از Root پروژه اجرا شود:

    .\Create-V2-PullRequest.ps1

    یا:

    pwsh -ExecutionPolicy Bypass -File .\Create-V2-PullRequest.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------
# Configuration
# ---------------------------------------------------------

$SourceBranch = "V2"
$TargetBranch = "main"

$PrTitle = "Merge V2 into main - ادغام نسخه توسعه‌یافته SakhtYar با Branch اصلی"

$TempMergeStarted = $false


# ---------------------------------------------------------
# Helpers
# ---------------------------------------------------------

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor DarkCyan
    Write-Host $Message -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor DarkCyan
}

function Write-OK {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Stop-Script {
    param([string]$Message)

    Write-Host ""
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    Write-Host ""
    exit 1
}

function Test-CommandExists {
    param([string]$Command)

    return [bool](Get-Command $Command -ErrorAction SilentlyContinue)
}


# ---------------------------------------------------------
# Header
# ---------------------------------------------------------

Clear-Host

Write-Host ""
Write-Host "SakhtYar Pull Request Automation" -ForegroundColor Magenta
Write-Host "Source : $SourceBranch" -ForegroundColor Gray
Write-Host "Target : $TargetBranch" -ForegroundColor Gray
Write-Host ""


# ---------------------------------------------------------
# 1. Required tools
# ---------------------------------------------------------

Write-Step "1/11 - بررسی ابزارهای مورد نیاز"

if (-not (Test-CommandExists "git")) {
    Stop-Script "Git روی سیستم نصب نیست یا در PATH قرار ندارد."
}

Write-OK "Git پیدا شد."

if (-not (Test-CommandExists "gh")) {
    Stop-Script @"
GitHub CLI پیدا نشد.

ابتدا GitHub CLI را نصب کن.

اگر winget داری:

winget install --id GitHub.cli

سپس:

gh auth login
"@
}

Write-OK "GitHub CLI پیدا شد."

if (-not (Test-CommandExists "mvn")) {

    if (Test-Path ".\mvnw.cmd") {
        $MavenCommand = ".\mvnw.cmd"
        Write-OK "Maven Wrapper پیدا شد."
    }
    else {
        Stop-Script "نه Maven و نه mvnw.cmd در پروژه پیدا نشد."
    }
}
else {
    $MavenCommand = "mvn"
    Write-OK "Maven پیدا شد."
}


# ---------------------------------------------------------
# 2. GitHub authentication
# ---------------------------------------------------------

Write-Step "2/11 - بررسی اتصال به GitHub"

& gh auth status

if ($LASTEXITCODE -ne 0) {
    Stop-Script "GitHub CLI Login نیست. ابتدا دستور gh auth login را اجرا کن."
}

Write-OK "GitHub Authentication معتبر است."


# ---------------------------------------------------------
# 3. Repository validation
# ---------------------------------------------------------

Write-Step "3/11 - بررسی Repository"

$insideRepo = git rev-parse --is-inside-work-tree 2>$null

if ($insideRepo -ne "true") {
    Stop-Script "این مسیر یک Git Repository نیست."
}

$RepoRoot = git rev-parse --show-toplevel

Write-OK "Repository پیدا شد:"
Write-Host "     $RepoRoot" -ForegroundColor Gray

Set-Location $RepoRoot

$RemoteUrl = git remote get-url origin

Write-Host ""
Write-Host "Origin:" -ForegroundColor DarkGray
Write-Host $RemoteUrl -ForegroundColor Gray

if ($RemoteUrl -notmatch "SakhtYar") {
    Write-Warn "نام Repository ظاهراً SakhtYar نیست. قبل از ادامه مطمئن شو Repository صحیح است."

    $continue = Read-Host "ادامه بدهم؟ (y/n)"

    if ($continue -notin @("y", "Y", "yes", "YES")) {
        exit
    }
}


# ---------------------------------------------------------
# 4. Working tree must be clean
# ---------------------------------------------------------

Write-Step "4/11 - بررسی تغییرات Local"

$GitStatus = git status --porcelain

if ($GitStatus) {

    Write-Host ""
    git status --short
    Write-Host ""

    Stop-Script @"
در Working Tree تغییر Commit نشده وجود دارد.

ابتدا تغییراتت را Commit یا Stash کن.

مثلاً:

git add .
git commit -m "your commit message"

سپس این اسکریپت را دوباره اجرا کن.
"@
}

Write-OK "Working Tree کاملاً Clean است."


# ---------------------------------------------------------
# 5. Fetch
# ---------------------------------------------------------

Write-Step "5/11 - دریافت آخرین وضعیت GitHub"

git fetch origin --prune

if ($LASTEXITCODE -ne 0) {
    Stop-Script "git fetch origin ناموفق بود."
}

Write-OK "آخرین اطلاعات Branchها دریافت شد."


# ---------------------------------------------------------
# Check branches
# ---------------------------------------------------------

git show-ref --verify --quiet "refs/remotes/origin/$TargetBranch"

if ($LASTEXITCODE -ne 0) {
    Stop-Script "Branch origin/$TargetBranch وجود ندارد."
}

git show-ref --verify --quiet "refs/remotes/origin/$SourceBranch"

if ($LASTEXITCODE -ne 0) {

    git show-ref --verify --quiet "refs/heads/$SourceBranch"

    if ($LASTEXITCODE -ne 0) {
        Stop-Script "Branch $SourceBranch پیدا نشد."
    }
}


# ---------------------------------------------------------
# Checkout V2
# ---------------------------------------------------------

Write-Step "6/11 - آماده‌سازی Branch $SourceBranch"

git checkout $SourceBranch

if ($LASTEXITCODE -ne 0) {
    Stop-Script "امکان Checkout کردن $SourceBranch وجود ندارد."
}

git pull --ff-only origin $SourceBranch

if ($LASTEXITCODE -ne 0) {
    Stop-Script @"
Branch Local با origin/$SourceBranch قابل Fast-Forward نیست.

احتمالاً Commitهای Local و Remote از هم جدا شده‌اند.

وضعیت را دستی بررسی کن.
"@
}

Write-OK "$SourceBranch با آخرین نسخه GitHub هماهنگ شد."


# ---------------------------------------------------------
# 7. Maven build/tests
# ---------------------------------------------------------

Write-Step "7/11 - اجرای Build و Test کامل Backend"

Write-Host ""
Write-Host "Command:" -ForegroundColor DarkGray
Write-Host "$MavenCommand clean verify" -ForegroundColor Gray
Write-Host ""

& $MavenCommand clean verify

if ($LASTEXITCODE -ne 0) {
    Stop-Script @"
Build یا Test پروژه شکست خورد.

Pull Request ساخته نشد.

ابتدا Errorهای Maven را رفع کن و سپس اسکریپت را دوباره اجرا کن.
"@
}

Write-OK "Maven Build و Testها با موفقیت تمام شدند."


# ---------------------------------------------------------
# Optional frontend checks
# ---------------------------------------------------------

Write-Step "8/11 - بررسی Frontend و Configurationها"

$PackageFiles = Get-ChildItem `
    -Path $RepoRoot `
    -Filter "package.json" `
    -File `
    -Recurse `
    -ErrorAction SilentlyContinue |
    Where-Object {
        $_.FullName -notmatch "\\node_modules\\" -and
        $_.FullName -notmatch "\\target\\"
    }

if ($PackageFiles.Count -gt 0) {

    Write-Host ""
    Write-Host "package.json پیدا شد:" -ForegroundColor Gray

    foreach ($pkg in $PackageFiles) {
        Write-Host " - $($pkg.FullName)" -ForegroundColor DarkGray
    }

    if (Test-CommandExists "npm") {
        Write-OK "npm روی سیستم نصب است."
    }
    else {
        Write-Warn "Frontend پیدا شد ولی npm روی سیستم پیدا نشد."
    }

}
else {
    Write-Warn "package.json پیدا نشد یا Frontend در این Repository وجود ندارد."
}


# Configuration files

$ConfigFiles = Get-ChildItem `
    -Path $RepoRoot `
    -Recurse `
    -File `
    -Include "application*.yml","application*.yaml","application*.properties",".env*" `
    -ErrorAction SilentlyContinue |
    Where-Object {
        $_.FullName -notmatch "\\target\\" -and
        $_.FullName -notmatch "\\node_modules\\"
    }

Write-Host ""
Write-Host "Configuration files:" -ForegroundColor Cyan

if ($ConfigFiles.Count -eq 0) {
    Write-Warn "فایل Configuration استانداردی پیدا نشد."
}
else {

    foreach ($file in $ConfigFiles) {
        Write-Host " - $($file.FullName.Replace($RepoRoot,''))" -ForegroundColor Gray
    }
}


# ---------------------------------------------------------
# Database migrations
# ---------------------------------------------------------

Write-Host ""
Write-Host "Database migrations:" -ForegroundColor Cyan

$MigrationFiles = Get-ChildItem `
    -Path $RepoRoot `
    -Recurse `
    -File `
    -ErrorAction SilentlyContinue |
    Where-Object {

        $_.FullName -notmatch "\\target\\" -and
        $_.FullName -notmatch "\\node_modules\\" -and
        (
            $_.FullName -match "\\db\\migration\\" -or
            $_.FullName -match "\\liquibase\\" -or
            $_.Name -match "^V\d+.*\.sql$"
        )
    }

if ($MigrationFiles.Count -eq 0) {

    Write-Warn "Database Migration مشخصی پیدا نشد."

}
else {

    foreach ($migration in $MigrationFiles) {

        Write-Host " - $($migration.FullName.Replace($RepoRoot,''))" `
            -ForegroundColor Gray
    }
}


# ---------------------------------------------------------
# 9. Secret scanning
# ---------------------------------------------------------

Write-Step "9/11 - بررسی Secret و Credentialهای احتمالی"

$SecretPatterns = @(
    'password\s*[:=]\s*["'']?[^$\s{][^"'']{3,}',
    'api[_-]?key\s*[:=]\s*["'']?[A-Za-z0-9._\-]{12,}',
    'secret\s*[:=]\s*["'']?[A-Za-z0-9._\-]{12,}',
    'token\s*[:=]\s*["'']?[A-Za-z0-9._\-]{20,}',
    'BEGIN PRIVATE KEY',
    'BEGIN RSA PRIVATE KEY',
    'ghp_[A-Za-z0-9]{20,}',
    'github_pat_[A-Za-z0-9_]{20,}'
)

$TrackedFiles = git ls-files

$SuspiciousFindings = @()

foreach ($file in $TrackedFiles) {

    $FullPath = Join-Path $RepoRoot $file

    if (-not (Test-Path $FullPath -PathType Leaf)) {
        continue
    }

    # Ignore binary/generated files
    if (
        $file -match '\.(png|jpg|jpeg|gif|ico|pdf|jar|class|zip|woff|woff2|ttf|exe|dll)$' -or
        $file -match '(^|/)target/' -or
        $file -match '(^|/)node_modules/'
    ) {
        continue
    }

    try {

        $Content = Get-Content $FullPath -Raw -ErrorAction Stop

        foreach ($Pattern in $SecretPatterns) {

            if ($Content -match $Pattern) {

                $SuspiciousFindings += $file
                break
            }
        }

    }
    catch {
        # Ignore unreadable files
    }
}

$SuspiciousFindings = $SuspiciousFindings | Sort-Object -Unique

if ($SuspiciousFindings.Count -gt 0) {

    Write-Host ""
    Write-Warn "موارد مشکوک به Credential یا Secret پیدا شدند:"

    foreach ($item in $SuspiciousFindings) {
        Write-Host " - $item" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Warn "این اسکن قطعی نیست؛ ممکن است False Positive باشد."

    $SecretContinue = Read-Host "فایل‌ها را بررسی کرده‌ای و مطمئنی Secret واقعی Commit نشده؟ (YES/no)"

    if ($SecretContinue -ne "YES") {

        Stop-Script @"
Pull Request ساخته نشد.

Secretهای احتمالی را بررسی کن.

اگر Secret واقعی قبلاً Commit شده است:
1. آن را از Repository حذف کن.
2. Credential/API Key را Rotate کن.
3. در صورت نیاز Git History را نیز پاک‌سازی کن.
"@
    }

}
else {

    Write-OK "Secret واضحی در فایل‌های Track شده پیدا نشد."
}


# ---------------------------------------------------------
# 10. Compare and conflict check
# ---------------------------------------------------------

Write-Step "10/11 - بررسی اختلاف و Conflict بین $SourceBranch و $TargetBranch"

$CommitCount = git rev-list --count "origin/$TargetBranch..HEAD"

Write-Host ""
Write-Host "Commits موجود در $SourceBranch و نه در $TargetBranch : $CommitCount" `
    -ForegroundColor Cyan

if ([int]$CommitCount -eq 0) {
    Stop-Script "$SourceBranch هیچ Commit جدیدی نسبت به $TargetBranch ندارد."
}


Write-Host ""
Write-Host "آخرین Commitهای قابل Merge:" -ForegroundColor Cyan

git log `
    --oneline `
    --decorate `
    "origin/$TargetBranch..HEAD" `
    -n 20


Write-Host ""
Write-Host "فایل‌های تغییرکرده:" -ForegroundColor Cyan

git diff `
    --stat `
    "origin/$TargetBranch...HEAD"


Write-Host ""
Write-Host "بررسی Conflict..." -ForegroundColor Cyan


# Temporary merge test
git merge `
    --no-commit `
    --no-ff `
    "origin/$TargetBranch"

$MergeExitCode = $LASTEXITCODE

if ($MergeExitCode -ne 0) {

    git merge --abort 2>$null

    Stop-Script @"
بین $SourceBranch و $TargetBranch Conflict وجود دارد.

Merge آزمایشی Abort شد.

ابتدا Conflictها را Resolve کن و دوباره اسکریپت را اجرا کن.
"@
}

$TempMergeStarted = $true

git merge --abort

$TempMergeStarted = $false

Write-OK "Conflictی برای Merge با $TargetBranch پیدا نشد."


# Verify tree is clean again

$PostMergeStatus = git status --porcelain

if ($PostMergeStatus) {
    Stop-Script "بعد از Merge Test، Working Tree Clean نیست. وضعیت Git را بررسی کن."
}


# ---------------------------------------------------------
# 11. Push + Create PR
# ---------------------------------------------------------

Write-Step "11/11 - Push و ساخت Pull Request"

git push origin $SourceBranch

if ($LASTEXITCODE -ne 0) {
    Stop-Script "Push کردن $SourceBranch ناموفق بود."
}

Write-OK "$SourceBranch روی GitHub به‌روز شد."


# Existing PR check

$ExistingPr = gh pr list `
    --base $TargetBranch `
    --head $SourceBranch `
    --state open `
    --json url `
    --jq '.[0].url'

if ($ExistingPr) {

    Write-Host ""
    Write-Warn "یک Pull Request باز از قبل وجود دارد:"
    Write-Host $ExistingPr -ForegroundColor Cyan

    exit 0
}


# ---------------------------------------------------------
# Pull Request body
# ---------------------------------------------------------

$PrBody = @"
# ادغام تغییرات Branch ``V2`` با ``main``

## خلاصه

این Pull Request با هدف ادغام آخرین تغییرات توسعه‌یافته در Branch ``V2`` با Branch اصلی پروژه یعنی ``main`` ایجاد شده است.

در Branch ``V2`` توسعه‌های اصلی پروژه **SakhtYar** انجام شده و پس از بررسی Build، Test، ساختار Repository و Conflictهای احتمالی، این تغییرات برای ورود به Branch اصلی آماده شده‌اند.

---

## Branchها

- **Source:** ``V2``
- **Target:** ``main``

---

## تغییرات اصلی

### معماری Backend

ساختار Backend پروژه به‌صورت ماژولار توسعه داده شده و شامل بخش‌هایی مانند موارد زیر است:

- Shared Kernel
- Audit
- Identity
- Case File
- Property
- Owner
- Document
- Geo
- Integration Neshan
- Agents
- Application

### Agentها

ساختار Agentهای هوشمند پروژه توسعه داده شده است تا قابلیت‌های مرتبط با پردازش درخواست کاربران، استخراج اطلاعات و استفاده از مدل‌های هوش مصنوعی به‌صورت مستقل قابل توسعه باشند.

### چندزبانه

زیرساخت پروژه برای پشتیبانی از زبان‌های فارسی و انگلیسی توسعه داده شده است.

### Authentication و Authorization

ساختار مدیریت کاربران، ورود، نقش‌ها و دسترسی‌های سیستم توسعه داده شده است.

### Property

مدل اطلاعات ملک برای نگهداری اطلاعات ساختاریافته و استفاده توسط سایر بخش‌ها و Agentها توسعه داده شده است.

### Case File

ساختار پرونده مشارکت در ساخت برای اتصال اطلاعات ملک، مالک، مدارک و سایر اطلاعات پروژه توسعه داده شده است.

### Owner

ساختار مدیریت مالکان و ارتباط آن‌ها با ملک و پرونده توسعه داده شده است.

### Document

زیرساخت مدیریت اسناد و مدارک مربوط به پرونده‌های مشارکت در ساخت توسعه داده شده است.

### Geo

زیرساخت اطلاعات مکانی و قابلیت‌های مرتبط با موقعیت ملک توسعه داده شده است.

### Neshan Integration

Integration مربوط به سرویس Neshan برای سرویس‌های مکانی پروژه اضافه و تنظیم شده است.

### PostgreSQL

تنظیمات اتصال Backend به PostgreSQL و Configurationهای مرتبط توسعه داده شده‌اند.

---

## بررسی‌های انجام‌شده

- [x] Working Tree قبل از بررسی Clean بوده است
- [x] آخرین وضعیت Remote دریافت شده است
- [x] Branch ``V2`` با Remote هماهنگ شده است
- [x] Maven Build اجرا شده است
- [x] Maven Tests اجرا شده است
- [x] ``mvn clean verify`` موفق بوده است
- [x] Configurationهای پروژه بررسی شده‌اند
- [x] Migrationهای Database بررسی شده‌اند
- [x] فایل‌های Track شده برای Secretهای واضح اسکن شده‌اند
- [x] اختلاف ``V2`` و ``main`` بررسی شده است
- [x] Merge آزمایشی با ``main`` انجام شده است
- [x] Conflict قابل تشخیصی وجود ندارد
- [x] آخرین تغییرات ``V2`` روی GitHub Push شده‌اند

---

## بررسی‌های نهایی در GitHub

قبل از Merge نهایی موارد زیر نیز بررسی شوند:

- [ ] GitHub Actions / CI موفق باشد
- [ ] بخش Files Changed مرور شود
- [ ] Secret یا Credential واقعی وجود نداشته باشد
- [ ] Configuration محیط Production بررسی شود
- [ ] Migrationهای Database برای محیط مقصد تأیید شوند
- [ ] Login و Administrator بررسی شوند
- [ ] Integration مربوط به Neshan بررسی شود
- [ ] Frontend در فارسی و انگلیسی بررسی شود

---

## نکات امنیتی

Password، Token، API Key، Private Key و سایر اطلاعات حساس نباید به‌صورت مستقیم در Repository نگهداری شوند.

اطلاعات حساس باید از طریق Environment Variable، Secret Management یا Configuration امن مدیریت شوند.

اگر Credential واقعی قبلاً Commit شده باشد، علاوه بر حذف از سورس، باید Credential مربوطه نیز Rotate شود.

---

## نتیجه

با Merge شدن این Pull Request، Branch ``main`` شامل آخرین نسخه توسعه‌یافته موجود در ``V2`` خواهد شد و مبنای توسعه فازهای بعدی پروژه SakhtYar قرار خواهد گرفت.
"@


# Create temporary body file because of Persian/Unicode content
$BodyFile = Join-Path $env:TEMP "sakhtyar-pr-body.md"

$PrBody | Set-Content `
    -Path $BodyFile `
    -Encoding UTF8


Write-Host ""
Write-Host "در حال ساخت Pull Request..." -ForegroundColor Cyan


$PrUrl = gh pr create `
    --base $TargetBranch `
    --head $SourceBranch `
    --title $PrTitle `
    --body-file $BodyFile


if ($LASTEXITCODE -ne 0) {
    Stop-Script "ساخت Pull Request ناموفق بود."
}


# Cleanup
Remove-Item $BodyFile -ErrorAction SilentlyContinue


# ---------------------------------------------------------
# Final result
# ---------------------------------------------------------

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "Pull Request با موفقیت ساخته شد." -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""

Write-Host $PrUrl -ForegroundColor Cyan

Write-Host ""
Write-Host "مرحله بعد:" -ForegroundColor Yellow
Write-Host "1. PR را در GitHub باز کن."
Write-Host "2. تب Files changed را بررسی کن."
Write-Host "3. نتیجه CI / GitHub Actions را بررسی کن."
Write-Host "4. اگر همه چیز صحیح بود Merge pull request را بزن."
Write-Host ""
