package com.sakhtyar.automation;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service
public class AutomationPlatformService {
  private final Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().getParent();
  private Path resolveRoot() {
    Path p=Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    if(Files.exists(p.resolve("backend/pom.xml"))) return p;
    if(p.getFileName()!=null && p.getFileName().toString().equals("backend")) return p.getParent();
    return root;
  }
  public Map<String,Object> status() throws IOException {
    Path r=resolveRoot(), status=r.resolve(".sakhtyar/runtime/automation-status.json");
    Map<String,Object> out=new LinkedHashMap<>();out.put("root",r.toString());out.put("statusExists",Files.exists(status));
    out.put("statusJson",Files.exists(status)?Files.readString(status,StandardCharsets.UTF_8):"{}");
    Path log=r.resolve(".sakhtyar/runtime/automation.log");
    out.put("log",Files.exists(log)?Files.readString(log,StandardCharsets.UTF_8):"");
    out.put("checkedAt",Instant.now().toString());return out;
  }
  public Map<String,Object> run(boolean checkOnly) throws Exception {
    Path r=resolveRoot(), script=r.resolve("tools/bootstrap/Start-SakhtYar.ps1");
    if(!Files.exists(script)) throw new IllegalStateException("Bootstrap script missing");
    List<String> cmd=new ArrayList<>(List.of("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-File",script.toString(),"-RepoRoot",r.toString()));
    if(checkOnly) cmd.add("-CheckOnly");
    Process p=new ProcessBuilder(cmd).directory(r.toFile()).redirectErrorStream(true).start();
    String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
    boolean done=p.waitFor(15,java.util.concurrent.TimeUnit.MINUTES);
    if(!done){p.destroyForcibly();throw new IllegalStateException("Automation timeout");}
    if(p.exitValue()!=0) throw new IllegalStateException("Automation failed: "+text);
    Map<String,Object> out=status();out.put("console",text);return out;
  }
}