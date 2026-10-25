package ir.sakhtyar.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Project-isolated SQLite+FTS5 index. No API key and no remote database required. */
public final class ContextStore implements AutoCloseable {
    public record Hit(String source, int segment, String excerpt, double relevance) { }
    public record Status(int sources, int chunks, int notes) { }
    private final Connection db;
    private final Path root;
    private final Path dbFile;

    public static Path defaultDirectory(Path project) {
        String id = sha256(project.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT)).substring(0,16);
        String base = System.getenv("LOCALAPPDATA");
        if (base == null || base.isBlank()) base = Path.of(System.getProperty("user.home"), ".local", "share").toString();
        return Path.of(base, "SakhtYar", "AI", "projects", id);
    }

    public ContextStore(Path project) throws Exception {
        this.root = project.toAbsolutePath().normalize();
        Path dir = defaultDirectory(root); Files.createDirectories(dir);
        dbFile = dir.resolve("context.db");
        db = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS source(path TEXT PRIMARY KEY, digest TEXT NOT NULL, indexed_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS chunk(id INTEGER PRIMARY KEY, path TEXT NOT NULL, segment INTEGER NOT NULL, content TEXT NOT NULL, UNIQUE(path,segment))");
            s.execute("CREATE VIRTUAL TABLE IF NOT EXISTS chunk_search USING fts5(path UNINDEXED, content, tokenize='unicode61')");
            s.execute("CREATE TABLE IF NOT EXISTS note(key TEXT PRIMARY KEY, content TEXT NOT NULL, updated_at TEXT NOT NULL)");
            s.execute("CREATE VIRTUAL TABLE IF NOT EXISTS note_search USING fts5(key UNINDEXED, content, tokenize='unicode61')");
            s.execute("CREATE TABLE IF NOT EXISTS transcript(session TEXT NOT NULL, ordinal INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, PRIMARY KEY(session,ordinal))");
            s.execute("CREATE VIRTUAL TABLE IF NOT EXISTS transcript_search USING fts5(session UNINDEXED, role UNINDEXED, text, tokenize='unicode61')");
        }
    }
    public Path location() { return dbFile; }
    public Path root() { return root; }

    public synchronized boolean update(Path file, String content) throws Exception {
        Path resolved = file.toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("File is outside project: " + file);
        String key = root.relativize(resolved).toString().replace('\\','/');
        String digest = sha256(content);
        try (PreparedStatement ps=db.prepareStatement("SELECT digest FROM source WHERE path=?")) {
            ps.setString(1,key);try(ResultSet rs=ps.executeQuery()){if(rs.next()&&digest.equals(rs.getString(1)))return false;}
        }
        boolean wasAuto = db.getAutoCommit(); db.setAutoCommit(false);
        try {
            try(PreparedStatement a=db.prepareStatement("DELETE FROM chunk WHERE path=?");PreparedStatement b=db.prepareStatement("DELETE FROM chunk_search WHERE path=?")) {
                a.setString(1,key); b.setString(1,key); a.executeUpdate();b.executeUpdate();
            }
            List<String> blocks = chunks(redact(content), 2400);
            try(PreparedStatement a=db.prepareStatement("INSERT INTO chunk(path,segment,content) VALUES(?,?,?)"); PreparedStatement b=db.prepareStatement("INSERT INTO chunk_search(path,content) VALUES(?,?)")) {
                for(int i=0;i<blocks.size();i++){
                    a.setString(1,key);a.setInt(2,i+1);a.setString(3,blocks.get(i));a.addBatch();
                    b.setString(1,key);b.setString(2,blocks.get(i));b.addBatch();
                }
                a.executeBatch();b.executeBatch();
            }
            try(PreparedStatement ps=db.prepareStatement("INSERT INTO source(path,digest,indexed_at) VALUES(?,?,datetime('now')) ON CONFLICT(path) DO UPDATE SET digest=excluded.digest,indexed_at=excluded.indexed_at")){
                ps.setString(1,key);ps.setString(2,digest);ps.executeUpdate();
            }
            db.commit();return true;
        }catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(wasAuto);}
    }
    private static List<String> chunks(String s,int max){
        List<String> out=new ArrayList<>();
        for(int i=0;i<s.length();){int end=Math.min(s.length(),i+max);if(end<s.length()){
            int nl=s.lastIndexOf('\n',end); if(nl>i+max/2)end=nl+1;
        } out.add(s.substring(i,end));i=end;}
        return out;
    }
    public synchronized void archive(String session,int ordinal,String role,String content)throws SQLException {
        if(!session.matches("[a-zA-Z0-9_.:-]{1,150}")||ordinal<0||ordinal>200000)throw new IllegalArgumentException("Invalid session identifier");
        if(!Set.of("user","assistant").contains(role))throw new IllegalArgumentException("Invalid role");
        String sanitized=redact(content);String body=sanitized.substring(0,Math.min(sanitized.length(),3000));
        try(PreparedStatement select=db.prepareStatement("SELECT 1 FROM transcript WHERE session=? AND ordinal=?")){
            select.setString(1,session);select.setInt(2,ordinal);
            try(ResultSet r=select.executeQuery()){if(r.next())return;}
        }
        try(PreparedStatement p=db.prepareStatement("INSERT INTO transcript(session,ordinal,role,text) VALUES(?,?,?,?)");
            PreparedStatement f=db.prepareStatement("INSERT INTO transcript_search(session,role,text) VALUES(?,?,?)")){
            p.setString(1,session);p.setInt(2,ordinal);p.setString(3,role);p.setString(4,body);p.executeUpdate();
            f.setString(1,session);f.setString(2,role);f.setString(3,body);f.executeUpdate();
        }
    }
    private static String redact(String s){
        return s.replaceAll("(?i)(api[_-]?key|token|password|secret)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[REDACTED]")
            .replaceAll("sk-[A-Za-z0-9_-]{18,}", "[REDACTED]");
    }
    public synchronized int purgeMissing(Set<String> retained)throws SQLException{
        List<String> missing=new ArrayList<>();
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT path FROM source")){
            while(r.next()){String path=r.getString(1);if(!retained.contains(path))missing.add(path);}
        }
        for(String path:missing){
            try(PreparedStatement a=db.prepareStatement("DELETE FROM source WHERE path=?");
                PreparedStatement b=db.prepareStatement("DELETE FROM chunk WHERE path=?");
                PreparedStatement c=db.prepareStatement("DELETE FROM chunk_search WHERE path=?")){
                for(PreparedStatement ps:List.of(a,b,c)){ps.setString(1,path);ps.executeUpdate();}
            }
        }
        return missing.size();
    }
    public synchronized void note(String key,String text) throws SQLException {
        if(!key.matches("[a-zA-Z0-9_.:/-]{1,180}"))throw new IllegalArgumentException("Invalid memory key");
        if(text.length()>30000)throw new IllegalArgumentException("Note exceeds 30 KB");
        try(PreparedStatement p=db.prepareStatement("INSERT INTO note(key,content,updated_at) VALUES(?,?,datetime('now')) ON CONFLICT(key) DO UPDATE SET content=excluded.content,updated_at=excluded.updated_at")){
            p.setString(1,key);p.setString(2,redact(text));p.executeUpdate();
        }
        try(PreparedStatement del=db.prepareStatement("DELETE FROM note_search WHERE key=?");
            PreparedStatement ins=db.prepareStatement("INSERT INTO note_search(key,content) VALUES(?,?)")){
            del.setString(1,key);del.executeUpdate();ins.setString(1,key);ins.setString(2,redact(text));ins.executeUpdate();
        }
    }
    public synchronized String getNote(String key) throws SQLException{
        try(PreparedStatement p=db.prepareStatement("SELECT content FROM note WHERE key=?")){
            p.setString(1,key);try(ResultSet r=p.executeQuery()){return r.next()?r.getString(1):"";}
        }
    }
    public synchronized List<Hit> search(String query,int limit,int maxChars) throws SQLException {
        String cleaned=String.join(" ",Arrays.stream(query.split("[^\\p{L}\\p{N}_]+"))
            .filter(s->!s.isBlank()).limit(10).map(s->"\""+s.replace("\"", "")+"\"").toList());
        if(cleaned.isBlank())return List.of();
        List<Hit> hits=new ArrayList<>();
        try(PreparedStatement p=db.prepareStatement("SELECT path,content,bm25(chunk_search) score FROM chunk_search WHERE chunk_search MATCH ? ORDER BY score LIMIT ?")){
            p.setString(1,cleaned.replace(' ', ' '));p.setInt(2,Math.max(1,Math.min(limit,15)));
            try(ResultSet r=p.executeQuery()){
                while(r.next()){
                    String source=r.getString(1), text=r.getString(2);
                    hits.add(new Hit(source,0,text.substring(0,Math.min(text.length(),Math.max(100,Math.min(maxChars,3000)))),r.getDouble(3)));
                }
            }
        }
        try(PreparedStatement p=db.prepareStatement("SELECT key,content,bm25(note_search) score FROM note_search WHERE note_search MATCH ? ORDER BY score LIMIT ?")){
            p.setString(1,cleaned);p.setInt(2,Math.max(1,Math.min(limit,8)));
            try(ResultSet r=p.executeQuery()){
                while(r.next()){String data=r.getString(2);hits.add(new Hit("note:"+r.getString(1),0,data.substring(0,Math.min(data.length(),Math.max(100,Math.min(maxChars,3000)))),r.getDouble(3)));}
            }
        }
        return hits;
    }
    public synchronized List<Hit> searchSessions(String query,int limit)throws SQLException {
        String token=query.replaceAll("[^\\p{L}\\p{N}_ ]", "").trim();if(token.isBlank())return List.of();
        List<Hit> out=new ArrayList<>();
        try(PreparedStatement p=db.prepareStatement("SELECT session,text,bm25(transcript_search) score FROM transcript_search WHERE transcript_search MATCH ? ORDER BY score LIMIT ?")){
            String match=String.join(" ",Arrays.stream(token.split("\\s+")).filter(x->!x.isBlank()).limit(6).map(x->"\""+x+"\"").toList());
            p.setString(1,match);p.setInt(2,Math.max(1,Math.min(limit,10)));
            try(ResultSet r=p.executeQuery()){while(r.next())out.add(new Hit("session:"+r.getString(1),0,r.getString(2),r.getDouble(3)));}
        }
        return out;
    }
    public synchronized Status status() throws SQLException {
        return new Status(count("source"),count("chunk"),count("note"));
    }
    private int count(String table)throws SQLException{try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){return r.next()?r.getInt(1):0;}}
    public synchronized void close()throws SQLException{db.close();}
    public static String sha256(String s){
        try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(b);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
