package com.sakhtyar.knowledge.api;

import com.sakhtyar.knowledge.application.KnowledgeAiLifecycleService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/knowledge/admin/ai-lifecycle")
public class KnowledgeAiLifecycleController {
    private final KnowledgeAiLifecycleService service;
    public KnowledgeAiLifecycleController(KnowledgeAiLifecycleService service){this.service=service;}
    private String manager(Authentication auth){
        if(auth==null||!auth.isAuthenticated()||auth.getAuthorities().stream().noneMatch(a->"USER_MANAGE".equals(a.getAuthority())))
            throw new AccessDeniedException("USER_MANAGE required");
        return auth.getName();
    }
    private String superAdmin(Authentication auth){
        String actor=manager(auth);
        if(auth.getAuthorities().stream().noneMatch(a->"ROLE_ADMIN".equals(a.getAuthority())))
            throw new AccessDeniedException("ADMIN role required");
        return actor;
    }
    @GetMapping("/overview") public Map<String,Object> overview(Authentication auth){manager(auth);return service.overview();}
    @GetMapping("/packages") public Map<String,Object> packages(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,
       @RequestParam(required=false) Boolean includingArchived,@RequestParam(required=false) String search,Authentication auth){
        manager(auth);return service.packages(page,size,includingArchived,search);
    }
    @GetMapping("/documents") public Map<String,Object> documents(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,@RequestParam(required=false) UUID batchId,@RequestParam(required=false) String category,@RequestParam(required=false) String search,@RequestParam(required=false) Boolean includingArchived,Authentication auth){
        manager(auth);return service.documents(page,size,batchId,category,search,includingArchived);
    }
    @GetMapping("/items") public Map<String,Object> items(@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size,@RequestParam(required=false) UUID batchId,@RequestParam(required=false) UUID documentId,@RequestParam(required=false) String category,@RequestParam(required=false) String search,@RequestParam(required=false) Boolean includingArchived,Authentication auth){
        manager(auth);return service.items(page,size,batchId,documentId,category,search,includingArchived);
    }
    public record Selection(String type,List<UUID> ids,String category) {
        KnowledgeAiLifecycleService.Scope scope(){return new KnowledgeAiLifecycleService.Scope(type,ids,category);}
    }
    public record Action(Selection selection,String reason) {}
    @PostMapping("/preview") public Map<String,Object> preview(@RequestBody Selection request,Authentication auth){manager(auth);return service.preview(request.scope());}
    @PostMapping("/archive") public Map<String,Object> archive(@RequestBody Action request,Authentication auth){return service.archive(request.selection().scope(),request.reason(),manager(auth));}
    @PostMapping("/restore") public Map<String,Object> restore(@RequestBody Selection request,Authentication auth){return service.restore(request.scope(),manager(auth));}
    @PostMapping("/backup") public Map<String,Object> backup(@RequestBody Selection request,Authentication auth){return service.prepareBackup(request.scope(),superAdmin(auth));}
    public record Purge(UUID backupId,String confirmation){}
    @PostMapping("/purge") public Map<String,Object> purge(@RequestBody Purge request,Authentication auth){return service.permanentlyDelete(request.backupId(),request.confirmation(),superAdmin(auth));}
    @GetMapping("/issues") public List<Map<String,Object>> issues(@RequestParam(required=false) UUID batchId,@RequestParam(required=false) String state,@RequestParam(required=false) Integer limit,Authentication auth){manager(auth);return service.issues(batchId,state,limit);}
    public record IssueDecision(String decision,String note){}
    @PostMapping("/issues/{id}/decision") public Map<String,Object> issue(@PathVariable UUID id,@RequestBody IssueDecision request,Authentication auth){return service.decideIssue(id,request.decision(),request.note(),manager(auth));}
    @PutMapping("/documents/{id}/edition") public Map<String,Object> edition(@PathVariable UUID id,@RequestBody Map<String,Object> request,Authentication auth){return service.updateEdition(id,request,manager(auth));}
    public record Classification(String category,String reason){}
    @PutMapping("/documents/{id}/classification") public Map<String,Object> classify(@PathVariable UUID id,@RequestBody Classification request,Authentication auth){return service.classify(id,request.category(),request.reason(),manager(auth));}
    @GetMapping("/editions") public Map<String,Object> editions(@RequestParam String family,Authentication auth){manager(auth);return service.editions(family);}
    @GetMapping("/compare") public Map<String,Object> compare(@RequestParam UUID from,@RequestParam UUID to,Authentication auth){manager(auth);return service.compare(from,to);}
    @GetMapping("/as-of") public Map<String,Object> asOf(@RequestParam String date,@RequestParam(required=false) String family,Authentication auth){manager(auth);return service.asOf(java.time.LocalDate.parse(date),family);}
    @GetMapping("/duplicates") public List<Map<String,Object>> duplicates(@RequestParam(required=false) Integer limit,Authentication auth){manager(auth);return service.duplicates(limit);}
    public record Baseline(String label,String note){}
    @PostMapping("/baselines") public Map<String,Object> baseline(@RequestBody Baseline request,Authentication auth){return service.createBaseline(request.label(),request.note(),manager(auth));}
    @GetMapping("/baselines") public List<Map<String,Object>> baselines(Authentication auth){manager(auth);return service.baselines();}
    @GetMapping("/events") public List<Map<String,Object>> events(@RequestParam(required=false) Integer limit,Authentication auth){manager(auth);return service.events(limit);}
}
