#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ProjectRoot = '',
    [switch]$RunAfter
)
$ErrorActionPreference = 'Stop'

function Info([string]$msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Good([string]$msg) { Write-Host "[ OK ] $msg" -ForegroundColor Green }
function AssertReplacement([string]$name, [string]$text, [string]$old, [string]$new) {
    $old = $old.Replace("`r`n", "`n"); $new = $new.Replace("`r`n", "`n")
    if ($text.Contains($new)) { Good "$name already patched"; return $text }
    if (-not $text.Contains($old)) { throw "Unknown source version at $name. Nothing was changed. Compare your local Java files with the prior Java-first package." }
    return $text.Replace($old, $new)
}
function WriteUtf8NoBom([string]$path,[string]$contents) {
    [System.IO.File]::WriteAllText($path, $contents, (New-Object System.Text.UTF8Encoding($false)))
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = $PSScriptRoot }
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = (Get-Location).Path }
$root = [IO.Path]::GetFullPath($ProjectRoot)
$module = Join-Path $root 'claude-launcher-ui'
$javaDir = Join-Path $module 'src\main\java\ir\sakhtyar\launcher'
$corePath = Join-Path $javaDir 'LauncherCore.java'
$dashboardPath = Join-Path $javaDir 'Dashboard.java'
$servicePath = Join-Path $javaDir 'ProxyHealthService.java'
if (-not (Test-Path -LiteralPath $corePath -PathType Leaf)) { throw "LauncherCore.java not found: $corePath" }
if (-not (Test-Path -LiteralPath $dashboardPath -PathType Leaf)) { throw "Dashboard.java not found: $dashboardPath" }
Info "Project: $root"
$coreRaw = [IO.File]::ReadAllText($corePath)
$dashRaw = [IO.File]::ReadAllText($dashboardPath)
$coreCrlf = $coreRaw.Contains("`r`n")
$dashCrlf = $dashRaw.Contains("`r`n")
$coreText = $coreRaw.Replace("`r`n", "`n")
$dashText = $dashRaw.Replace("`r`n", "`n")
$oldV2 = @'
    public boolean v2rayRunning() {return ProcessHandle.allProcesses().anyMatch(p->p.info().command().orElse("").toLowerCase(Locale.ROOT).contains("v2rayn.exe"));}
'@
$newV2 = @'
    /** Optional Windows process inspection: absence must never override a verified proxy connection. */
    public boolean v2rayRunning() {
        boolean found = ProcessHandle.allProcesses().anyMatch(p -> p.info().command()
            .map(path -> path.replace('\\', '/').toLowerCase(Locale.ROOT).endsWith("/v2rayn.exe"))
            .orElse(false));
        if (found) return true;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return false;
        try {
            Process p = new ProcessBuilder("tasklist.exe", "/FI", "IMAGENAME eq v2rayN.exe", "/FO", "CSV", "/NH")
                .redirectErrorStream(true).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) { p.destroyForcibly(); return false; }
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return output.toLowerCase(Locale.ROOT).contains("\"v2rayn.exe\"");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }
'@
$oldCountry = @'
    public String proxyCountry() throws Exception {
        if(!proxyListening()) throw new IOException("HTTP/Mixed proxy is not listening");
        HttpClient client=HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1",port("SAKHTYAR_PROXY_PORT",10808)))).connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest req=HttpRequest.newBuilder(URI.create("https://ipwho.is/")).timeout(Duration.ofSeconds(9)).GET().build();
        HttpResponse<String> response=client.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()!=200) throw new IOException("IP check HTTP "+response.statusCode());
        var tree=json.readTree(response.body());
        if(!tree.path("success").asBoolean(false)) throw new IOException("IP check failed");
        return tree.path("country_code").asText("");
    }
'@
$newCountry = @'
    public String proxyCountry() throws Exception {
        if (!proxyListening()) throw new IOException("HTTP/Mixed proxy is not listening at 127.0.0.1:" + port("SAKHTYAR_PROXY_PORT",10808));
        return ProxyHealthService.check(port("SAKHTYAR_PROXY_PORT",10808), json).countryCode();
    }
'@
$oldPreflight = @'
    public List<String> preflight() throws Exception {
        List<String> results=new ArrayList<>();
        if(!v2rayRunning())throw new IOException("v2rayN is not running");results.add("v2rayN: OK");
        if(!proxyListening())throw new IOException("Proxy port not available");results.add("Proxy: OK");
        String c=proxyCountry();if(!"DE".equalsIgnoreCase(c))throw new IOException("Proxy exit is not Germany: "+c);results.add("Exit: Germany");
        if(!routerListening())throw new IOException("CCR router is not ready");results.add("CCR: OK");
        return results;
    }
'@
$newPreflight = @'
    public List<String> preflight() throws Exception {
        List<String> results = new ArrayList<>();
        // Process paths are often hidden on Windows; HTTP proxy connectivity is authoritative.
        boolean processFound = v2rayRunning();
        if (!proxyListening()) throw new IOException("HTTP/Mixed proxy port is not reachable: 127.0.0.1:" + port("SAKHTYAR_PROXY_PORT",10808));
        ProxyHealthService.Result exit = ProxyHealthService.check(port("SAKHTYAR_PROXY_PORT",10808), json);
        if (!"DE".equalsIgnoreCase(exit.countryCode())) {
            throw new IOException("Proxy exit is not Germany: " + exit.countryCode() + " (IP " + exit.ip() + ")");
        }
        results.add(processFound ? "v2rayN process: detected" : "v2rayN process: not visible (proxy independently verified)");
        results.add("HTTP proxy: 127.0.0.1:" + port("SAKHTYAR_PROXY_PORT",10808) + " OK");
        results.add("Exit: Germany [DE] | IP: " + exit.ip());
        if (!routerListening()) throw new IOException("CCR router is not ready on port " + port("SAKHTYAR_CCR_PORT",3456));
        results.add("CCR: OK");
        return results;
    }
'@
$oldStatus = @'
        connection.setText(tr("روتر CCR آماده است. برای تست اتصال کلیک کنید.","CCR router ready. Click Check Connection."));
'@
$newStatus = @'
        connection.setText(tr("اتصال هنوز بررسی نشده است؛ روی «بررسی اتصال» بزنید.","Connection has not been tested yet. Click Check connection."));
'@
$oldCheck = @'
    private void check(){new Thread(()->{try{var r=core.preflight();Platform.runLater(()->connection.setText(String.join(" · ",r)));}catch(Exception e){Platform.runLater(()->connection.setText(e.getMessage()));}},"network-check").start();}
'@
$newCheck = @'
    private void check(){
        connection.setText(tr("در حال بررسی اتصال واقعی از پراکسی...","Testing HTTPS connectivity through the HTTP proxy..."));
        Thread t = new Thread(() -> {
            try {
                var results = core.preflight();
                Platform.runLater(() -> {
                    connection.setText(tr("اتصال آلمان تأیید شد.\n", "German proxy verified.\n") + String.join("\n", results));
                    status.setText(tr("اتصال آماده است.", "Connection ready."));
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    connection.setText(tr("بررسی اتصال ناموفق بود: ", "Connection check failed: ") + ex.getMessage());
                    status.setText(tr("اتصال نیاز به بررسی دارد.", "Connection needs attention."));
                });
            }
        }, "network-check");
        t.setDaemon(true);
        t.start();
    }
'@
$serviceCode = @'
package ir.sakhtyar.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Performs a real HTTPS request through the configured v2rayN HTTP/Mixed proxy. */
public final class ProxyHealthService {
    public record Result(String ip, String countryCode) { }
    private ProxyHealthService() { }

    public static Result check(int port, ObjectMapper mapper) throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", port)))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://ipwho.is/"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .GET()
            .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            throw new IOException("HTTPS proxy test failed via 127.0.0.1:" + port + ": " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) throw new IOException("IP check HTTP " + response.statusCode());
        var data = mapper.readTree(response.body());
        if (!data.path("success").asBoolean(false)) throw new IOException("IP lookup did not succeed");
        String ip = data.path("ip").asText("");
        String country = data.path("country_code").asText("");
        if (ip.isBlank() || country.isBlank()) throw new IOException("IP lookup returned incomplete data");
        return new Result(ip, country);
    }
}
'@

# Verify every source anchor and prepare ALL changes before touching a single file.
$coreText = AssertReplacement 'v2rayN inspection' $coreText $oldV2 $newV2
$coreText = AssertReplacement 'Proxy country service' $coreText $oldCountry $newCountry
$coreText = AssertReplacement 'Preflight' $coreText $oldPreflight $newPreflight
$dashText = AssertReplacement 'Startup connection text' $dashText $oldStatus $newStatus
$dashText = AssertReplacement 'Dashboard Check connection action' $dashText $oldCheck $newCheck
if (Test-Path -LiteralPath $servicePath -PathType Leaf) {
    $existing = ([IO.File]::ReadAllText($servicePath)).Replace("`r`n", "`n")
    if ($existing.Trim() -ne $serviceCode.Replace("`r`n", "`n").Trim()) {
        throw "ProxyHealthService.java already exists with different content. No files were changed."
    }
}
if ($coreCrlf) { $coreText = $coreText.Replace("`n", "`r`n") }
if ($dashCrlf) { $dashText = $dashText.Replace("`n", "`r`n") }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $root ("_claude_launcher\backups\proxy-fix-" + $stamp)
New-Item -ItemType Directory -Path $backup -Force | Out-Null
Copy-Item -LiteralPath $corePath -Destination (Join-Path $backup 'LauncherCore.java')
Copy-Item -LiteralPath $dashboardPath -Destination (Join-Path $backup 'Dashboard.java')
$hadService = Test-Path -LiteralPath $servicePath -PathType Leaf
if ($hadService) { Copy-Item -LiteralPath $servicePath -Destination (Join-Path $backup 'ProxyHealthService.java') }
Info "Backup: $backup"

try {
    WriteUtf8NoBom $corePath $coreText
    WriteUtf8NoBom $dashboardPath $dashText
    if (-not $hadService) { WriteUtf8NoBom $servicePath ($serviceCode.Replace("`r`n", "`n") + "`n") }
    Good 'Java classes updated'
    $mvn = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if (-not $mvn) { $mvn = Get-Command mvn -ErrorAction SilentlyContinue }
    $wrapper = Join-Path $module 'mvnw.cmd'
    if (Test-Path -LiteralPath $wrapper) { $mvnExe = $wrapper }
    elseif ($mvn) { $mvnExe = $mvn.Source }
    else { throw 'Maven not found. Java changes rolled back to avoid leaving an unverified build.' }
    Info 'Compiling JavaFX launcher (Maven)...'
    & $mvnExe '-q' '-f' (Join-Path $module 'pom.xml') '-DskipTests' 'compile'
    if ($LASTEXITCODE -ne 0) { throw "Maven compilation failed (exit code $LASTEXITCODE)" }
    Good 'Maven compilation successful. Restart the dashboard to load the new Java classes.'
} catch {
    $errorText = $_.Exception.Message
    Copy-Item -LiteralPath (Join-Path $backup 'LauncherCore.java') -Destination $corePath -Force
    Copy-Item -LiteralPath (Join-Path $backup 'Dashboard.java') -Destination $dashboardPath -Force
    if ($hadService) { Copy-Item -LiteralPath (Join-Path $backup 'ProxyHealthService.java') -Destination $servicePath -Force }
    else { Remove-Item -LiteralPath $servicePath -Force -ErrorAction SilentlyContinue }
    Write-Host "[ROLLBACK] Original Java files restored: $errorText" -ForegroundColor Red
    exit 1
}
if ($RunAfter) {
    $launcher = Join-Path $root 'Run-Claude-Free.cmd'
    if (-not (Test-Path -LiteralPath $launcher)) { throw "Run-Claude-Free.cmd not found: $launcher" }
    Info 'Launching updated dashboard...'
    & $launcher
    exit $LASTEXITCODE
}
Write-Host "`nNext: cd `"$root`"; .\Run-Claude-Free.cmd" -ForegroundColor Yellow
