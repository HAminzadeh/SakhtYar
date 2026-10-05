[CmdletBinding()]
param(
 [ValidateSet('Prepare','Export','All')][string]$Stage='All',
 [string]$RepoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
 [string]$InputRoot='', [string]$OutputRoot='', [string]$RunId=''
)
$ErrorActionPreference='Stop'
if(!$InputRoot){$InputRoot=Join-Path $RepoRoot 'DocumentationOfLawsAndRegulations'}
if(!$OutputRoot){$OutputRoot=Join-Path $RepoRoot '.local\knowledge-preparation'}
$py=Join-Path $RepoRoot '.local\venv-knowledge-preparation\Scripts\python.exe'
$engine=Join-Path $PSScriptRoot 'deep_prepare.py'
if(!(Test-Path $py)){throw 'Bootstrap first: tools\knowledge-preparation\bootstrap-knowledge-preparation.ps1'}
New-Item -ItemType Directory -Force -Path $OutputRoot|Out-Null
switch($Stage){
 'Prepare' { & $py $engine prepare --input $InputRoot --output $OutputRoot }
 'All'     { & $py $engine all --input $InputRoot --output $OutputRoot }
 'Export'  { if(!$RunId){throw '-RunId is required for Export'}; & $py $engine export --run-id $RunId --output $OutputRoot }
}
if($LASTEXITCODE -ne 0){throw "Knowledge preparation failed with exit code $LASTEXITCODE"}

$EnricherV13 = Join-Path $RepoRoot 'tools\knowledge-preparation\enrich_v13.py'
$KnowledgePythonV13 = Join-Path $RepoRoot '.local\venv-knowledge-preparation\Scripts\python.exe'
if (-not (Test-Path $KnowledgePythonV13)) { throw "Knowledge preparation Python not found: $KnowledgePythonV13" }
if (-not (Test-Path $EnricherV13)) { throw "Knowledge enrichment v1.3 not found: $EnricherV13" }
# The legacy engine has just created the newest PREPARED run. v1.3 accepts an explicit id when available,
# but retains latest-PREPARED fallback for compatibility with the current runner.
& $KnowledgePythonV13 $EnricherV13
if ($LASTEXITCODE -ne 0) { throw "Knowledge enrichment v1.3 failed with exit code $LASTEXITCODE" }

