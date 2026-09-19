param(
  [string]$RepoPath = "D:\ChatGPT_Projects\SakhtYar\source"
)

$ErrorActionPreference = "Stop"

$SourcePath = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "Applying SakhtYar Phase 0 to: $RepoPath"

if (-not (Test-Path (Join-Path $RepoPath ".git"))) {
  throw "Target path is not a Git repository: $RepoPath"
}

$items = Get-ChildItem $SourcePath -Force |
  Where-Object { $_.Name -notin @("apply-phase0.ps1") }

foreach ($item in $items) {
  $destination = Join-Path $RepoPath $item.Name

  if (Test-Path $destination) {
    Remove-Item $destination -Recurse -Force
  }

  Copy-Item $item.FullName $destination -Recurse -Force
}

Push-Location $RepoPath
try {
  git branch --show-current
  git status --short
  Write-Host ""
  Write-Host "Files applied. Review, then run:"
  Write-Host 'git add -A'
  Write-Host 'git commit -m "feat: implement phase 0 foundation"'
  Write-Host 'git push origin main'
}
finally {
  Pop-Location
}
