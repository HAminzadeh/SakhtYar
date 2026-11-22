package com.sakhtyar.casefile.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class Phase08CaseMembershipValidationTest {
    private final CaseRepository repository = mock(CaseRepository.class);
    private final CaseAccessService access = mock(CaseAccessService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private final CaseMembershipService service = new CaseMembershipService(repository, access, jdbc, audit);
    private final UUID caseId = UUID.randomUUID();

    private void exists() {
        CaseEntity entity = mock(CaseEntity.class);
        when(repository.findById(caseId)).thenReturn(Optional.of(entity));
    }

    @Test void rejectsUnsafeMemberNamesBeforeAnyDatabaseMutation() {
        exists();
        for (String invalid : new String[] { "a", "x/y", "bad\\name", "bad name",
                "bad\nname", " bad\tname ", "" }) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> service.grant(caseId, invalid));
            assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        }
        verifyNoInteractions(jdbc, audit);
    }

    @Test void nonOwnerIsDeniedMembershipManagementBeforeDatabaseRead() {
        CaseEntity item = mock(CaseEntity.class);
        when(repository.findById(caseId)).thenReturn(Optional.of(item));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found."))
                .when(access).requireWrite(item);
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.list(caseId)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.grant(caseId, "member_01")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.revoke(caseId, "member_01")).getStatusCode());
        verifyNoInteractions(jdbc, audit);
    }
}
