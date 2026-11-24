[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('SelfTest','Audit','Plan','Backup','Clone','DryRun','ApplyDatabase','Verify','Report')][string]$Mode,
    [string]$RuntimeRole,
    [string]$BackupRoot,
    [string]$Approve,
    [switch]$CheckBackend
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$python = Get-Command python -ErrorAction Stop
if ($Mode -eq 'SelfTest') {
    & $python.Source -B -m unittest discover -s (Join-Path $root 'tests\database') -p 'test_migration_lifecycle.py' -v
} else {
    $params = @('-B', (Join-Path $PSScriptRoot 'migration_lifecycle.py'), '--mode', $Mode, '--root', $root)
    if ($RuntimeRole) { $params += @('--runtime-role', $RuntimeRole) }
    if ($BackupRoot) { $params += @('--backup-root', $BackupRoot) }
    if ($Approve) { $params += @('--approve', $Approve) }
    if ($CheckBackend) { $params += '--check-backend' }
    & $python.Source @params
}
exit $LASTEXITCODE
