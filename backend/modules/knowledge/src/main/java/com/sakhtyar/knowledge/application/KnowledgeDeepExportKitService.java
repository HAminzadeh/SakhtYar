package com.sakhtyar.knowledge.application;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Generates the PowerShell kit used by KnowledgeCorpusExportRunnerService
 * for DEEP knowledge-corpus exports.
 *
 * This class is intentionally self-contained so the generated export kit can
 * run from a clean checkout without committing local export artifacts.
 */
@Service
public class KnowledgeDeepExportKitService {

    private final KnowledgeAdminService adminService;

    public KnowledgeDeepExportKitService(KnowledgeAdminService adminService) {
        this.adminService = adminService;
    }

    public Map<String, Object> exportKit(
            Integer maxBundleMb,
            Integer imageDpi,
            Boolean renderPageImages,
            Boolean ocrFallback,
            Boolean includeOriginals
    ) {
        int bundle = clamp(maxBundleMb == null ? 120 : maxBundleMb, 40, 500);
        int dpi = clamp(imageDpi == null ? 144 : imageDpi, 96, 220);
        boolean render = renderPageImages == null || renderPageImages;
        boolean ocr = ocrFallback == null || ocrFallback;
        boolean originals = includeOriginals == null || includeOriginals;

        Map<String, Object> profile = adminService.profile();
        String sourcePath = String.valueOf(profile.get("sourcePath"));

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "DEEP");
        result.put("sourcePath", sourcePath);
        result.put("maxBundleMb", bundle);
        result.put("imageDpi", dpi);
        result.put("renderPageImages", render);
        result.put("ocrFallback", ocr);
        result.put("includeOriginals", originals);
        result.put("script", buildScript());
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * The runner writes this script into .local/knowledge-export-runtime
     * and executes it with RepoRoot/SourcePath/options.
     *
     * The script deliberately keeps all generated artifacts under
     * .local/knowledge-deep-export so they remain outside source control.
     */
    private static String buildScript() {
        return """
param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [Parameter(Mandatory=$true)][string]$SourcePath,
    [int]$MaxBundleMb = 120,
    [int]$ImageDpi = 144,
    [int]$RenderPageImages = 1,
    [int]$OcrFallback = 1,
    [int]$IncludeOriginals = 1
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host $Message
    Write-Host "============================================================"
}

function Resolve-SourcePath {
    param([string]$Root,[string]$Source)

    if ([System.IO.Path]::IsPathRooted($Source)) {
        return [System.IO.Path]::GetFullPath($Source)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $Root $Source))
}

function Get-SafeRelativePath {
    param([string]$Base,[string]$Full)

    $baseUri = [Uri](([System.IO.Path]::GetFullPath($Base).TrimEnd('\\') + '\\'))
    $fileUri = [Uri]([System.IO.Path]::GetFullPath($Full))
    return [Uri]::UnescapeDataString(
        $baseUri.MakeRelativeUri($fileUri).ToString()
    ).Replace('/','\\')
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function New-ZipFromDirectory {
    param(
        [Parameter(Mandatory=$true)][string]$SourceDirectory,
        [Parameter(Mandatory=$true)][string]$ZipPath
    )

    if (Test-Path $ZipPath) {
        Remove-Item $ZipPath -Force
    }

    Compress-Archive -Path (Join-Path $SourceDirectory '*') -DestinationPath $ZipPath -CompressionLevel Optimal -Force
}

$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)
$ResolvedSource = Resolve-SourcePath -Root $RepoRoot -Source $SourcePath

if (-not (Test-Path -LiteralPath $ResolvedSource -PathType Container)) {
    throw "Knowledge source directory not found: $ResolvedSource"
}

$OutRoot = Join-Path $RepoRoot ".local\\knowledge-deep-export"
$WorkRoot = Join-Path $OutRoot "work"
$CorpusRoot = Join-Path $WorkRoot "corpus"
$DocsRoot = Join-Path $CorpusRoot "documents"
$OriginalsRoot = Join-Path $CorpusRoot "originals"

if (Test-Path $WorkRoot) {
    Remove-Item $WorkRoot -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null
New-Item -ItemType Directory -Force -Path $DocsRoot | Out-Null
if ($IncludeOriginals -ne 0) {
    New-Item -ItemType Directory -Force -Path $OriginalsRoot | Out-Null
}

Write-Step "Starting DEEP export"
Write-Host "Source directory : $ResolvedSource"
Write-Host "Output directory : $OutRoot"
Write-Host "Max bundle MB    : $MaxBundleMb"
Write-Host "Image DPI        : $ImageDpi"
Write-Host "Render pages     : $RenderPageImages"
Write-Host "OCR fallback     : $OcrFallback"
Write-Host "Include originals: $IncludeOriginals"

$extensions = @(
    ".pdf",".doc",".docx",".xls",".xlsx",".ppt",".pptx",
    ".txt",".md",".csv",".json",".xml",".html",".htm",
    ".jpg",".jpeg",".png",".tif",".tiff"
)

$files = @(
    Get-ChildItem -LiteralPath $ResolvedSource -Recurse -File -Force |
        Where-Object {
            $extensions -contains $_.Extension.ToLowerInvariant()
        } |
        Sort-Object FullName
)

Write-Host "Documents found  : $($files.Count)"

$manifest = New-Object System.Collections.Generic.List[object]
$index = 0

foreach ($file in $files) {
    $index++
    $relative = Get-SafeRelativePath -Base $ResolvedSource -Full $file.FullName
    $sha = Get-Sha256 -Path $file.FullName
    $docId = "doc_" + $sha.Substring(0,24)
    $docDir = Join-Path $DocsRoot $docId

    New-Item -ItemType Directory -Force -Path $docDir | Out-Null

    $metadata = [ordered]@{
        documentId = $docId
        relativePath = $relative
        filename = $file.Name
        extension = $file.Extension.ToLowerInvariant()
        sizeBytes = $file.Length
        sha256 = $sha
        lastWriteTimeUtc = $file.LastWriteTimeUtc.ToString("o")
        extractionStatus = "REGISTERED"
        imageDpi = $ImageDpi
        renderPageImages = ($RenderPageImages -ne 0)
        ocrFallback = ($OcrFallback -ne 0)
    }

    $metadata |
        ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $docDir "metadata.json") -Encoding UTF8

    if ($file.Extension.ToLowerInvariant() -in @(".txt",".md",".csv",".json",".xml",".html",".htm")) {
        try {
            $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
            Set-Content -LiteralPath (Join-Path $docDir "text.txt") -Value $text -Encoding UTF8
            $metadata.extractionStatus = "TEXT_EXTRACTED"
        }
        catch {
            $metadata.extractionStatus = "TEXT_READ_FAILED"
            $metadata["error"] = $_.Exception.Message
        }

        $metadata |
            ConvertTo-Json -Depth 8 |
            Set-Content -LiteralPath (Join-Path $docDir "metadata.json") -Encoding UTF8
    }

    if ($IncludeOriginals -ne 0) {
        $originalTarget = Join-Path $OriginalsRoot $relative
        $originalParent = Split-Path -Parent $originalTarget
        New-Item -ItemType Directory -Force -Path $originalParent | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $originalTarget -Force
    }

    $manifest.Add([pscustomobject]$metadata)

    Write-Host ("[{0}/{1}] OK {2}" -f $index, $files.Count, $relative)
}

$manifestPath = Join-Path $CorpusRoot "manifest.json"
$manifest |
    ConvertTo-Json -Depth 10 |
    Set-Content -LiteralPath $manifestPath -Encoding UTF8

$summary = [ordered]@{
    schemaVersion = "1.0"
    exportKind = "DEEP"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    sourcePath = $ResolvedSource
    documentCount = $files.Count
    options = [ordered]@{
        maxBundleMb = $MaxBundleMb
        imageDpi = $ImageDpi
        renderPageImages = ($RenderPageImages -ne 0)
        ocrFallback = ($OcrFallback -ne 0)
        includeOriginals = ($IncludeOriginals -ne 0)
    }
}

$summary |
    ConvertTo-Json -Depth 10 |
    Set-Content -LiteralPath (Join-Path $CorpusRoot "export-summary.json") -Encoding UTF8

Write-Step "Creating master ZIP"

$masterZip = Join-Path $OutRoot "sakhtyar-deep-corpus-master.zip"
New-ZipFromDirectory -SourceDirectory $CorpusRoot -ZipPath $masterZip

$masterFile = Get-Item -LiteralPath $masterZip
$masterSha = Get-Sha256 -Path $masterZip

$bundleIndex = [ordered]@{
    schemaVersion = "1.0"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    maxBundleMb = $MaxBundleMb
    master = [ordered]@{
        filename = $masterFile.Name
        sizeBytes = $masterFile.Length
        sha256 = $masterSha
    }
    parts = @()
}

$bundleIndex |
    ConvertTo-Json -Depth 10 |
    Set-Content -LiteralPath (Join-Path $OutRoot "bundle-index.json") -Encoding UTF8

Write-Step "DEEP export completed"
Write-Host "Master ZIP       : $masterZip"
Write-Host "Master size bytes: $($masterFile.Length)"
Write-Host "Documents        : $($files.Count)"
""";
    }
}