package com.sakhtyar.knowledge.platform;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/knowledge/platform") public class KnowledgePlatformController {
 private final CatalogResolver catalog; private final KnowledgeQueryService knowledge; private final GraphRagIndexService graph; private final KnowledgeReleaseService releases; private final RetrievalPlanner planner;
 public KnowledgePlatformController(CatalogResolver catalog,KnowledgeQueryService knowledge,GraphRagIndexService graph,KnowledgeReleaseService releases,RetrievalPlanner planner){this.catalog=catalog;this.knowledge=knowledge;this.graph=graph;this.releases=releases;this.planner=planner;}
 @GetMapping("/catalog/resolve") public Object resolve(@RequestParam String term){return catalog.resolve(term);}
 @GetMapping("/releases/{id}/compatibility") public Object compatibility(@PathVariable UUID id){return releases.compatibility(id);}
 @GetMapping("/releases/{releaseId}/rules") public Object rules(@PathVariable UUID releaseId,@RequestParam UUID conceptId,@RequestParam(required=false) String jurisdiction){return knowledge.rules(releaseId,conceptId,jurisdiction);}
 @GetMapping("/graph/{graphVersionId}/nodes/{nodeId}/neighbors") public Object neighbors(@PathVariable UUID graphVersionId,@PathVariable UUID nodeId){return graph.neighborhood(graphVersionId,nodeId);}
 @GetMapping("/retrieval/plan") public Object plan(@RequestParam String question){return Map.of("mode",planner.plan(question));}
}