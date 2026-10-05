param(
  [Parameter(Mandatory=$true)][string]$RunId,
  [Parameter(Mandatory=$true)][string]$WorkflowId,
  [Parameter(Mandatory=$true)][string]$CorrelationId,
  [Parameter(Mandatory=$true)][string]$InputRoot,
  [string]$OutputRoot = ".local\knowledge-preparation"
)
$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Python = Join-Path $RepoRoot ".local\venv-knowledge-preparation\Scripts\python.exe"
$Pipeline = Join-Path $PSScriptRoot "pipeline.py"
if (-not (Test-Path $Python)) { throw "Python venv not found: $Python" }
if (-not (Test-Path $Pipeline)) { throw "Pipeline not found: $Pipeline" }
& $Python $Pipeline --run-id $RunId --workflow-id $WorkflowId --correlation-id $CorrelationId `
  --input-root $InputRoot --output-root $OutputRoot
if ($LASTEXITCODE -ne 0) { throw "Knowledge Preparation failed with exit code $LASTEXITCODE" }
