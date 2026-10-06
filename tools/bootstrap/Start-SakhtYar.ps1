param([string]$RepoRoot=(Split-Path -Parent (Split-Path -Parent $PSScriptRoot)),[switch]$CheckOnly)
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
function Log([string]$Tag,[string]$Message){$Line=("["+$Tag+"] "+$Message);Write-Host $Line;$script:Lines.Add($Line)|Out-Null}
function Has([string]$Name){return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)}
function Run([string]$Exe,[string[]]$CommandArguments,[string]$Label){Log "CHECK" $Label;$Old=$ErrorActionPreference;try{$ErrorActionPreference="Continue"; & $Exe @CommandArguments;$Code=$LASTEXITCODE}finally{$ErrorActionPreference=$Old};if($Code -ne 0){throw "$Label failed ($Code)"}}
function Winget([string]$Id,[string]$Label){if($CheckOnly){Log "MISSING" ($Label+" (check-only)");return};if(-not(Has "winget.exe")){throw "$Label missing; winget unavailable"};Log "INSTALL" $Label;Run (Get-Command winget.exe).Source @("install","--id",$Id,"--exact","--silent","--accept-package-agreements","--accept-source-agreements") $Label}
$Root=[IO.Path]::GetFullPath($RepoRoot)
$Repo=Join-Path $Root ".sakhtyar/repository"
$Runtime=Join-Path $Root ".sakhtyar/runtime"
$WheelCache=Join-Path $Repo "python/wheels"
$Tessdata=Join-Path $Repo "tesseract/tessdata"
$Venv=Join-Path $Root ".local/venv-persian-intelligence/Scripts/python.exe"
$script:Lines=New-Object 'System.Collections.Generic.List[string]'
New-Item -ItemType Directory -Force -Path $Repo,$Runtime,$WheelCache,$Tessdata|Out-Null
Log "CHECK" "SakhtYar desired-state environment"

$Core=@(@("git.exe","Git.Git","Git"),@("docker.exe","Docker.DockerDesktop","Docker Desktop"),@("java.exe","EclipseAdoptium.Temurin.21.JDK","Java 21"),@("node.exe","OpenJS.NodeJS","Node.js"),@("python.exe","Python.Python.3.13","Python"))
foreach($Item in $Core){if(Has $Item[0]){Log "OK" $Item[2]}else{Winget $Item[1] $Item[2]}}
if(-not(Test-Path $Venv)){
 if($CheckOnly){Log "MISSING" "Project Python venv"}else{Run (Get-Command python.exe).Source @("-m","venv",(Join-Path $Root ".local/venv-persian-intelligence")) "Create project Python venv"}
}
if(Test-Path $Venv){
 $Deps=@(@("pymupdf","PyMuPDF==1.26.4"),@("docx","python-docx==1.2.0"),@("pptx","python-pptx==1.0.2"),@("pytesseract","pytesseract==0.3.13"),@("PIL","Pillow==11.3.0"))
 foreach($Dep in $Deps){
  $Old=$ErrorActionPreference;try{$ErrorActionPreference="Continue"; & $Venv -c ("import "+$Dep[0]) *> $null;$Present=$LASTEXITCODE -eq 0}finally{$ErrorActionPreference=$Old}
  if($Present){Log "OK" ("Python "+$Dep[0]);continue}
  if($CheckOnly){Log "MISSING" ("Python "+$Dep[0]);continue}
  Log "DOWNLOAD" ($Dep[1]+" -> local wheel repository")
  Run $Venv @("-m","pip","download","--dest",$WheelCache,$Dep[1]) ("Cache "+$Dep[1])
  Log "INSTALL" $Dep[1]
  Run $Venv @("-m","pip","install","--no-index","--find-links",$WheelCache,$Dep[1]) ("Install cached "+$Dep[1])
 }
}
$Tesseract=$null
$Cmd=Get-Command tesseract.exe -ErrorAction SilentlyContinue
if($Cmd){$Tesseract=$Cmd.Source}
if(-not $Tesseract -and (Test-Path "C:/Program Files/Tesseract-OCR/tesseract.exe")){$Tesseract="C:/Program Files/Tesseract-OCR/tesseract.exe"}
if(-not $Tesseract){Winget "UB-Mannheim.TesseractOCR" "Tesseract OCR";if(Test-Path "C:/Program Files/Tesseract-OCR/tesseract.exe"){$Tesseract="C:/Program Files/Tesseract-OCR/tesseract.exe"}}
$Fas=Join-Path $Tessdata "fas.traineddata"
if(-not(Test-Path $Fas)){
 if($CheckOnly){Log "MISSING" "Persian OCR fas.traineddata"}
 else{
  Log "DOWNLOAD" "Persian OCR data -> project repository"
  Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/tesseract-ocr/tessdata_fast/raw/main/fas.traineddata" -OutFile $Fas
 }
}else{Log "OK" "Persian OCR data (project-local)"}
# Never write to Program Files. The processor receives project-local tessdata.
$EnvFile=Join-Path $Runtime "bootstrap.env"
$ExeValue=if($Tesseract){$Tesseract}else{""}
[IO.File]::WriteAllText($EnvFile,("SAKHTYAR_TESSERACT_EXE="+$ExeValue+"`nSAKHTYAR_TESSDATA_DIR="+$Tessdata+"`n"),(New-Object Text.UTF8Encoding($false)))
if($Tesseract -and (Test-Path $Fas)){
 $env:SAKHTYAR_TESSERACT_EXE=$Tesseract;$env:SAKHTYAR_TESSDATA_DIR=$Tessdata;Remove-Item Env:TESSDATA_PREFIX -ErrorAction SilentlyContinue
 $Lang=& $Tesseract --tessdata-dir $Tessdata --list-langs 2>&1 | Out-String
 if($Lang -match "(?m)^fas\s*$"){Log "OK" "Tesseract Persian language verified"}else{Log "FAIL" "Tesseract cannot load project-local fas data"}
}
if(Has "docker.exe"){
 $Old=$ErrorActionPreference;try{$ErrorActionPreference="Continue"; & docker.exe info *> $null;$DockerReady=$LASTEXITCODE -eq 0}finally{$ErrorActionPreference=$Old}
 if($DockerReady){Log "OK" "Docker engine"}else{Log "WARN" "Docker CLI exists but engine is not ready"}
}
$Status=[ordered]@{checkedAt=(Get-Date).ToString("o");checkOnly=[bool]$CheckOnly;repository=$Repo;items=@($script:Lines)}
$Status|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $Runtime "automation-status.json") -Encoding UTF8
$script:Lines|Set-Content -LiteralPath (Join-Path $Runtime "automation.log") -Encoding UTF8
Log "OK" "SakhtYar automation bootstrap complete"