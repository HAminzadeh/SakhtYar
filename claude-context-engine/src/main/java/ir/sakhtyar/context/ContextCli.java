package ir.sakhtyar.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;

/** Java-only bootstrap and diagnostic CLI. Writes no business logic to shell scripts. */
public final class ContextCli {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static void setup(Path root)throws Exception{
        Path mcp=root.resolve(".mcp.json");
        ObjectNode tree=Files.exists(mcp)?(ObjectNode)JSON.readTree(mcp.toFile()):JSON.createObjectNode();
        ObjectNode servers=tree.with("mcpServers");
        if(!servers.has("sakhtyar-context")){
            ObjectNode svc=servers.putObject("sakhtyar-context");svc.put("type","stdio");svc.put("command","cmd.exe");
            var args=svc.putArray("args");args.add("/d");args.add("/c");args.add("claude-context-engine\\context-server.cmd");
            Path tmp=Files.createTempFile(root,"mcp-",".tmp");JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),tree);
            Files.move(tmp,mcp,StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[OK] Registered SakhtYar memory MCP; kept existing CodeGraph registration");
        }else System.out.println("[OK] Local memory MCP already configured");
    }
    public static void main(String[] args)throws Exception{
        if(args.length<2){System.err.println("Usage: ContextCli <bootstrap|index|status|search|graph|skills|install|note> <root> [argument]");System.exit(2);}
        String verb=args[0];
        String rawRoot=args[1].trim();
        if(rawRoot.startsWith("\""))rawRoot=rawRoot.substring(1);
        if(rawRoot.endsWith("\""))rawRoot=rawRoot.substring(0,rawRoot.length()-1);
        if(rawRoot.isBlank())throw new IllegalArgumentException("Invalid project root");
        Path root=Path.of(rawRoot).toAbsolutePath().normalize();
        switch(verb){
            case "bootstrap" -> setup(root);
            case "graph" -> System.out.println(new CodeGraphBridge(root).inspect());
            case "skills" -> new SkillCatalog(root).list().forEach(System.out::println);
            case "install" -> {
                if(args.length<4||!"--reviewed".equals(args[3]))throw new IllegalArgumentException("Explicit --reviewed approval required");
                System.out.println(new SkillCatalog(root).install(args[2],false));
            }
            case "index", "status", "search", "note" -> {
                try(ContextStore store=new ContextStore(root)){
                    switch(verb){
                        case "index" -> System.out.println(new ProjectIndexer(root).index(store,15000));
                        case "status" -> System.out.println("Memory: "+store.location()+" "+store.status());
                        case "search" -> store.search(args.length>2?args[2]:"",6,700).forEach(System.out::println);
                        case "note" -> {if(args.length<4)throw new IllegalArgumentException("note key text");store.note(args[2],args[3]);System.out.println("Saved");}
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown command: "+verb);
        }
    }
}
