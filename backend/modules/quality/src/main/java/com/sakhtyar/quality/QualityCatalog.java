package com.sakhtyar.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class QualityCatalog {
    private final List<TestDefinition> all;
    private final Map<String, TestDefinition> byId;

    public record TestDefinition(String id, String module, String title, String description,
                                 String expected, String kind, String target, String risk,
                                 String coverage, String source) { }

    public QualityCatalog(ObjectMapper mapper) throws IOException {
        var input = new ClassPathResource("quality/catalog.json");
        var raw = new String(input.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode root = mapper.readTree(raw);
        List<TestDefinition> found = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode n : root) {
            TestDefinition def = new TestDefinition(
                field(n,"id"), field(n,"module"), field(n,"title"), field(n,"description"),
                field(n,"expected"), field(n,"kind"), field(n,"target"), field(n,"risk"),
                field(n,"coverage"), field(n,"source"));
            if(!ids.add(def.id()) || !def.id().matches("[A-Z0-9_-]{3,100}"))
                throw new IllegalStateException("Invalid/duplicate quality scenario: " + def.id());
            if(!Set.of("ROUTE","SQL","EXTERNAL").contains(def.kind()))
                throw new IllegalStateException("Unsupported quality scenario kind: " + def.kind());
            found.add(def);
        }
        all=List.copyOf(found);
        Map<String,TestDefinition> map=new LinkedHashMap<>();
        all.forEach(d->map.put(d.id(),d));
        byId=Map.copyOf(map);
    }
    private String field(JsonNode n,String key){return n.path(key).asText("");}
    public List<TestDefinition> all(){return all;}
    public TestDefinition require(String id){
        var result=byId.get(id);
        if(result==null) throw new IllegalArgumentException("Unknown test id: "+id);
        return result;
    }
}
