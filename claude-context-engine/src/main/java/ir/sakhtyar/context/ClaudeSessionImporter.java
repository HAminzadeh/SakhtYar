package ir.sakhtyar.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Import only an explicitly selected local Claude session JSONL. No network or automatic history scanning. */
public final class ClaudeSessionImporter {
    public record Report(int read,int saved,int skipped) { }
    private final ObjectMapper json=new ObjectMapper();
    public Report importFile(Path selected,ContextStore store)throws Exception{
        if(!selected.getFileName().toString().endsWith(".jsonl"))throw new IOException("Select a .jsonl Claude session");
        if(Files.size(selected)>100_000_000)throw new IOException("Session exceeds 100 MB limit");
        String session=selected.getFileName().toString().replaceFirst("\\.jsonl$","");
        if(!session.matches("[a-zA-Z0-9_.-]{1,150}"))session=ContextStore.sha256(session).substring(0,20);
        int read=0,saved=0,skipped=0;
        try(BufferedReader reader=Files.newBufferedReader(selected,StandardCharsets.UTF_8)){
            String line;while((line=reader.readLine())!=null&&read<10000){read++;
                try{
                    JsonNode item=json.readTree(line);String role=item.path("message").path("role").asText("");
                    if(!"assistant".equals(role)&&!"user".equals(role)){skipped++;continue;}
                    JsonNode content=item.path("message").path("content");StringBuilder text=new StringBuilder();
                    if(content.isTextual())text.append(content.asText());
                    else if(content.isArray())for(JsonNode c:content)if("text".equals(c.path("type").asText()))text.append(c.path("text").asText()).append("\n");
                    if(text.isEmpty()){skipped++;continue;}
                    store.archive(session,read,role,text.toString());saved++;
                }catch(Exception e){skipped++;}
            }
        }
        return new Report(read,saved,skipped);
    }
}
