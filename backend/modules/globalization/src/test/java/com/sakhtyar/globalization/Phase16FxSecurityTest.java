package com.sakhtyar.globalization;

import com.sakhtyar.globalization.application.FxService;
import com.sakhtyar.globalization.domain.GlobalDtos.FxRequest;
import com.sakhtyar.audit.application.AuditService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase16FxSecurityTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final AuditService audit=mock(AuditService.class);
    private final FxService service=new FxService(jdbc,audit);
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    private void login(String role){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "actor", "ignored",List.of(new SimpleGrantedAuthority(role))));
    }
    private FxRequest request() {return new FxRequest(UUID.randomUUID(),UUID.randomUUID(),BigDecimal.TEN,
            "Sample source",null,null,BigDecimal.ONE);}

    @Test void nonAdminCannotWriteApprovedRates(){
        login("CASE_READ");
        assertThrows(ResponseStatusException.class,()->service.add(request()));
        verifyNoInteractions(jdbc,audit);
    }
    @Test void testerCannotApproveRates(){
        login("ROLE_TESTER");
        assertThrows(ResponseStatusException.class,()->service.review(UUID.randomUUID(),true));
        verifyNoInteractions(jdbc,audit);
    }
    @Test void invalidRateRejectedBeforeDatabaseWrite(){
        login("USER_MANAGE");
        var r=request();
        var invalid=new FxRequest(r.baseCurrencyId(),r.quoteCurrencyId(),BigDecimal.ZERO,
            r.sourceLabel(),r.sourceUrl(),r.observedAt(),r.confidence());
        assertThrows(IllegalArgumentException.class,()->service.add(invalid));
        verifyNoInteractions(jdbc,audit);
    }
    @Test void missingCurrencyIdsRejected(){
        login("USER_MANAGE");
        assertThrows(ResponseStatusException.class,()->service.add(new FxRequest(null,null,BigDecimal.ONE,"Source",null,null,null)));
        verifyNoInteractions(jdbc,audit);
    }
}
