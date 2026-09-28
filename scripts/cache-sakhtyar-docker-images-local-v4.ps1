$ErrorActionPreference = "Stop"

$root = Get-Location
$imagesDir = Join-Path $root ".local\docker-images"
$manifestPath = Join-Path $imagesDir "manifest.json"
$gitignorePath = Join-Path $root ".gitignore"
$composePath = Join-Path $root "compose.spring-local.yml"

if (-not (Test-Path $composePath)) {
    throw "Run this script from the SakhtYar repository root."
}

New-Item -ItemType Directory -Force -Path $imagesDir | Out-Null

function Invoke-DockerText([string]$Arguments) {
    $output = cmd.exe /d /c "docker $Arguments 2>&1"
    $code = $LASTEXITCODE
    return [PSCustomObject]@{
        ExitCode = $code
        Output   = @($output)
    }
}

function Test-LocalImage([string]$Image) {
    cmd.exe /d /c "docker image inspect `"$Image`" >nul 2>nul"
    return ($LASTEXITCODE -eq 0)
}

function Save-Image([string]$Image, [string]$Service) {
    if (-not (Test-LocalImage $Image)) {
        Write-Host "SKIP (not available locally): $Image" -ForegroundColor Yellow
        return $null
    }

    $idResult = Invoke-DockerText "image inspect `"$Image`" --format `"{{.Id}}`""
    if ($idResult.ExitCode -ne 0) {
        throw "Could not inspect image $Image"
    }
    $id = ($idResult.Output | Select-Object -First 1).Trim()

    $digestResult = Invoke-DockerText "image inspect `"$Image`" --format `"{{json .RepoDigests}}`""
    $digest = ($digestResult.Output -join "`n").Trim()

    $safeName = ($Image -replace '[\\/:@]', '_')
    $tarName = "$safeName.tar"
    $tarPath = Join-Path $imagesDir $tarName

    if (Test-Path $tarPath) {
        Write-Host "EXISTS: $Image" -ForegroundColor DarkGray
    } else {
        Write-Host "Saving $Image ..." -ForegroundColor Cyan
        docker save -o $tarPath $Image
        if ($LASTEXITCODE -ne 0) {
            throw "docker save failed for $Image"
        }
    }

    $sha256 = (Get-FileHash -Algorithm SHA256 $tarPath).Hash.ToLowerInvariant()

    return [PSCustomObject]@{
        service    = $Service
        image      = $Image
        imageId    = $id
        repoDigest = $digest
        file       = $tarName
        sha256     = $sha256
        sizeBytes  = (Get-Item $tarPath).Length
    }
}

$ignoreEntry = "/.local/docker-images/"
if (-not (Test-Path $gitignorePath)) {
    [System.IO.File]::WriteAllText(
        $gitignorePath,
        "$ignoreEntry`r`n",
        (New-Object System.Text.UTF8Encoding($false))
    )
} else {
    $gitignore = Get-Content $gitignorePath -Raw
    if ($gitignore -notmatch [regex]::Escape($ignoreEntry)) {
        Add-Content -Path $gitignorePath -Value "`r`n$ignoreEntry"
    }
}

Write-Host ""
Write-Host "=== SakhtYar local Docker image cache ===" -ForegroundColor Cyan
Write-Host "Reading compose images..." -ForegroundColor Cyan

$configJson = docker compose -f $composePath config --format json
if ($LASTEXITCODE -ne 0) {
    throw "Could not read compose configuration."
}

$config = $configJson | ConvertFrom-Json
$entries = @()

foreach ($prop in $config.services.PSObject.Properties) {
    $serviceName = $prop.Name
    $service = $prop.Value

    if ($null -ne $service.image -and -not [string]::IsNullOrWhiteSpace([string]$service.image)) {
        $entries += [PSCustomObject]@{
            Service = $serviceName
            Image   = [string]$service.image
        }
    }
}

Write-Host "Discovering existing local infrastructure images..." -ForegroundColor Cyan

$existing = docker image ls --format "{{.Repository}}|{{.Tag}}|{{.ID}}" 2>$null
foreach ($line in $existing) {
    $parts = $line -split "\|", 3
    if ($parts.Count -ne 3) { continue }

    $repo = $parts[0]
    $tag = $parts[1]

    $isRelevant = $repo -match "(?i)sakhtyar|minio|postgres|postgis|redis|prometheus|grafana|loki|tempo|alloy|node"
    $hasTag = $tag -ne "<none>"

    if ($isRelevant -and $hasTag) {
        $entries += [PSCustomObject]@{
            Service = "discovered-local"
            Image   = "${repo}:${tag}"
        }
    }
}

$dedup = @{}
foreach ($entry in $entries) {
    if (-not $dedup.ContainsKey($entry.Image)) {
        $dedup[$entry.Image] = $entry
    }
}
$entries = $dedup.Values | Sort-Object Image

$manifest = @()

foreach ($entry in $entries) {
    $saved = Save-Image -Image $entry.Image -Service $entry.Service
    if ($null -ne $saved) {
        $manifest += $saved
    }
}

$minioCandidate = "quay.io/minio/minio:latest"
$knownGoodTag = "sakhtyar/minio-community:known-good-local"
$tempName = "sakhtyar-minio-local-verify"

Write-Host ""
Write-Host "=== Verifying existing local MinIO Community image ===" -ForegroundColor Cyan

if (Test-LocalImage $minioCandidate) {
    $tempExists = docker ps -a --filter "name=^/$tempName$" --format "{{.Names}}" 2>$null
    if ($tempExists -eq $tempName) {
        docker rm -f $tempName *> $null
    }

    Write-Host "Testing LOCAL image only: $minioCandidate" -ForegroundColor Cyan

    docker run --pull=never -d `
        --name $tempName `
        -e MINIO_ROOT_USER=verifyadmin `
        -e MINIO_ROOT_PASSWORD=verify-password-123456 `
        -p 19000:9000 `
        -p 19001:9001 `
        $minioCandidate `
        server /data --console-address ":9001" *> $null

    if ($LASTEXITCODE -ne 0) {
        throw "Existing local MinIO Community image could not start."
    }

    try {
        $healthy = $false

        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 1

            try {
                $response = Invoke-WebRequest `
                    -Uri "http://127.0.0.1:19000/minio/health/live" `
                    -UseBasicParsing `
                    -TimeoutSec 2

                if ($response.StatusCode -eq 200) {
                    $healthy = $true
                    break
                }
            } catch {
            }

            $runningResult = Invoke-DockerText "inspect -f `"{{.State.Running}}`" $tempName"
            if ($runningResult.ExitCode -ne 0 -or (($runningResult.Output -join "").Trim() -ne "true")) {
                break
            }
        }

        # Important: MinIO writes normal INFO startup messages to stderr.
        # Use cmd.exe to merge stdout/stderr without PowerShell turning them
        # into terminating NativeCommandError records.
        $logsResult = Invoke-DockerText "logs $tempName"
        $logs = $logsResult.Output
        $logsText = ($logs -join "`n")

        Write-Host ""
        Write-Host "Temporary MinIO logs:" -ForegroundColor DarkCyan
        $logs | Select-Object -Last 25 | ForEach-Object { Write-Host $_ }

        if ($logsResult.ExitCode -ne 0) {
            throw "Could not read temporary MinIO logs."
        }

        if (-not $healthy) {
            throw "Local MinIO Community candidate did not become healthy."
        }

        if ($logsText -match "(?i)license.{0,100}(expired|required|invalid|missing)|expired.{0,100}license") {
            throw "License-related message detected in local MinIO candidate."
        }

        Write-Host ""
        Write-Host "PASS: existing local MinIO Community image is healthy and has no license gate in startup logs." -ForegroundColor Green

        docker tag $minioCandidate $knownGoodTag
        if ($LASTEXITCODE -ne 0) {
            throw "Could not tag known-good MinIO image."
        }

        $knownGood = Save-Image -Image $knownGoodTag -Service "minio-known-good"
        if ($null -ne $knownGood) {
            $manifest += $knownGood
        }
    }
    finally {
        $tempExists = docker ps -a --filter "name=^/$tempName$" --format "{{.Names}}" 2>$null
        if ($tempExists -eq $tempName) {
            docker rm -f $tempName *> $null
        }
    }
} else {
    Write-Host "No local quay.io/minio/minio:latest image found; MinIO verification skipped." -ForegroundColor Yellow
}

$manifest = $manifest | Sort-Object image -Unique
$manifestJson = $manifest | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText(
    $manifestPath,
    $manifestJson,
    (New-Object System.Text.UTF8Encoding($false))
)

Write-Host ""
Write-Host "DONE." -ForegroundColor Green
Write-Host "Local Docker image cache:" -ForegroundColor Green
Write-Host "  $imagesDir"
Write-Host "Manifest:" -ForegroundColor Green
Write-Host "  $manifestPath"
Write-Host ""
Write-Host "Missing images were skipped instead of aborting." -ForegroundColor Yellow
Write-Host "No Docker volume was deleted." -ForegroundColor Yellow
