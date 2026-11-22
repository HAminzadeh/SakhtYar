package com.sakhtyar.owner.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.owner.api.OwnerDtos.OwnerResponse;
import com.sakhtyar.owner.domain.OwnerEntity;
import com.sakhtyar.owner.domain.OwnerRepository;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase10OwnerPrivacyContractTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void readOnlyMemberGetsShareAndNameWithoutNationalIdOrMobile() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("member", null,
                        List.of(new SimpleGrantedAuthority("OWNER_READ"))));
        UUID caseId = UUID.randomUUID(), propertyId = UUID.randomUUID();
        CaseRepository cases = mock(CaseRepository.class);
        CaseAccessService access = mock(CaseAccessService.class);
        PropertyRepository properties = mock(PropertyRepository.class);
        OwnerRepository owners = mock(OwnerRepository.class);
        CaseEntity c = mock(CaseEntity.class);
        when(c.getDataScope()).thenReturn("REAL");
        when(cases.findById(caseId)).thenReturn(Optional.of(c));
        PropertyEntity p = mock(PropertyEntity.class);
        when(p.getDataScope()).thenReturn("REAL");
        when(p.getCaseId()).thenReturn(caseId);
        when(p.getId()).thenReturn(propertyId);
        when(properties.findByCaseId(caseId)).thenReturn(Optional.of(p));
        OwnerEntity owner = mock(OwnerEntity.class);
        when(owner.getFirstName()).thenReturn("Maryam");
        when(owner.getNationalId()).thenReturn("1234567890");
        when(owner.getMobile()).thenReturn("09123456789");
        when(owner.getOwnershipNumerator()).thenReturn(1);
        when(owner.getOwnershipDenominator()).thenReturn(2);
        when(owners.findByPropertyIdOrderByCreatedAtAsc(propertyId)).thenReturn(List.of(owner));
        OwnerService service = new OwnerService(owners, properties, mock(AuditService.class), cases, access);
        OwnerResponse response = service.list(caseId).get(0);
        assertEquals("Maryam", response.firstName());
        assertEquals(1, response.ownershipNumerator());
        assertNull(response.nationalId());
        assertNull(response.mobile());
        verify(access).requireRead(c);
        verify(access, never()).requireWrite(c);
    }
    @Test void responseFactoryRetainsExistingShapeForAuthorizedWriters() {
        OwnerEntity owner = mock(OwnerEntity.class);
        when(owner.getNationalId()).thenReturn("1234567890");
        when(owner.getMobile()).thenReturn("09123456789");
        assertEquals("1234567890", OwnerResponse.from(owner).nationalId());
        assertNull(OwnerResponse.from(owner, false).nationalId());
        assertNull(OwnerResponse.from(owner, false).mobile());
    }
}
