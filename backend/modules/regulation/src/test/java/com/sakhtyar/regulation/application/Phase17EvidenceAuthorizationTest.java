package com.sakhtyar.regulation.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.globalization.application.JurisdictionService;
import com.sakhtyar.property.domain.PropertyRepository;
import com.sakhtyar.regulation.domain.UrbanRuleRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class Phase17EvidenceAuthorizationTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final PropertyRepository properties=mock(PropertyRepository.class);
    private final CaseAccessService access=mock(CaseAccessService.class);
    private final RegulationEvidenceService service=new RegulationEvidenceService(jdbc,properties,
            mock(UrbanRuleRepository.class),access,mock(JurisdictionService.class));

    @AfterEach void clearAuth() {SecurityContextHolder.clearContext();}

    @Test void unauthorizedEvidenceIsNotQueried() {
        UUID c=UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(c);
        assertThrows(ResponseStatusException.class,()->service.evidence(c));
        assertThrows(ResponseStatusException.class,()->service.authorizedUrbanRuleIds(c));
        assertThrows(ResponseStatusException.class,()->service.municipalityInputs(c));
        verifyNoInteractions(jdbc,properties);
    }
    @Test void directServiceBindWithoutManagerIsRejectedBeforeSql() {
        assertThrows(AccessDeniedException.class,()->service.bind(UUID.randomUUID(),UUID.randomUUID(),"intruder"));
        verifyNoInteractions(jdbc,properties);
    }
}
