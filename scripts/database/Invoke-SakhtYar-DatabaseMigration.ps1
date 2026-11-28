[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('SelfTest','Audit','Plan','Backup','Clone','DryRun','ApplyDatabase','Startup','Verify','Report')][string]$Mode,
    [string]$RuntimeRole,
    [string]$BackupRoot,
    [string]$Approve,
    [switch]$CheckBackend
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$python = Get-Command python -ErrorAction Stop
if ($Mode -eq 'SelfTest') {
    & $python.Source -B -m unittest discover -s (Join-Path $root 'tests\database') -p 'test_*.py' -v
} elseif ($Mode -eq 'Startup') {
    $runtimeUser = if ($env:SAKHTYAR_RUNTIME_DB_USER) { $env:SAKHTYAR_RUNTIME_DB_USER } else { 'sakhtyar_runtime' }
    $hasRuntimePassword = [string]::IsNullOrWhiteSpace($env:SAKHTYAR_RUNTIME_DB_PASSWORD) -eq $false
    $params = @('-B', (Join-Path $PSScriptRoot 'startup_migration.py'), '--profile', 'local', '--root', $root, '--runtime-role', 'sakhtyar_runtime', '--app-runtime-user', $runtimeUser, '--app-runtime-password-present', $hasRuntimePassword.ToString().ToLowerInvariant())
    & $python.Source @params
} else {
    $params = @('-B', (Join-Path $PSScriptRoot 'migration_lifecycle.py'), '--mode', $Mode, '--root', $root)
    if ($RuntimeRole) { $params += @('--runtime-role', $RuntimeRole) }
    if ($BackupRoot) { $params += @('--backup-root', $BackupRoot) }
    if ($Approve) { $params += @('--approve', $Approve) }
    if ($CheckBackend) { $params += '--check-backend' }
    & $python.Source @params
}
exit $LASTEXITCODE
