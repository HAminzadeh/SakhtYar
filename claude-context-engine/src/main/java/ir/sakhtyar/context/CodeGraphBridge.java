package ir.sakhtyar.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Reads the existing CodeGraph MCP registration and checks its actual MCP tool handshake. */
public final class CodeGraphBridge {
    public record Health(boolean configured,boolean responding,int tools,String info) { }
    private final Path project;
    private final ObjectMapper json=new ObjectMapper();
    public CodeGraphBridge(Path project){this.project=project.toAbsolutePath().normalize();}
    public Health inspect(){
        Path config=project.resolve(".mcp.json");
        if(!Files.isRegularFile(config))return new Health(false,false,0,".mcp.json missing");
        try{
            JsonNode server=json.readTree(config.toFile()).path("mcpServers").path("codegraph");
            if(server.isMissingNode())return new Health(false,false,0,"CodeGraph entry missing");
            String command=server.path("command").asText("");if(command.isBlank())return new Health(false,false,0,"Missing command");
            List<String> cmd=new ArrayList<>();cmd.add(command);
            server.path("args").forEach(n->cmd.add(n.asText()));
            // Never execute arbitrary third-party configurations from the UI.
            if(!cmd.toString().toLowerCase(Locale.ROOT).contains("codegraph"))return new Health(true,false,0,"Not an approved CodeGraph command");
            Process process=new ProcessBuilder(cmd).directory(project.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"mcp-probe");t.setDaemon(true);return t;});
            try{
                Future<Health> task=io.submit(()->{
                    try(var out=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
                        var in=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                        send(out,Map.of("jsonrpc","2.0","id",1,"method","initialize","params",Map.of("protocolVersion","2025-06-18","capabilities",Map.of(),"clientInfo",Map.of("name","sakhtyar-context-check","version","1"))));
                        JsonNode init=readReply(in,1);if(!init.has("result"))return new Health(true,false,0,"MCP initialization rejected");
                        send(out,Map.of("jsonrpc","2.0","method","notifications/initialized"));
                        send(out,Map.of("jsonrpc","2.0","id",2,"method","tools/list","params",Map.of()));
                        JsonNode reply=readReply(in,2);return new Health(true,reply.has("result"),reply.path("result").path("tools").size(),"CodeGraph tools listed");
                    }
                });
                return task.get(15,TimeUnit.SECONDS);
            }finally{process.destroy();if(process.isAlive())process.destroyForcibly();io.shutdownNow();}
        }catch(Exception e){return new Health(true,false,0,e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void send(BufferedWriter w,Map<String,Object> data)throws IOException {w.write(json.writeValueAsString(data));w.write('\n');w.flush();}
    private JsonNode readReply(BufferedReader r,int id)throws IOException {
        for(int i=0;i<25;i++){String line=r.readLine();if(line==null)throw new IOException("MCP closed stdout");
            if(!line.startsWith("{"))continue;JsonNode n=json.readTree(line);if(n.path("id").asInt(-1)==id)return n;
        }
        throw new IOException("MCP response not received");
    }
}
