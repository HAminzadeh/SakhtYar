package com.sakhtyar.casefile.application;

import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase03CaseAclTest {
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    private static void user(String name,String... roles){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            name,null,java.util.Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList()));
    }
    private static CaseEntity item(String creator,String scope){
        CaseEntity item=mock(CaseEntity.class);
        when(item.getCreatedBy()).thenReturn(creator);
        when(item.getDataScope()).thenReturn(scope);
        return item;
    }
    @Test void activeMemberCanReadButCannotMutateOrManageOtherUsers(){
        CaseRepository repository=mock(CaseRepository.class);
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        CaseAccessService access=new CaseAccessService(repository,jdbc);
        CaseEntity item=item("creator","REAL");
        UUID id=UUID.randomUUID(); when(item.getId()).thenReturn(id);
        user("member","CASE_READ","CASE_WRITE");
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(id),eq("member"))).thenReturn(true);
        assertTrue(access.canRead(item));
        ResponseStatusException forbidden=assertThrows(ResponseStatusException.class,()->access.requireWrite(item));
        assertEquals(HttpStatus.NOT_FOUND,forbidden.getStatusCode());
        user("creator","CASE_WRITE");
        assertDoesNotThrow(()->access.requireWrite(item));
    }
    @Test void adminCannotReadTestTaggedCaseThroughRealBusinessApi(){
        CaseAccessService access=new CaseAccessService(mock(CaseRepository.class),mock(JdbcTemplate.class));
        user("admin","ROLE_ADMIN","CASE_READ");
        assertFalse(access.canRead(item("admin","TEST")));
        assertThrows(ResponseStatusException.class,()->access.requireWrite(item("admin","TEST")));
    }
    @Test void nestedWritesAreAuthorizedBeforeController(){
        CaseAccessService access=mock(CaseAccessService.class);
        Phase03CaseAuthorizationInterceptor interceptor=new Phase03CaseAuthorizationInterceptor(access);
        HttpServletRequest request=mock(HttpServletRequest.class);
        var id=UUID.randomUUID();
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/v1/cases/"+id+"/owners");
        when(request.getMethod()).thenReturn("POST");
        assertTrue(interceptor.preHandle(request,null,null));
        verify(access).requireWrite(id);
    }
}
