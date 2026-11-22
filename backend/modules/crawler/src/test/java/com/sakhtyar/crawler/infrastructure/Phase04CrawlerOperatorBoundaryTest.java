package com.sakhtyar.crawler.infrastructure;

import com.sakhtyar.crawler.application.IngestionPipeline;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.junit.jupiter.api.Assertions.*;

class Phase04CrawlerOperatorBoundaryTest {
    private static IngestionPipeline runner(){return new IngestionPipeline(null,null,null,null,null,null,null,null,null,null,null);}
    @Test void anonymousCallerBlockedBeforeAnyJdbcOrEgress(){
        assertThrows(SecurityException.class,()->runner().run(UUID.randomUUID(),1,null));
    }
    @Test void authenticatedButNotPrivilegedCallerIsDenied(){
        var reader=new UsernamePasswordAuthenticationToken("reader",null,
            List.of(new SimpleGrantedAuthority("CASE_READ")));
        assertThrows(SecurityException.class,()->runner().run(UUID.randomUUID(),1,reader));
    }
}
