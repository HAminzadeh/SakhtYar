#requires -Version 5.1
<#
SakhtYar self-installing Claude dashboard launcher.
Copies embedded source only when files are missing; never overwrites existing code.
Requires Windows, Java 17+, Maven, internet for first Maven dependency download.
Does not enable TUN or change the system proxy.
#>
[CmdletBinding()]
param(
  [string]$ProjectRoot = '',
  [switch]$InstallOnly,
  [switch]$KeepConsoleOpen
)
$ErrorActionPreference = 'Stop'
function Log([string]$type,[string]$message) {
  $color = switch ($type) { 'OK' {'Green'} 'WARN' {'Yellow'} 'ERROR' {'Red'} default {'Cyan'} }
  Write-Host ("[{0}] {1}" -f $type,$message) -ForegroundColor $color
}
function Fail([string]$message,[int]$code=1) {
  Log 'ERROR' $message
  if ($KeepConsoleOpen) { Read-Host 'Press Enter to exit' | Out-Null }
  exit $code
}
$payload = @'
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
'@
try {
  if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = $PSScriptRoot }
  if ([string]::IsNullOrWhiteSpace($ProjectRoot) -and $MyInvocation.MyCommand.Path) {
    $ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
  }
  if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = (Get-Location).Path }
  $root = [IO.Path]::GetFullPath($ProjectRoot)
  if (-not (Test-Path -LiteralPath $root -PathType Container)) { Fail "Project root missing: $root" 2 }
  if (-not (Test-Path -LiteralPath (Join-Path $root '_claude_launcher') -PathType Container)) {
    Log 'WARN' 'Existing _claude_launcher directory not found. Dashboard can be installed, but launching Claude requires your existing launcher.'
  }
  Log 'INFO' "Project root: $root"
  $module = Join-Path $root 'claude-launcher-ui'
  $archiveBytes = [Convert]::FromBase64String(($payload -replace '\s',''))
  $archiveStream = New-Object IO.MemoryStream(,$archiveBytes)
  Add-Type -AssemblyName System.IO.Compression
  $archive = New-Object IO.Compression.ZipArchive($archiveStream, [IO.Compression.ZipArchiveMode]::Read, $false)
  try {
    $prefix = 'claude-launcher-ui/'
    foreach ($entry in $archive.Entries) {
      if (-not $entry.FullName.StartsWith($prefix,[StringComparison]::Ordinal)) { Fail 'Unexpected embedded archive path.' 3 }
      if ($entry.FullName.EndsWith('/')) { continue }
      $relative = $entry.FullName.Substring($prefix.Length)
      if ($relative -notin @('pom.xml','src/main/java/ir/sakhtyar/launcher/Dashboard.java')) { Fail 'Unexpected embedded file.' 3 }
      $target = Join-Path $module ($relative.Replace('/',[IO.Path]::DirectorySeparatorChar))
      if (Test-Path -LiteralPath $target) { Log 'INFO' "Preserved existing: $target"; continue }
      $folder = Split-Path -Parent $target
      [IO.Directory]::CreateDirectory($folder) | Out-Null
      $source = $entry.Open()
      try {
        $output = New-Object IO.FileStream($target,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try { $source.CopyTo($output) } finally { $output.Dispose() }
      } finally { $source.Dispose() }
      Log 'OK' "Created: $target"
    }
  } finally { $archive.Dispose(); $archiveStream.Dispose() }
  Log 'OK' 'JavaFX module files are present.'
  if ($InstallOnly) { Log 'OK' 'Installation completed (-InstallOnly).'; exit 0 }
  $java = Get-Command java -ErrorAction SilentlyContinue
  if (-not $java) { Fail 'Java not found. Install JDK 17+ and add it to PATH.' 4 }
  Log 'INFO' "Java executable: $($java.Source)"
  $mvn = $null
  foreach ($candidate in @((Join-Path $module 'mvnw.cmd'),(Join-Path $root 'mvnw.cmd'))) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { $mvn = $candidate; break }
  }
  if (-not $mvn) {
    $found = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $found) { $found = Get-Command mvn -ErrorAction SilentlyContinue }
    if ($found) { $mvn = $found.Source }
  }
  if (-not $mvn) { Fail 'Maven not found (mvn / mvnw.cmd). Install Maven.' 5 }
  $env:SAKHTYAR_PROJECT_ROOT = $root
  $env:SAKHTYAR_CLAUDE_LAUNCHER = Join-Path $root '_claude_launcher\Start-Claude-Free-Lite.ps1'
  Log 'INFO' 'Launching dashboard. TUN will not be enabled.'
  Push-Location -LiteralPath $module
  try {
    & $mvn '-f' (Join-Path $module 'pom.xml') 'javafx:run'
    $exitCode = $LASTEXITCODE
  } finally { Pop-Location }
  if ($exitCode -ne 0) { Fail "Maven/JavaFX exit code: $exitCode" 6 }
  Log 'OK' 'Dashboard closed.'
} catch { Fail $_.Exception.Message 99 }
if ($KeepConsoleOpen) { Read-Host 'Press Enter to exit' | Out-Null }
