# PowerShell 5.1 read-only diagnostics; does not start/stop containers or change volumes.
param([string]$Source = 'D:\ChatGPT_Projects\SakhtYar\source')
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath (Join-Path $Source 'compose.spring-local.yml'))) { throw 'Source path is not a SakhtYar checkout.' }
Push-Location -LiteralPath $Source
try {
    Write-Host '[DIAG] Docker image and running container state (read-only)'
    docker compose -f compose.spring-local.yml ps
    Write-Host '[DIAG] Tempo image version; no credentials or environment variables are queried'
    docker image inspect grafana/tempo:latest --format '{{.Id}} {{.Created}}'
    Write-Host '[DIAG] Last 80 Tempo log lines. Review before sharing for sensitive data.'
    docker compose -f compose.spring-local.yml logs --no-color --tail 80 tempo
} finally { Pop-Location }
