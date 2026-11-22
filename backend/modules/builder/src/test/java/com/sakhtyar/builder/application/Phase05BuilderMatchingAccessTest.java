package com.sakhtyar.builder.application;

import com.sakhtyar.builder.domain.BuilderProfileRepository;
import com.sakhtyar.property.domain.PropertyRepository;
import com.sakhtyar.casefile.application.CaseAccessService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase05BuilderMatchingAccessTest {
    @Test void outsiderCannotDiscoverCasePropertyThroughMatchingEndpoint() {
        var builders=mock(BuilderProfileRepository.class);
        var properties=mock(PropertyRepository.class);
        var access=mock(CaseAccessService.class);
        UUID caseId=UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(access).requireRead(caseId);
        var service=new BuilderMatchingService(builders,properties,access);
        assertThrows(ResponseStatusException.class,
            ()->service.match(caseId,BigDecimal.TEN,10));
        verifyNoInteractions(properties,builders);
    }
}
