package com.sakhtyar.analysis.application;

import static com.sakhtyar.analysis.api.AnalysisSnapshotDtos.*;

import tools.jackson.databind.ObjectMapper;
import com.sakhtyar.analysis.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.scenario.domain.ConstructionScenarioRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisSnapshotService {
    private final AnalysisSnapshotRepository snapshotRepo;
    private final DataLineageRepository lineageRepo;
    private final CaseRepository caseRepo;
    private final ConstructionScenarioRepository scenarioRepo;
    private final AuditService auditService;
    private final SnapshotHasher hasher;

    public AnalysisSnapshotService(AnalysisSnapshotRepository snapshotRepo,DataLineageRepository lineageRepo,
            CaseRepository caseRepo,ConstructionScenarioRepository scenarioRepo,
            AuditService auditService,ObjectMapper mapper) {
        this.snapshotRepo=snapshotRepo; this.lineageRepo=lineageRepo; this.caseRepo=caseRepo;
        this.scenarioRepo=scenarioRepo; this.auditService=auditService; this.hasher=new SnapshotHasher(mapper);
    }

    @Transactional
    public SnapshotResponse create(CreateSnapshotRequest r,Authentication auth) {
        if(!caseRepo.existsById(r.caseId())) throw new IllegalArgumentException("Construction case not found.");

        if(r.scenarioId()!=null && !scenarioRepo.existsById(r.scenarioId()))
            throw new IllegalArgumentException("Construction scenario not found.");

        if(r.parentSnapshotId()!=null) {
            var parent=snapshotRepo.findById(r.parentSnapshotId())
                    .orElseThrow(()->new IllegalArgumentException("Parent analysis snapshot not found."));
            if(!parent.getCaseId().equals(r.caseId()))
                throw new IllegalArgumentException("Parent snapshot belongs to a different case.");
        }

        if((r.rootEntityType()==null) != (r.rootEntityId()==null))
            throw new IllegalArgumentException("rootEntityType and rootEntityId must be provided together.");

        validateLineage(r.lineage());

        Map<String,Object> metadata=r.metadata()==null?Map.of():r.metadata();
        String hash=hasher.sha256(r.inputSnapshot(),r.resultSnapshot(),metadata);
        Instant now=Instant.now();

        var snapshot=new AnalysisSnapshotEntity(
                UUID.randomUUID(),r.caseId(),r.scenarioId(),normalized(r.analysisType()),
                normalized(r.status()),r.schemaVersion().trim(),trim(r.calculationEngineVersion()),
                trim(r.knowledgeVersion()),trim(r.regulationVersion()),trim(r.materialPriceVersion()),
                trim(r.rootEntityType()),r.rootEntityId(),r.parentSnapshotId(),hash,r.lineage().size(),
                r.inputSnapshot(),r.resultSnapshot(),metadata,actor(auth),now
        );
        snapshotRepo.save(snapshot);

        List<DataLineageEntity> lines=new ArrayList<>();
        for(var x:r.lineage()) {
            lines.add(lineageRepo.save(new DataLineageEntity(
                    UUID.randomUUID(),r.caseId(),snapshot.getId(),x.outputPath().trim(),
                    normalized(x.relationshipType()),normalized(x.sourceType()),trim(x.sourceEntityType()),
                    x.sourceEntityId(),trim(x.sourceUrl()),trim(x.sourceLabel()),x.sourceObservedAt(),
                    trim(x.sourceVersion()),trim(x.sourceHash()),x.confidence(),
                    x.metadata()==null?Map.of():x.metadata(),now
            )));
        }

        auditService.record("ANALYSIS_SNAPSHOT",snapshot.getId(),"ANALYSIS_SNAPSHOT_CREATED",
                Map.of("caseId",r.caseId().toString(),"analysisType",snapshot.getAnalysisType(),
                        "contentSha256",hash,"sourceCount",r.lineage().size()));

        return toResponse(snapshot,lines);
    }

    @Transactional(readOnly=true)
    public SnapshotResponse get(UUID id) {
        var e=snapshotRepo.findById(id)
                .orElseThrow(()->new IllegalArgumentException("Analysis snapshot not found."));
        return toResponse(e,lineageRepo.findByAnalysisSnapshotIdOrderByCreatedAtAsc(id));
    }

    @Transactional(readOnly=true)
    public List<SnapshotResponse> byCase(UUID caseId,String analysisType) {
        List<AnalysisSnapshotEntity> list = analysisType==null || analysisType.isBlank()
                ? snapshotRepo.findByCaseIdOrderByCreatedAtDesc(caseId)
                : snapshotRepo.findByCaseIdAndAnalysisTypeOrderByCreatedAtDesc(caseId,normalized(analysisType));
        return list.stream()
                .map(e->toResponse(e,lineageRepo.findByAnalysisSnapshotIdOrderByCreatedAtAsc(e.getId())))
                .toList();
    }

    @Transactional(readOnly=true)
    public List<SnapshotResponse> byScenario(UUID scenarioId) {
        return snapshotRepo.findByScenarioIdOrderByCreatedAtDesc(scenarioId).stream()
                .map(e->toResponse(e,lineageRepo.findByAnalysisSnapshotIdOrderByCreatedAtAsc(e.getId())))
                .toList();
    }

    @Transactional(readOnly=true)
    public List<LineageResponse> lineage(UUID snapshotId) {
        if(!snapshotRepo.existsById(snapshotId)) throw new IllegalArgumentException("Analysis snapshot not found.");
        return lineageRepo.findByAnalysisSnapshotIdOrderByCreatedAtAsc(snapshotId).stream()
                .map(LineageResponse::from).toList();
    }

    private SnapshotResponse toResponse(AnalysisSnapshotEntity e,List<DataLineageEntity> lines) {
        return new SnapshotResponse(e.getId(),e.getCaseId(),e.getScenarioId(),e.getAnalysisType(),
                e.getStatus(),e.getSchemaVersion(),e.getCalculationEngineVersion(),e.getKnowledgeVersion(),
                e.getRegulationVersion(),e.getMaterialPriceVersion(),e.getRootEntityType(),e.getRootEntityId(),
                e.getParentSnapshotId(),e.getContentSha256(),e.getSourceCount(),e.getInputSnapshot(),
                e.getResultSnapshot(),e.getMetadata(),e.getCreatedBy(),e.getCreatedAt(),
                lines.stream().map(LineageResponse::from).toList());
    }

    private void validateLineage(List<LineageRequest> lines) {
        if(lines==null || lines.isEmpty()) throw new IllegalArgumentException("At least one lineage source is required.");
        for(var x:lines) {
            boolean hasSource=x.sourceEntityId()!=null
                    || (x.sourceUrl()!=null && !x.sourceUrl().isBlank())
                    || (x.sourceLabel()!=null && !x.sourceLabel().isBlank());
            if(!hasSource) throw new IllegalArgumentException(
                    "Each lineage item requires sourceEntityId, sourceUrl or sourceLabel.");
        }
    }

    private static String normalized(String s){return s.trim().toUpperCase(Locale.ROOT);}
    private static String trim(String s){return s==null?null:s.trim();}
    private static String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
}