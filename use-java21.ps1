param(
    [string]$JavaHome
)

$ErrorActionPreference = "Stop"

function Resolve-Jdk21 {
    param([string]$PreferredPath)

    if ($PreferredPath) {
        $javaExe = Join-Path $PreferredPath "bin\java.exe"
        if (Test-Path $javaExe) {
            return (Resolve-Path $PreferredPath).Path
        }

        throw "JDK 21 was not found at: $PreferredPath"
    }

    $patterns = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Amazon Corretto\jdk21*",
        "C:\Program Files\BellSoft\LibericaJDK-21*"
    )

    foreach ($pattern in $patterns) {
        $candidate = Get-ChildItem $pattern -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            Select-Object -First 1

        if ($candidate) {
            $javaExe = Join-Path $candidate.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                return $candidate.FullName
            }
        }
    }

    throw @"
JDK 21 was not found.

Install it without removing Java 8:
    winget install EclipseAdoptium.Temurin.21.JDK

Then close and reopen PowerShell and run this script again.
"@
}

$resolvedJavaHome = Resolve-Jdk21 -PreferredPath $JavaHome

# Remove any existing Java bin entries from PATH for this PowerShell session only.
$pathParts = $env:Path -split ';' | Where-Object {
    $_ -and
    ($_ -notmatch '\\Java\\jdk[^\\]*\\bin$') -and
    ($_ -notmatch '\\Eclipse Adoptium\\jdk[^\\]*\\bin$') -and
    ($_ -notmatch '\\Microsoft\\jdk[^\\]*\\bin$') -and
    ($_ -notmatch '\\Amazon Corretto\\jdk[^\\]*\\bin$') -and
    ($_ -notmatch '\\LibericaJDK[^\\]*\\bin$')
}

$env:JAVA_HOME = $resolvedJavaHome
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$($pathParts -join ';')"

Write-Host ""
Write-Host "Java 21 is active for THIS PowerShell window only." -ForegroundColor Green
Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan
Write-Host ""

& java -version
Write-Host ""
& javac -version
Write-Host ""
& mvn -version
