[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('SelfTest','Audit','DraftPolicy','DraftClone','Plan','DryRun','ApplyRole','Verify')][string]$Mode,
    [string]$Approve
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$python = Get-Command python -ErrorAction Stop
if ($Mode -eq 'SelfTest') {
    & $python.Source -B -m unittest discover -s (Join-Path $root 'tests\database') -p 'test_*lifecycle.py' -v
} else {
    $params = @('-B', (Join-Path $PSScriptRoot 'role_lifecycle.py'), '--mode', $Mode, '--root', $root)
    if ($Approve) { $params += @('--approve', $Approve) }
    & $python.Source @params
}
exit $LASTEXITCODE
