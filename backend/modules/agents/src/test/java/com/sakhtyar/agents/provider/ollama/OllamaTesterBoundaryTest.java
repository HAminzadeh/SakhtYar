package com.sakhtyar.agents.provider.ollama;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class OllamaTesterBoundaryTest {
    @Test void testerMustUseOfflineModelMock() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("tester",null,
                List.of(new SimpleGrantedAuthority("ROLE_TESTER"))));
        try {
            var provider=new OllamaAiModelProvider(null,null,null,null);
            assertFalse(provider.available());
            assertThrows(SecurityException.class,()->provider.generate(null));
        } finally { SecurityContextHolder.clearContext(); }
    }
}
