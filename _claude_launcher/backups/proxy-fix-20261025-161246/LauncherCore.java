package ir.sakhtyar.launcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Business logic. No PowerShell scripts are used. */
public final class LauncherCore {
    public enum Action { CONTINUE, NEW, CHOOSE }
    public record Request(Action action, String profile) {}
    private final Path root;
    private final Path stateDir;
    private final ObjectMapper json = new ObjectMapper();
    public LauncherCore(Path root) { this.root=root.toAbsolutePath().normalize(); this.stateDir=this.root.resolve("_claude_launcher"); }
    public Path root() { return root; }
    public Path selectionFile() { return stateDir.resolve("launch-selection.properties"); }
    public Path settingsFile() { return stateDir.resolve("control-center-settings.json"); }
    public static int port(String variable, int fallback) { try {int p=Integer.parseInt(System.getenv().getOrDefault(variable, ""+fallback)); return p>0&&p<=65535?p:fallback;}catch(Exception e){return fallback;} }
    public boolean routerListening() {return tcp("127.0.0.1",port("SAKHTYAR_CCR_PORT",3456));}
    public boolean proxyListening() {return tcp("127.0.0.1",port("SAKHTYAR_PROXY_PORT",10808));}
    private static boolean tcp(String host,int port){try(Socket s=new Socket()){s.connect(new InetSocketAddress(host,port),1500);return true;}catch(IOException e){return false;}}
    public void ensureRouter() throws Exception {
        if(routerListening()) return;
        Process p=new ProcessBuilder("cmd.exe","/d","/c","ccr.cmd start --no-open").directory(root.toFile()).redirectErrorStream(true).start();
        // Drain output so a chatty router cannot block on a full pipe.
        Thread drain=new Thread(()->{try(var in=p.getInputStream()){in.transferTo(java.io.OutputStream.nullOutputStream());}catch(IOException ignored){}},"ccr-output");drain.setDaemon(true);drain.start();
        for(int i=0;i<20;i++){if(routerListening())return; Thread.sleep(500);}
        if(p.isAlive())p.destroy();
        throw new IOException("CCR gateway did not become available on 127.0.0.1:"+port("SAKHTYAR_CCR_PORT",3456));
    }
    public boolean v2rayRunning() {return ProcessHandle.allProcesses().anyMatch(p->p.info().command().orElse("").toLowerCase(Locale.ROOT).contains("v2rayn.exe"));}
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
    public List<String> preflight() throws Exception {
        List<String> results=new ArrayList<>();
        if(!v2rayRunning())throw new IOException("v2rayN is not running");results.add("v2rayN: OK");
        if(!proxyListening())throw new IOException("Proxy port not available");results.add("Proxy: OK");
        String c=proxyCountry();if(!"DE".equalsIgnoreCase(c))throw new IOException("Proxy exit is not Germany: "+c);results.add("Exit: Germany");
        if(!routerListening())throw new IOException("CCR router is not ready");results.add("CCR: OK");
        return results;
    }
    public Map<String,Object> readSettings(){try {if(Files.exists(settingsFile()))return json.readValue(settingsFile().toFile(),new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception ignored){} return new LinkedHashMap<>();}
    public void writeSettings(Map<String,Object> values)throws IOException { Files.createDirectories(stateDir); Path t=Files.createTempFile(stateDir,"settings-",".tmp"); json.writerWithDefaultPrettyPrinter().writeValue(t.toFile(), values); try{Files.move(t,settingsFile(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(t,settingsFile(),StandardCopyOption.REPLACE_EXISTING);} }
    public List<String> profiles(){List<String> result=new ArrayList<>(List.of("Claude-Free"));Path legacy=stateDir.resolve("provider-profiles.json");try{if(Files.exists(legacy)){List<String> list=json.readValue(legacy.toFile(),new TypeReference<List<String>>(){});for(String p:list)if(validProfile(p)&&!result.contains(p))result.add(p);}}catch(Exception ignored){} return result;}
    public static boolean validProfile(String p){return p!=null&&p.matches("[A-Za-z0-9_.-]{1,80}");}
    public void choose(Request request)throws IOException {if(!validProfile(request.profile()))throw new IOException("Invalid profile name"); Files.createDirectories(stateDir); Properties p=new Properties();p.setProperty("action",request.action().name());p.setProperty("profile",request.profile());try(var out=Files.newOutputStream(selectionFile())){p.store(out,"SakhtYar launcher choice");}}
    public void clearSelection()throws IOException {Files.deleteIfExists(selectionFile());}
    public String usageNote(){return "Token/cost telemetry unavailable unless the configured provider supplies verifiable metrics. Use /cost or CCR analytics when supported.";}
}
