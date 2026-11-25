package com.sakhtyar.globalization;

import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.globalization.application.JurisdictionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase16JurisdictionAccessTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CaseAccessService access=mock(CaseAccessService.class);
    private final CaseRepository cases=mock(CaseRepository.class);
    private final JurisdictionService service=new JurisdictionService(jdbc,access,cases);

    @Test void unknownCaseCannotReadJurisdiction() {
        UUID id=UUID.randomUUID();
        when(cases.findById(id)).thenReturn(Optional.empty());
        var e=assertThrows(ResponseStatusException.class,()->service.forCase(id));
        assertEquals(HttpStatus.NOT_FOUND,e.getStatusCode());
        verifyNoInteractions(jdbc);
    }
    @Test void unauthorizedWriteStopsBeforeAnySqlMutation() {
        UUID id=UUID.randomUUID();
        CaseEntity item=mock(CaseEntity.class);
        when(cases.findByIdForUpdate(id)).thenReturn(Optional.of(item));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found.")).when(access).requireWrite(item);
        assertThrows(ResponseStatusException.class,()->service.setCaseLocation(id,UUID.randomUUID(),null,null));
        verifyNoInteractions(jdbc);
    }
    @Test void unauthorizedReadStopsBeforeLocationQuery() {
        UUID id=UUID.randomUUID();
        CaseEntity item=mock(CaseEntity.class);
        when(cases.findById(id)).thenReturn(Optional.of(item));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found.")).when(access).requireRead(item);
        assertThrows(ResponseStatusException.class,()->service.forCase(id));
        verifyNoInteractions(jdbc);
    }
}
