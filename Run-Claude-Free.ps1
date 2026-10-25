#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [int]$GatewayPort = 3456,
    [int]$StartupTimeoutSeconds = 25
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Status([string]$level, [string]$message) {
    Write-Host "[$level] $message"
}
function TcpReady([int]$port) {
    $socket = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $socket.ConnectAsync('127.0.0.1', $port)
        return ($pending.Wait(500) -and $socket.Connected)
    } catch { return $false } finally { $socket.Dispose() }
}

try {
    if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = $PSScriptRoot }
    if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = (Get-Location).Path }
    $ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
    if (-not (Test-Path -LiteralPath $ProjectRoot -PathType Container)) {
        throw "Project directory not found: $ProjectRoot"
    }
    Set-Location -LiteralPath $ProjectRoot
    Status 'INFO' "Project root: $ProjectRoot"

    $ccr = Get-Command 'ccr.cmd' -ErrorAction SilentlyContinue
    if (-not $ccr) { $ccr = Get-Command 'ccr' -ErrorAction SilentlyContinue }
    if (-not $ccr) { throw 'CCR not found on PATH. Install/configure CCR first.' }

    # This CCR version reuses the running background service, and starts it if absent.
    # Do not print its output: management URLs may contain a secret token.
    Status 'INFO' 'Checking / starting CCR in background (no browser)...'
    $output = & $ccr.Source start --no-open 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $sanitized = ($output -replace '(?i)(ccr_web_token|token|key)=[^\s&]+','$1=[REDACTED]')
        throw "CCR start returned exit code $exitCode. $sanitized"
    }

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (TcpReady $GatewayPort) { break }
        Start-Sleep -Milliseconds 600
    }
    if (-not (TcpReady $GatewayPort)) {
        throw "CCR command completed but gateway 127.0.0.1:$GatewayPort is not reachable. Check CCR Server settings and gateway port."
    }
    Status 'OK' "CCR gateway port $GatewayPort is listening."
    Status 'INFO' 'Launching SakhtYar dashboard...'

    $candidates = @('Start-SakhtYar-v4.ps1','Start-SakhtYar.ps1')
    $dashboardScript = $null
    foreach ($candidate in $candidates) {
        $path = Join-Path $ProjectRoot $candidate
        if (Test-Path -LiteralPath $path -PathType Leaf) { $dashboardScript = $path; break }
    }
    if (-not $dashboardScript) {
        throw 'SakhtYar dashboard launcher not found (Start-SakhtYar-v4.ps1 or Start-SakhtYar.ps1).'
    }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $dashboardScript -ProjectRoot $ProjectRoot
    if ($LASTEXITCODE -ne 0) { throw "Dashboard launcher exited with code $LASTEXITCODE" }
} catch {
    Status 'ERROR' $_.Exception.Message
    exit 1
}
