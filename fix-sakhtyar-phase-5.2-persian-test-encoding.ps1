param(
    [string]$ExpectedBranch = "phase-5.2-knowledge-platform"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 78) -ForegroundColor DarkCyan
    Write-Host (" " + $Text) -ForegroundColor Cyan
    Write-Host ("=" * 78) -ForegroundColor DarkCyan
}

Write-Step "SakhtYar Phase 5.2 - Fix Persian test encoding"

$RepoRoot = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($RepoRoot)) {
    throw "Run this script from inside the SakhtYar repository."
}
$RepoRoot = $RepoRoot.Trim()
Set-Location $RepoRoot

$currentBranch = (& git branch --show-current).Trim()
if ($currentBranch -ne $ExpectedBranch) {
    throw "Current branch is '$currentBranch'. Expected '$ExpectedBranch'."
}

$testRel = "backend/modules/knowledge/src/test/java/com/sakhtyar/knowledge/application/KnowledgeTextNormalizerTest.java"
$testFile = Join-Path $RepoRoot $testRel

if (-not (Test-Path $testFile)) {
    throw "Test file not found: $testRel"
}

Write-Step "1/4 - Backing up current test"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupDir = Join-Path $RepoRoot ".local\patch-backups\phase-5.2-encoding-fix-$stamp"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
Copy-Item $testFile (Join-Path $backupDir "KnowledgeTextNormalizerTest.java")
Write-Host "Backup: $backupDir" -ForegroundColor Green

Write-Step "2/4 - Rewriting test with ASCII-safe Unicode escapes"

$test = @'
package com.sakhtyar.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class KnowledgeTextNormalizerTest {

    @Test
    void normalizesArabicVariantsAndWhitespace() {
        assertEquals(
                "\u0645\u0634\u0627\u0631\u06A9\u062A \u062F\u0631 \u0633\u0627\u062E\u062A",
                KnowledgeTextNormalizer.normalize(
                        "  \u0645\u0634\u0627\u0631\u0643\u062A\u200C \u062F\u0631   \u0633\u0627\u062E\u062A  "
                )
        );
    }

    @Test
    void removesArabicDiacritics() {
        assertEquals(
                "\u0645\u0627\u0644\u06A9",
                KnowledgeTextNormalizer.normalize(
                        "\u0645\u0627\u0644\u0650\u06A9"
                )
        );
    }

    @Test
    void handlesNullAndBlankInput() {
        assertEquals("", KnowledgeTextNormalizer.normalize(null));
        assertEquals("", KnowledgeTextNormalizer.normalize("   "));
    }
}
'@

[System.IO.File]::WriteAllText(
    $testFile,
    $test,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Rewrote $testRel using Java Unicode escapes only." -ForegroundColor Green

Write-Step "3/4 - Running knowledge module tests"
Push-Location (Join-Path $RepoRoot "backend")
try {
    & mvn -pl modules/knowledge -am test
    if ($LASTEXITCODE -ne 0) {
        throw "Knowledge module tests failed."
    }

    Write-Step "Running full backend test suite"
    & mvn test
    if ($LASTEXITCODE -ne 0) {
        throw "Full Maven test suite failed."
    }
}
finally {
    Pop-Location
}

Write-Step "4/4 - Result"
Write-Host "Current branch:" -ForegroundColor Cyan
& git branch --show-current

Write-Host ""
Write-Host "Changed files:" -ForegroundColor Cyan
& git status --short

Write-Host ""
Write-Host "Encoding fix applied successfully." -ForegroundColor Green
Write-Host "Next: run the backend from IntelliJ and confirm Flyway V10 applies successfully."
