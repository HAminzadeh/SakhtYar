#requires -Version 5.1
[CmdletBinding()]
param([ValidateSet('Preflight','Launch','Resume','Choose','Usage','Diagnostics')][string]$Action='Preflight', [Parameter(Mandatory)][string]$Root, [Parameter(Mandatory)][string]$Settings, [string]$ProfileName="Claude-Free")
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
function Say($kind,$msg){
 $line="[$kind] $msg"
 Write-Output $line
 if($script:logFile){try{Add-Content -LiteralPath $script:logFile -Value $line -Encoding UTF8}catch{}}
}
function Block($message){ Say 'ERROR' $message; exit 2 }
function Enabled([string]$name){ if($config.Contains($name)){ return [bool]$config[$name] }; return $true }
function CommandPath($name){$c=Get-Command $name -ErrorAction SilentlyContinue; if($c){return $c.Source}; return $null}
function LoadSettings {
  $h=@{}
  if(Test-Path -LiteralPath $Settings){
    $raw=Get-Content -LiteralPath $Settings -Raw | ConvertFrom-Json
    foreach($p in $raw.PSObject.Properties){$h[$p.Name]=[bool]$p.Value}
  }
  return $h
}
$config=LoadSettings
if(-not(Test-Path -LiteralPath $Root -PathType Container)){Block "Invalid root: $Root"}
$rootPath=(Resolve-Path -LiteralPath $Root).Path
$state=Join-Path $rootPath '_claude_launcher'
New-Item -ItemType Directory -Force -Path $state | Out-Null
$logDir=Join-Path $state 'logs'
if(Enabled 'Write logs'){New-Item -ItemType Directory -Force -Path $logDir | Out-Null; $script:logFile=Join-Path $logDir ('run-'+(Get-Date -Format 'yyyyMMdd')+'.log')}
Say 'INFO' "Root: $rootPath"
$proxy=''
if(Enabled 'Check v2rayN process'){
  if(-not(Get-Process -Name 'v2rayN' -ErrorAction SilentlyContinue)){Block 'v2rayN is not running. Start it before Claude.'}
  Say 'OK' 'v2rayN process detected'
}
$ports=@()
if($env:SAKHTYAR_PROXY_PORT -match '^\d{2,5}$'){$ports+=([int]$env:SAKHTYAR_PROXY_PORT)}
# v2rayN mixed HTTP/SOCKS port is commonly 10808; user can override using SAKHTYAR_PROXY_PORT.
$ports+=@(10808,10809,10810)
foreach($port in ($ports | Select-Object -Unique)){
 if($port -lt 1 -or $port -gt 65535){continue}
 try{
  $tcp=New-Object Net.Sockets.TcpClient
  $task=$tcp.ConnectAsync('127.0.0.1',$port)
  if($task.Wait(900) -and $tcp.Connected){$proxy="http://127.0.0.1:$port"; $tcp.Close(); break}
  $tcp.Close()
 }catch{}
}
$networkChecks=(Enabled 'Test proxy HTTPS') -or (Enabled 'Check German exit IP') -or (Enabled 'Set Claude proxy') -or (Enabled 'Monitor connection')
if($networkChecks -and -not $proxy){Block 'Local HTTP proxy port not detected; set SAKHTYAR_PROXY_PORT to v2rayN mixed/HTTP port.'}
if($proxy){Say 'INFO' "Proxy candidate: $proxy"}
function CheckProxy {
 param([switch]$RequireGermany)
 try{
  $response=Invoke-RestMethod -Uri 'https://ipwho.is/' -Proxy $proxy -TimeoutSec 12 -ErrorAction Stop
  if($response.success -eq $false){throw 'Geo-IP provider returned an error'}
  if(-not $response.ip -or -not $response.country_code){throw 'IP response incomplete'}
  Say 'OK' "Proxy IP: $($response.ip); Country: $($response.country_code)"
  if($RequireGermany -and $response.country_code -ne 'DE'){Block "Proxy exit country is $($response.country_code), not DE"}
 }catch{Block "Proxy HTTPS/geolocation failed: $($_.Exception.Message)"}
}
if((Enabled 'Test proxy HTTPS') -or (Enabled 'Check German exit IP')) {CheckProxy -RequireGermany:(Enabled 'Check German exit IP')}
if(Enabled 'Verify project path'){Say 'OK' 'Project directory found'}
if(Enabled 'Check Java Node and npm'){
 foreach($tool in @('java','node','npm')) {if(CommandPath $tool){Say 'OK' "$tool available"} else {Say 'WARN' "$tool not found"}}
}
$claude=CommandPath 'claude.cmd'; if(-not $claude){$claude=CommandPath 'claude'}
if((Enabled 'Check Claude CLI') -and -not $claude){Block 'Claude CLI not found. Install Claude Code and ensure claude is on PATH.'}
if($claude){Say 'OK' "Claude executable: $claude"}
if((Enabled 'Check versions') -and $claude){try{$ver= & $claude --version 2>$null; Say 'INFO' "Claude version: $ver"}catch{Say 'WARN' 'Unable to read Claude version'}}
if(Enabled 'Run Java tests'){
 if(Test-Path (Join-Path $rootPath 'pom.xml')) {Say 'INFO' 'Maven project detected. Java tests are NOT automatically executed during launch to avoid unexpected build side effects.'}
 elseif(Test-Path (Join-Path $rootPath 'gradlew.bat')){Say 'INFO' 'Gradle project detected. Java tests are NOT automatically executed during launch.'}
 else{Say 'INFO' 'No root Maven/Gradle build detected'}
}
if(Enabled 'Check CCR provider'){
 if((CommandPath 'ccr.cmd') -or (CommandPath 'ccr')){Say 'OK' 'CCR installed'}else{Block 'CCR CLI not found. Install/configure CCR first.'}
}
if(Enabled 'Check authentication'){
 if($env:ANTHROPIC_API_KEY){Say 'INFO' 'ANTHROPIC_API_KEY configured; API billing may apply'}
 else{Say 'INFO' 'No ANTHROPIC_API_KEY present; Claude may use account login or configured provider'}
}
if(Enabled 'Check CLAUDE.md'){
 if(Test-Path (Join-Path $rootPath 'CLAUDE.md')){Say 'OK' 'CLAUDE.md present'}else{Say 'WARN' 'CLAUDE.md absent'}
}
if(Enabled 'Inspect Git changes'){
 if(CommandPath 'git'){
  $summary=& git -C $rootPath status --porcelain 2>$null
  if($LASTEXITCODE -eq 0){Say 'INFO' "Git changed entries: $(@($summary).Count)"}else{Say 'WARN' 'Not a Git checkout'}
 }
}
if(Enabled 'Build handoff'){
 $handoff=Join-Path $state 'Build-Handoff.ps1'
 if(Test-Path -LiteralPath $handoff){
  Say 'INFO' 'Building project handoff...'
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $handoff
  if($LASTEXITCODE -ne 0){Block "Build-Handoff failed ($LASTEXITCODE)"}
 } else {Say 'WARN' 'Build-Handoff.ps1 not found; skipping (not creating a replacement)'}
}
if($Action -eq 'Diagnostics'){
 $target=Join-Path $logDir ('diagnostics-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.txt')
 New-Item -ItemType Directory -Force -Path $logDir | Out-Null
 @("Project: $rootPath","Time: $(Get-Date -Format o)","ProxyCandidate: $proxy","ClaudeInstalled: $([bool]$claude)","v2rayNRunning: $([bool](Get-Process v2rayN -ErrorAction SilentlyContinue))") | Set-Content -LiteralPath $target -Encoding UTF8
 Say 'OK' "Diagnostics saved: $target";exit 0
}
if($Action -eq 'Usage'){
  Say 'USAGE' 'Usage data is not exposed by this launcher. Use CCR management UI for provider-specific token and cost reports; figures are not estimated.'
  Say 'INFO' 'Session histories are managed by Claude Code and are not token usage records.'
  exit 0
}
if($Action -eq 'Preflight'){Say 'OK' 'Preflight finished';exit 0}
if(-not $proxy){Block 'Refusing to launch without local proxy validation.'}
$ccr=CommandPath 'ccr.cmd'; if(-not $ccr){$ccr=CommandPath 'ccr'}
if(-not $ccr){Block 'ccr command missing: Claude-Free profile needs CCR.'}
if($ProfileName -notmatch '^[\p{L}\p{N}_. -]{1,80}$'){Block 'Invalid profile name.'}
# A fresh terminal owns interactive input. CCR receives Claude flags after --.
# Resume: continue most recent for project. Choose: native Claude session picker.
# Launch: new session, does not erase historical sessions.
$parts=@('"'+$ccr+'"','"'+$ProfileName+'"','cli')
if($Action -eq 'Resume'){$parts+=@('--','--continue')}
if($Action -eq 'Choose'){$parts+=@('--','--resume')}
$inner=($parts -join ' ')
# Capture proxy only in spawned process, not global Windows environment.
$command='set "HTTP_PROXY='+$proxy+'" && set "HTTPS_PROXY='+$proxy+'" && set "ALL_PROXY='+$proxy+'" && '+$inner
try {
  $p=Start-Process -FilePath 'cmd.exe' -ArgumentList @('/d','/k',$command) -WorkingDirectory $rootPath -PassThru
  Say 'OK' "Opened interactive Claude terminal; profile=$ProfileName; action=$Action; process=$($p.Id)"
  Say 'INFO' 'Terminal remains independent of dashboard. Session history is preserved by Claude Code.'
  exit 0
} catch {Block "Failed to start CCR terminal: $($_.Exception.Message)"}
