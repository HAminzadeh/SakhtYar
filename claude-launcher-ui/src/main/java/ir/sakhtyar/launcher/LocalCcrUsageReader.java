package ir.sakhtyar.launcher;

import ir.sakhtyar.context.CcrUsageImporter;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Read-only best-effort CCR SQLite usage snapshot: works only when known metrics columns exist. */
public final class LocalCcrUsageReader {
    public record Snapshot(CcrUsageImporter.Report report, String source) { }
    private static String find(Map<String,String> columns,String... aliases){
        for(String alias:aliases){String key=alias.toLowerCase(Locale.ROOT).replace("_","");if(columns.containsKey(key))return columns.get(key);}
        return null;
    }
    private static String expr(String col){return col==null?"0":"COALESCE(SUM(CAST(\""+col+"\" AS INTEGER)),0)";}
    public Snapshot read() throws Exception {
        String home=System.getenv().getOrDefault("APPDATA",System.getProperty("user.home"));
        Path base=Path.of(home,"claude-code-router","app-data");
        List<Path> candidates=List.of(base.resolve("usage.sqlite"),base.resolve("request-logs.sqlite"));
        for(Path file:candidates){
            if(!Files.isRegularFile(file))continue;
            String uri="jdbc:sqlite:file:"+file.toUri().getRawPath()+"?mode=ro";
            try(Connection db=DriverManager.getConnection(uri);Statement tables=db.createStatement();ResultSet names=tables.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")){
                while(names.next()){
                    String table=names.getString(1);if(!table.matches("[A-Za-z0-9_]+"))continue;
                    if(!table.toLowerCase(Locale.ROOT).matches(".*(usage|request|log).*"))continue;
                    Map<String,String> cols=new HashMap<>();
                    try(Statement pragma=db.createStatement();ResultSet rs=pragma.executeQuery("PRAGMA table_info(\""+table+"\")")){
                        while(rs.next()){String name=rs.getString("name");cols.put(name.toLowerCase(Locale.ROOT).replace("_",""),name);}
                    }
                    String input=find(cols,"input_tokens","inputTokens","prompt_tokens","promptTokens");
                    String output=find(cols,"output_tokens","outputTokens","completion_tokens","completionTokens");
                    if(input==null&&output==null)continue;
                    String cache=find(cols,"cache_read_input_tokens","cacheReadInputTokens","cacheTokens");
                    String thinking=find(cols,"thinking_tokens","thinkingTokens","reasoning_tokens");
                    String status=find(cols,"status","status_code","statusCode","http_status");
                    String ok=status==null?"0":"COALESCE(SUM(CASE WHEN CAST(\""+status+"\" AS INTEGER) BETWEEN 200 AND 299 THEN 1 ELSE 0 END),0)";
                    String fail=status==null?"0":"COALESCE(SUM(CASE WHEN CAST(\""+status+"\" AS INTEGER)>=400 THEN 1 ELSE 0 END),0)";
                    String sql="SELECT COUNT(*),"+ok+","+fail+","+expr(input)+","+expr(output)+","+expr(cache)+","+expr(thinking)+" FROM \""+table+"\"";
                    try(Statement sum=db.createStatement();ResultSet r=sum.executeQuery(sql)){
                        if(!r.next()||r.getLong(1)==0)continue;
                        CcrUsageImporter.Report report=new CcrUsageImporter.Report(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(1),r.getLong(4),r.getLong(5),r.getLong(6),r.getLong(7));
                        return new Snapshot(report,file.getFileName()+" / "+table);
                    }
                }
            }catch(Exception ignored){/* Schema/version mismatch: try the next database */}
        }
        throw new IllegalStateException("No compatible CCR usage table available. Export JSON/JSONL from CCR Logs and import it instead.");
    }
}
