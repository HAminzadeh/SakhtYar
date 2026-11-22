package ir.sakhtyar.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Bounded, incremental, source-only indexing. Secrets and build outputs are excluded. */
public final class ProjectIndexer {
    public record Result(int scanned, int updated, int skipped, boolean limited) { }
    private static final Set<String> EXT = Set.of("java","md","xml","json","yaml","yml","ts","tsx","js","jsx","sql","properties");
    private static final Set<String> EXCLUDE = Set.of(".git",".idea",".gradle",".m2","node_modules","target","dist","build","out",".venv","venv",".next",".claude", "_claude_launcher","logs","backup","backups","secrets");
    private final Path root;
    public ProjectIndexer(Path root){this.root=root.toAbsolutePath().normalize();}
    public Result index(ContextStore store,int maxFiles)throws Exception {
        if(!Files.isDirectory(root))throw new IOException("Missing project root: "+root);
        int scanned=0,updated=0,skipped=0;boolean limited=false;Set<String> current=new HashSet<>();
        try(Stream<Path> paths=Files.walk(root)){
            Iterator<Path> files=paths.filter(Files::isRegularFile).filter(this::allowed).sorted().iterator();
            while(files.hasNext()){
                if(scanned>=Math.max(1,Math.min(maxFiles,25000))){limited=true;break;}
                Path p=files.next();scanned++;current.add(root.relativize(p).toString().replace('\\','/'));
                try{
                    if(Files.size(p)>750_000){skipped++;continue;}
                    String body=Files.readString(p,StandardCharsets.UTF_8);
                    if(body.indexOf('\0')>=0){skipped++;continue;}
                    if(store.update(p,body))updated++;
                }catch(IOException e){skipped++;}
            }
        }
        if(!limited&&skipped==0)store.purgeMissing(current);
        return new Result(scanned,updated,skipped,limited);
    }
    private boolean allowed(Path p){
        Path rel=root.relativize(p);for(Path part:rel){String n=part.toString().toLowerCase(Locale.ROOT);if(EXCLUDE.contains(n)||n.startsWith(".env")||n.endsWith(".key")||n.endsWith(".pem")||n.endsWith(".p12")||n.endsWith(".pfx")||n.contains("credentials"))return false;}
        String name=p.getFileName().toString().toLowerCase(Locale.ROOT);
        int i=name.lastIndexOf('.');return i>0&&EXT.contains(name.substring(i+1));
    }
}
