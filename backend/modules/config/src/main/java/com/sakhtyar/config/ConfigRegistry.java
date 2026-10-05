package com.sakhtyar.config;
import org.springframework.stereotype.Component;import java.util.*;
@Component public class ConfigRegistry {
 private final List<ConfigDefinition> defs=List.of(
  new ConfigDefinition("knowledge.workers","Knowledge","Pipeline workers","Concurrent knowledge workers","INTEGER",false,"HOT_RELOAD","2"),
  new ConfigDefinition("knowledge.heartbeatSeconds","Knowledge","Heartbeat threshold","Worker stale threshold","INTEGER",false,"HOT_RELOAD","20"),
  new ConfigDefinition("ai.ollama.baseUrl","AI","Ollama URL","Local AI runtime URL","STRING",false,"RESTART_REQUIRED","http://localhost:11434"),
  new ConfigDefinition("persian.ocr.enabled","Persian Intelligence","OCR enabled","Enable OCR fallback","BOOLEAN",false,"HOT_RELOAD","true"),
  new ConfigDefinition("integration.neshan.apiKey","Integrations","Neshan API key","Neshan service credential","SECRET",true,"HOT_RELOAD",null));
 public List<ConfigDefinition> all(){return defs;}
 public Optional<ConfigDefinition> find(String k){return defs.stream().filter(x->x.key().equals(k)).findFirst();}
}