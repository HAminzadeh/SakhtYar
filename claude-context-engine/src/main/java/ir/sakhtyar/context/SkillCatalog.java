package ir.sakhtyar.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Curated, auditable local skills; no automatic third-party download or execution. */
public final class SkillCatalog {
    public record Skill(String name,String group,String maturity,boolean installed,boolean present,String digest) { }
    public static final Map<String,List<String>> GROUPS;
    static {
        Map<String,List<String>> groups = new LinkedHashMap<>();
        groups.put("Backend",List.of("springboot-patterns","springboot-tdd","springboot-security","springboot-verification","database-migrations","postgres-patterns","api-design"));
        groups.put("Frontend",List.of("react-patterns","react-testing","react-performance","design-system","e2e-testing","sakhtyar-persian-rtl"));
        groups.put("Architecture",List.of("architecture-decision-records","knowledge-ops","security-review","git-workflow","sakhtyar-debugging","sakhtyar-skill-development"));
        groups.put("Programming",List.of("springboot-patterns","react-patterns","api-design","e2e-testing","security-review","frontend-design-direction","sakhtyar-debugging","java-coding-standards","springboot-tdd","docker-patterns"));
        GROUPS = Collections.unmodifiableMap(groups);
    }
    private final Path root;
    public SkillCatalog(Path root){this.root=root.toAbsolutePath().normalize();}
    private static String slug(String key){return key.startsWith("sakhtyar-")?key:"sakhtyar-"+key;}
    private Path source(String key){return root.resolve("engineering/ai-harness/skills").resolve(key).resolve("SKILL.md");}
    private Path target(String key){return root.resolve(".claude/skills").resolve(slug(key)).resolve("SKILL.md");}
    public List<Skill> list()throws IOException{
        List<Skill> out=new ArrayList<>();
        for(var g:GROUPS.entrySet())for(String name:g.getValue()){
            Path s=source(name),t=target(name);boolean exists=Files.isRegularFile(s);
            String body=exists?Files.readString(s,StandardCharsets.UTF_8):"";
            String maturity=body.contains("maturity: adapted")?"adapted":body.contains("maturity: catalog")?"catalog":"unknown";
            out.add(new Skill(name,g.getKey(),maturity,Files.isRegularFile(t),exists,exists?ContextStore.sha256(body).substring(0,12):""));
        }
        return out;
    }
    /** Install one reviewed project-local markdown skill; never overwrite an existing different skill. */
    public synchronized String install(String name,boolean allowCatalog)throws IOException {
        if(!GROUPS.values().stream().anyMatch(a->a.contains(name)))throw new IOException("Not in approved skills catalog");
        Path src=source(name),dst=target(name);if(!Files.isRegularFile(src))throw new IOException("Skill not found in project: "+name);
        String content=Files.readString(src,StandardCharsets.UTF_8);
        if(content.length()>50000||!content.startsWith("---\n")||!content.contains("\nname:"))throw new IOException("Unsupported SKILL.md content");
        boolean isCatalog=content.contains("maturity: catalog");
        if(isCatalog&&!allowCatalog)throw new IOException("This is only a catalog draft; review/adapt it before installing: "+name);
        String wrapped=content.replaceFirst("(?m)^name: [^\\r\\n]+", "name: "+slug(name));
        if(!wrapped.contains("disable-model-invocation:"))wrapped=wrapped.replaceFirst("(?m)^---\\R","---\ndisable-model-invocation: true\n");
        Path signature=dst.getParent().resolve(".sakhtyar-installed.sha256");
        if(Files.isRegularFile(dst)){
            String existing=Files.readString(dst,StandardCharsets.UTF_8);
            if(existing.equals(wrapped))return "Already up to date";
            if(!Files.isRegularFile(signature)||!Files.readString(signature,StandardCharsets.UTF_8).trim().equals(ContextStore.sha256(existing)))
                throw new IOException("Modified/unmanaged installed skill; not overwriting: "+dst);
            Path backup=ContextStore.defaultDirectory(root).resolve("skill-backups");Files.createDirectories(backup);
            Path old=backup.resolve(slug(name)+"-"+System.currentTimeMillis()+".md");Files.copy(dst,old);
            Files.writeString(dst,wrapped,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(signature,ContextStore.sha256(wrapped),StandardCharsets.UTF_8);
            return "Updated managed skill, backed up to "+old;
        }
        Files.createDirectories(dst.getParent());Files.writeString(dst,wrapped,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        Files.writeString(signature,ContextStore.sha256(wrapped),StandardCharsets.UTF_8);
        return "Installed: "+dst;
    }
    public synchronized boolean removeIfManaged(String name)throws IOException {
        Path p=target(name);if(!Files.isRegularFile(p))return false;
        String body=Files.readString(p,StandardCharsets.UTF_8);
        Path marker=p.getParent().resolve(".sakhtyar-installed.sha256");
        if(!Files.isRegularFile(marker)||!Files.readString(marker,StandardCharsets.UTF_8).trim().equals(ContextStore.sha256(body)))
            throw new IOException("Modified/unmanaged skill: will not remove " + p);
        Files.delete(p);Files.deleteIfExists(p.getParent().resolve(".sakhtyar-installed.sha256"));return true;
    }
}
