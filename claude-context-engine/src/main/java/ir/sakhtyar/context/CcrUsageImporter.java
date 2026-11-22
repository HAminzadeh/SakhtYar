package ir.sakhtyar.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Read-only aggregation from an explicitly selected CCR JSON/JSONL export; no invented token values. */
public final class CcrUsageImporter {
    public record Report(long entries,long successful,long failed,long measured,long inputTokens,long outputTokens,long cacheReadTokens,long thinkingTokens){}
    private final ObjectMapper json=new ObjectMapper();
    private static long field(JsonNode n,String... names){for(String name:names){JsonNode v=n.path(name);if(v.isIntegralNumber())return Math.max(0,v.asLong());}return 0;}
    public Report inspect(Path file)throws IOException {
        if(Files.size(file)>25_000_000)throw new IOException("Usage export exceeds 25MB");
        Accumulator a=new Accumulator();
        String content=Files.readString(file,StandardCharsets.UTF_8);
        if(content.stripLeading().startsWith("[" )||content.stripLeading().startsWith("{")){
            try{JsonNode root=json.readTree(content);
                JsonNode items=root.isArray()?root:root.has("logs")?root.path("logs"):root.has("data")?root.path("data"):null;
                if(items!=null&&items.isArray()){for(JsonNode item:items)a.add(item);return a.report();}
                if(root.isObject()&&!root.has("logs")){a.add(root);return a.report();}
            }catch(Exception ignored){/* JSONL fallback */}
        }
        try(BufferedReader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            String line;while((line=r.readLine())!=null){if(line.isBlank())continue;
                try{a.add(json.readTree(line));}catch(Exception ignored){}
            }
        }
        return a.report();
    }
    private static final class Accumulator {
        long entries,success,fail,measured,in,out,cache,think;
        void add(JsonNode entry){
            if(!entry.isObject())return;
            int status=entry.path("status").asInt(entry.path("statusCode").asInt(0));entries++;
            if(status>=200&&status<300)success++;else if(status>=400)fail++;
            JsonNode usage=entry.has("usage")?entry.path("usage"):entry.has("tokenUsage")?entry.path("tokenUsage"):entry;
            boolean present=usage.has("input_tokens")||usage.has("inputTokens")||usage.has("output_tokens")||usage.has("outputTokens");
            if(present)measured++;
            in+=field(usage,"input_tokens","inputTokens");out+=field(usage,"output_tokens","outputTokens");
            cache+=field(usage,"cache_read_input_tokens","cacheReadInputTokens");think+=field(usage,"thinking_tokens","thinkingTokens");
        }
        Report report(){return new Report(entries,success,fail,measured,in,out,cache,think);}
    }
}
