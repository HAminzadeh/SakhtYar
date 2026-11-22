package com.sakhtyar.analysis.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.analysis.api.AnalysisSnapshotDtos.CreateSnapshotRequest;
import com.sakhtyar.analysis.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.scenario.domain.ConstructionScenarioRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class Phase05CaseAclTest {
    private final AnalysisSnapshotRepository repo=mock(AnalysisSnapshotRepository.class);
    private final DataLineageRepository lineage=mock(DataLineageRepository.class);
    private final CaseRepository cases=mock(CaseRepository.class);
    private final ConstructionScenarioRepository scenarios=mock(ConstructionScenarioRepository.class);
    private final AuditService audit=mock(AuditService.class);
    private final CaseAccessService acl=mock(CaseAccessService.class);
    private final AnalysisSnapshotService service = new AnalysisSnapshotService(repo,lineage,cases,scenarios,
            audit,new ObjectMapper(),acl);

    @Test void unauthorizedCreateRejectedBeforeAnyRepositoryWrite() {
        UUID caseId=UUID.randomUUID();
        var req=new CreateSnapshotRequest(caseId,null,"FINANCE","READY","1",null,null,null,
                null,null,null,null,Map.of("price",1),Map.of("profit",1),Map.of(),List.of());
        doThrow(new SecurityException("deny")).when(acl).requireWrite(caseId);
        assertThrows(SecurityException.class,()->service.create(req,null));
        verifyNoInteractions(repo,lineage);
    }
    @Test void unauthorizedCaseListRejectedBeforeRepositoryQuery() {
        UUID caseId=UUID.randomUUID();
        doThrow(new SecurityException("deny")).when(acl).requireRead(caseId);
        assertThrows(SecurityException.class,()->service.byCase(caseId,null));
        verifyNoInteractions(repo,lineage);
    }
    @Test void unauthorizedIdAndLineageRejectedBeforeLineageLookup() {
        UUID id=UUID.randomUUID(), caseId=UUID.randomUUID();
        var row=mock(AnalysisSnapshotEntity.class);
        when(row.getCaseId()).thenReturn(caseId);
        when(repo.findById(id)).thenReturn(Optional.of(row));
        doThrow(new SecurityException("deny")).when(acl).requireRead(caseId);
        assertThrows(SecurityException.class,()->service.get(id));
        assertThrows(SecurityException.class,()->service.lineage(id));
        verifyNoInteractions(lineage);
    }
    @Test void scenarioListFiltersInaccessibleCasesBeforeLineageRead() {
        UUID scenarioId=UUID.randomUUID();
        var row=mock(AnalysisSnapshotEntity.class);
        when(row.getCaseId()).thenReturn(UUID.randomUUID());
        when(repo.findByScenarioIdOrderByCreatedAtDesc(scenarioId)).thenReturn(List.of(row));
        assertTrue(service.byScenario(scenarioId).isEmpty());
        verifyNoInteractions(lineage);
    }
}