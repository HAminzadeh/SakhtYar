param([string]$RepoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)
$ErrorActionPreference='Stop'
$venv=Join-Path $RepoRoot '.local\venv-knowledge-preparation'
if(!(Get-Command python -ErrorAction SilentlyContinue)){throw 'Python was not found in PATH.'}
if(!(Test-Path $venv)){python -m venv $venv}
$py=Join-Path $venv 'Scripts\python.exe'
& $py -m pip install --upgrade pip
& $py -m pip install 'psycopg[binary]>=3.2,<4' 'pymupdf>=1.24,<2'
Write-Host "Knowledge preparation environment ready: $venv" -ForegroundColor Green