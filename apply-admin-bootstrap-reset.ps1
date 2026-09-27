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

Write-Host "Project root: $ProjectRoot"
Write-Host "AdminBootstrap: $adminBootstrap"
Write-Host "application.yml: $appYml"
Write-Host "application-local.yml: $localYml"

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
        admin.changePassword(
                passwordEncoder.encode(password),
                now
        );

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

Set-Content -Path $adminBootstrap -Value $adminContent -Encoding UTF8
Write-Host "Updated: $adminBootstrap" -ForegroundColor Green

Write-Step "Updating application.yml"

$appContent = Get-Content $appYml -Raw

if ($appContent -match '(?m)^\s{4}reset-password-on-startup:') {
    Write-Host "reset-password-on-startup already exists in application.yml; leaving it unchanged." -ForegroundColor Yellow
}
else {
    $pattern = '(?m)^(\s{4}display-name:\s*\$\{APP_BOOTSTRAP_ADMIN_DISPLAY_NAME:[^\r\n]*\})\s*$'
    if ($appContent -notmatch $pattern) {
        throw "Could not find bootstrap-admin display-name line in application.yml"
    }

    $replacement = '$1' + "`r`n" + '    reset-password-on-startup: ${APP_BOOTSTRAP_ADMIN_RESET_PASSWORD_ON_STARTUP:false}'
    $appContent = [regex]::Replace($appContent, $pattern, $replacement, 1)
    Set-Content -Path $appYml -Value $appContent -Encoding UTF8
    Write-Host "Added reset-password-on-startup to application.yml" -ForegroundColor Green
}

Write-Step "Updating application-local.yml"

$localBlock = @'
app:
  bootstrap-admin:
    reset-password-on-startup: ${APP_BOOTSTRAP_ADMIN_RESET_PASSWORD_ON_STARTUP:true}
'@

if (-not (Test-Path $localYml)) {
    Set-Content -Path $localYml -Value $localBlock -Encoding UTF8
    Write-Host "Created: $localYml" -ForegroundColor Green
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
        Set-Content -Path $localYml -Value $localContent -Encoding UTF8
        Write-Host "Updated existing reset-password-on-startup in application-local.yml" -ForegroundColor Green
    }
    elseif ([string]::IsNullOrWhiteSpace($localContent)) {
        Set-Content -Path $localYml -Value $localBlock -Encoding UTF8
        Write-Host "Initialized empty application-local.yml" -ForegroundColor Green
    }
    else {
        Add-Content -Path $localYml -Value "`r`n$localBlock" -Encoding UTF8
        Write-Host "Appended local bootstrap-admin settings to application-local.yml" -ForegroundColor Green
        Write-Warning "Review application-local.yml for duplicate top-level 'app:' keys. YAML duplicate keys may be undesirable."
    }
}

Write-Step "Verification"

Select-String -Path $adminBootstrap -Pattern "resetPasswordOnStartup|BOOTSTRAP_ADMIN_PASSWORD_RESET" |
    ForEach-Object { Write-Host $_.Line.Trim() }

Select-String -Path $appYml -Pattern "reset-password-on-startup" |
    ForEach-Object { Write-Host $_.Line.Trim() }

Select-String -Path $localYml -Pattern "reset-password-on-startup" |
    ForEach-Object { Write-Host $_.Line.Trim() }

if ($Build) {
    Write-Step "Building backend"
    if (-not (Test-Path $backendPom)) {
        throw "backend/pom.xml not found: $backendPom"
    }

    & mvn -f $backendPom clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }

    Write-Host "Maven build completed successfully." -ForegroundColor Green
}

Write-Step "Done"

Write-Host @"

Next steps:
1) Reload Maven in IntelliJ if needed.
2) Restart SakhtYarApplication using the local profile.
3) Login with:
   username: admin
   password: ChangeMeNow_123!

For production/server profiles, reset-password-on-startup remains false by default.
"@ -ForegroundColor Green
