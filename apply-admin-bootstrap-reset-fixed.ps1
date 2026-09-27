param(
    [Parameter(Mandatory = $false)]
    [string]$ProjectRoot = (Get-Location).Path,

    [Parameter(Mandatory = $false)]
    [switch]$Build
)

$ErrorActionPreference = "Stop"

function Write-Step($message) {
    Write-Host "`n=== $message ===" -ForegroundColor Cyan
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Content
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Backup-File($path) {
    if (Test-Path $path) {
        $backup = "$path.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Copy-Item $path $backup -Force
        Write-Host "Backup: $backup" -ForegroundColor DarkGray
    }
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path

$adminBootstrap = Join-Path $ProjectRoot "backend\modules\identity\src\main\java\com\sakhtyar\identity\application\AdminBootstrap.java"
$appYml         = Join-Path $ProjectRoot "backend\app\src\main\resources\application.yml"
$localYml       = Join-Path $ProjectRoot "backend\app\src\main\resources\application-local.yml"
$backendPom     = Join-Path $ProjectRoot "backend\pom.xml"

Write-Step "Checking project files"

foreach ($file in @($adminBootstrap, $appYml)) {
    if (-not (Test-Path $file)) {
        throw "Required file not found: $file"
    }
}

Write-Step "Backing up files"
Backup-File $adminBootstrap
Backup-File $appYml
if (Test-Path $localYml) {
    Backup-File $localYml
}

Write-Step "Updating AdminBootstrap.java"

$adminContent = @'
package com.sakhtyar.identity.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.identity.domain.UserEntity;
import com.sakhtyar.identity.domain.UserRepository;
import com.sakhtyar.identity.domain.UserRole;
import com.sakhtyar.identity.domain.UserStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final String username;
    private final String password;
    private final String displayName;
    private final boolean resetPasswordOnStartup;

    public AdminBootstrap(
            UserRepository repository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            @Value("${app.bootstrap-admin.username}") String username,
            @Value("${app.bootstrap-admin.password}") String password,
            @Value("${app.bootstrap-admin.display-name}") String displayName,
            @Value("${app.bootstrap-admin.reset-password-on-startup:false}") boolean resetPasswordOnStartup
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.resetPasswordOnStartup = resetPasswordOnStartup;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UserEntity existingAdmin =
                repository.findByUsernameIgnoreCase(username).orElse(null);

        if (existingAdmin != null) {
            resetExistingAdminPasswordIfRequested(existingAdmin);
            return;
        }

        Instant now = Instant.now();
        UserEntity admin = new UserEntity(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(password),
                displayName,
                null,
                null,
                UserRole.ADMIN,
                UserStatus.ACTIVE,
                now
        );

        repository.save(admin);

        auditService.recordAs(
                "USER",
                admin.getId(),
                "BOOTSTRAP_ADMIN_CREATED",
                "system",
                Map.of("username", admin.getUsername())
        );
    }

    private void resetExistingAdminPasswordIfRequested(UserEntity admin) {
        if (!resetPasswordOnStartup) {
            return;
        }

        if (passwordEncoder.matches(password, admin.getPasswordHash())) {
            return;
        }

        Instant now = Instant.now();
        admin.changePassword(passwordEncoder.encode(password), now);
        repository.save(admin);

        auditService.recordAs(
                "USER",
                admin.getId(),
                "BOOTSTRAP_ADMIN_PASSWORD_RESET",
                "system",
                Map.of("username", admin.getUsername())
        );
    }
}
'@

Write-Utf8NoBom -Path $adminBootstrap -Content $adminContent
Write-Host "Updated without UTF-8 BOM: $adminBootstrap" -ForegroundColor Green

Write-Step "Updating application.yml"

$appContent = Get-Content $appYml -Raw

if ($appContent -notmatch '(?m)^\s{4}reset-password-on-startup:') {
    $pattern = '(?m)^(\s{4}display-name:\s*\$\{APP_BOOTSTRAP_ADMIN_DISPLAY_NAME:[^\r\n]*\})\s*$'
    if ($appContent -notmatch $pattern) {
        throw "Could not find bootstrap-admin display-name line in application.yml"
    }

    $replacement = '$1' + "`r`n" + '    reset-password-on-startup: ${APP_BOOTSTRAP_ADMIN_RESET_PASSWORD_ON_STARTUP:false}'
    $appContent = [regex]::Replace($appContent, $pattern, $replacement, 1)
}
Write-Utf8NoBom -Path $appYml -Content $appContent
Write-Host "application.yml written without BOM." -ForegroundColor Green

Write-Step "Updating application-local.yml"

$localBlock = @'
app:
  bootstrap-admin:
    reset-password-on-startup: ${APP_BOOTSTRAP_ADMIN_RESET_PASSWORD_ON_STARTUP:true}
'@

if (-not (Test-Path $localYml)) {
    Write-Utf8NoBom -Path $localYml -Content $localBlock
}
else {
    $localContent = Get-Content $localYml -Raw
    if ($localContent -match '(?m)^\s*reset-password-on-startup:') {
        $localContent = [regex]::Replace(
            $localContent,
            '(?m)^(\s*reset-password-on-startup:\s*).*$',
            '${1}${APP_BOOTSTRAP_ADMIN_RESET_PASSWORD_ON_STARTUP:true}',
            1
        )
        Write-Utf8NoBom -Path $localYml -Content $localContent
    }
    elseif ([string]::IsNullOrWhiteSpace($localContent)) {
        Write-Utf8NoBom -Path $localYml -Content $localBlock
    }
    else {
        $combined = $localContent.TrimEnd() + "`r`n`r`n" + $localBlock + "`r`n"
        Write-Utf8NoBom -Path $localYml -Content $combined
        Write-Warning "Review application-local.yml for duplicate top-level 'app:' keys."
    }
}
Write-Host "application-local.yml written without BOM." -ForegroundColor Green

Write-Step "Checking BOM"

$bytes = [System.IO.File]::ReadAllBytes($adminBootstrap)
if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
    throw "AdminBootstrap.java still contains UTF-8 BOM."
}
Write-Host "AdminBootstrap.java has no UTF-8 BOM." -ForegroundColor Green

if ($Build) {
    Write-Step "Building backend"
    & mvn -f $backendPom clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }
    Write-Host "Maven build completed successfully." -ForegroundColor Green
}

Write-Step "Done"
Write-Host @"

Restart SakhtYarApplication with the local profile, then login with:
  username: admin
  password: ChangeMeNow_123!

"@ -ForegroundColor Green
