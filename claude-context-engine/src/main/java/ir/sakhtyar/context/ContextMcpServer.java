package ir.sakhtyar.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Stdio JSON-RPC MCP for local project memory. Never writes protocol logs to stdout. */
public final class ContextMcpServer {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final ContextStore store;
    public ContextMcpServer(Path root)throws Exception{store=new ContextStore(root);}
    private static Map<String,Object> tool(String name,String description,Map<String,Object> props,List<String> required){
        return Map.of("name",name,"description",description,"inputSchema",Map.of("type","object","properties",props,"required",required));
    }
    private static Map<String,Object> prop(String description){return Map.of("type","string","description",description);}
    private Object dispatch(JsonNode req)throws Exception {
        String method=req.path("method").asText("");JsonNode p=req.path("params");
        return switch(method){
            case "initialize" -> Map.of("protocolVersion","2025-06-18","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","sakhtyar-local-memory","version","1.0.0"));
            case "tools/list" -> Map.of("tools",List.of(
                tool("sakhtyar_memory_search","Find relevant, bounded local project references without reading the entire repository.",Map.of("query",prop("Narrow phrase or symbol")),List.of("query")),
                tool("sakhtyar_memory_get","Read a named locally stored decision or handoff.",Map.of("key",prop("Memory key")),List.of("key")),
                tool("sakhtyar_session_search","Search explicitly archived local Claude session text.",Map.of("query",prop("Narrow phrase")),List.of("query")),
                tool("sakhtyar_memory_put","Save a short, reviewed project decision or handoff for later sessions.",Map.of("key",prop("Memory key"),"content",prop("Text to remember, no secrets")),List.of("key","content")),
                tool("sakhtyar_memory_status","Check the local index state.",Map.of(),List.of())
            ));
            case "tools/call" -> call(p.path("name").asText(),p.path("arguments"));
            case "ping" -> Map.of();
            default -> throw new IllegalArgumentException("Unknown MCP method: "+method);
        };
    }
    private Object call(String name,JsonNode a)throws Exception{
        String data=switch(name){
            case "sakhtyar_memory_search" -> {
                var hits=store.search(a.path("query").asText(),5,1000);
                StringBuilder s=new StringBuilder();for(var h:hits)s.append("SOURCE: ").append(h.source()).append("\n").append(h.excerpt()).append("\n---\n");
                yield s.length()==0?"No matching local context":s.toString();
            }
            case "sakhtyar_memory_get" -> store.getNote(a.path("key").asText());
            case "sakhtyar_session_search" -> {
                var hits=store.searchSessions(a.path("query").asText(),5);StringBuilder s=new StringBuilder();
                for(var hit:hits)s.append(hit.source()).append("\n").append(hit.excerpt()).append("\n---\n");
                yield s.length()==0?"No matching archived sessions":s.toString();
            }
            case "sakhtyar_memory_put" -> {store.note(a.path("key").asText(),a.path("content").asText());yield "Saved in local project memory";}
            case "sakhtyar_memory_status" -> {var s=store.status();yield "Sources: "+s.sources()+"; chunks: "+s.chunks()+"; notes: "+s.notes();}
            default -> throw new IllegalArgumentException("Unknown tool");
        };
        return Map.of("content",List.of(Map.of("type","text","text",data)));
    }
    public void serve() throws Exception {
        try(var in=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
            var out=new BufferedWriter(new OutputStreamWriter(System.out,StandardCharsets.UTF_8))){
            String line;while((line=in.readLine())!=null){try{
                JsonNode req=JSON.readTree(line);if(!req.has("id"))continue;
                ObjectNode reply=JSON.createObjectNode();reply.put("jsonrpc","2.0");reply.set("id",req.get("id"));
                try{reply.set("result",JSON.valueToTree(dispatch(req)));}
                catch(Exception e){reply.set("error",JSON.valueToTree(Map.of("code",-32603,"message",e.getMessage()==null?"error":e.getMessage())));}
                out.write(JSON.writeValueAsString(reply));out.write('\n');out.flush();
            }catch(Exception e){System.err.println("MCP protocol error: "+e.getClass().getSimpleName());}}
        }finally{store.close();}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1){System.err.println("Expected project root argument");System.exit(2);}
        new ContextMcpServer(Path.of(args[0])).serve();
    }
}
