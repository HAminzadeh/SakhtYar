[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('SelfTest','Plan','Apply','Verify')][string]$Mode,
    [string]$Approve
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$python = Get-Command python -ErrorAction Stop
if ($Mode -eq 'SelfTest') {
    & $python.Source -B -m unittest discover -s (Join-Path $root 'tests\database') -p 'test_*lifecycle.py' -v
} else {
    $params = @('-B', (Join-Path $PSScriptRoot 'local_schema_upgrade.py'), '--mode', $Mode, '--root', $root)
    if ($Approve) { $params += @('--approve', $Approve) }
    & $python.Source @params
}
exit $LASTEXITCODE
