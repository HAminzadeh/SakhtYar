package com.sakhtyar.property.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.casefile.domain.CaseStatus;
import com.sakhtyar.property.api.PropertyDtos.MergePropertyFactsRequest;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class Phase09PropertyWriteContractTest {
    @Test void caseLockIsRequiredForEveryPropertyMutation() throws Exception {
        Method method = CaseRepository.class.getMethod("findByIdForUpdate", UUID.class);
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test void validLocalizedFactUsesParentCaseLockAndRecordsAudit() {
        UUID caseId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        CaseRepository cases = mock(CaseRepository.class);
        PropertyRepository properties = mock(PropertyRepository.class);
        CaseAccessService access = mock(CaseAccessService.class);
        AuditService audit = mock(AuditService.class);
        CaseEntity c = mock(CaseEntity.class);
        PropertyEntity p = mock(PropertyEntity.class);
        when(c.getDataScope()).thenReturn("REAL");
        when(c.getStatus()).thenReturn(CaseStatus.DRAFT);
        when(p.getId()).thenReturn(propertyId);
        when(p.getAttributes()).thenReturn(Map.of());
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(c));
        when(properties.findByCaseId(caseId)).thenReturn(Optional.of(p));
        PropertyService service = new PropertyService(properties, cases, audit, access);
        assertDoesNotThrow(() -> service.mergeFacts(caseId,
                new MergePropertyFactsRequest(Map.of("landAreaM2", "۱۰۰٫۵۰"))));
        verify(access).requireWrite(c);
        verify(cases).findByIdForUpdate(caseId);
        verify(properties).save(p);
        verify(audit).record(eq("PROPERTY"), eq(propertyId), eq("PROPERTY_FACTS_MERGED"), anyMap());
    }

    @Test void missingCoordinatePairFailsBeforePersistenceOrAudit() {
        UUID caseId = UUID.randomUUID();
        CaseRepository cases = mock(CaseRepository.class);
        PropertyRepository properties = mock(PropertyRepository.class);
        CaseAccessService access = mock(CaseAccessService.class);
        AuditService audit = mock(AuditService.class);
        CaseEntity c = mock(CaseEntity.class);
        PropertyEntity p = mock(PropertyEntity.class);
        when(c.getDataScope()).thenReturn("REAL");
        when(c.getStatus()).thenReturn(CaseStatus.DRAFT);
        when(p.getAttributes()).thenReturn(Map.of());
        when(p.getLatitude()).thenReturn(new BigDecimal("35.7"));
        when(p.getLongitude()).thenReturn(null);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(c));
        when(properties.findByCaseId(caseId)).thenReturn(Optional.of(p));
        PropertyService service = new PropertyService(properties, cases, audit, access);
        assertThrows(IllegalArgumentException.class, () -> service.mergeFacts(caseId,
                new MergePropertyFactsRequest(Map.of("city", "Tehran"))));
        verify(properties, never()).save(any());
        verifyNoInteractions(audit);
    }
    @Test void factMergeCannotOverflowExistingJsonbAttributeLimit() {
        UUID caseId = UUID.randomUUID();
        CaseRepository cases = mock(CaseRepository.class);
        PropertyRepository properties = mock(PropertyRepository.class);
        CaseAccessService access = mock(CaseAccessService.class);
        AuditService audit = mock(AuditService.class);
        CaseEntity c = mock(CaseEntity.class);
        PropertyEntity p = mock(PropertyEntity.class);
        java.util.Map<String, Object> existing = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 150; i++) existing.put("existingKey" + i, "value");
        when(c.getDataScope()).thenReturn("REAL");
        when(c.getStatus()).thenReturn(CaseStatus.DRAFT);
        when(p.getAttributes()).thenReturn(existing);
        when(cases.findByIdForUpdate(caseId)).thenReturn(Optional.of(c));
        when(properties.findByCaseId(caseId)).thenReturn(Optional.of(p));
        PropertyService service = new PropertyService(properties, cases, audit, access);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                service.mergeFacts(caseId, new MergePropertyFactsRequest(Map.of("oneMoreKey", "value"))));
        verify(properties, never()).save(any());
        verifyNoInteractions(audit);
    }

}
