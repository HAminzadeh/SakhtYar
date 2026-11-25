package com.sakhtyar.globalization;

import com.sakhtyar.globalization.application.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase16CatalogValidationTest {
    @Test void extremelyLongQueriesNeverReachDatabase(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        CatalogService service=new CatalogService(jdbc);
        assertThrows(IllegalArgumentException.class,()->service.countries("x".repeat(101)));
        assertThrows(IllegalArgumentException.class,()->service.cities(java.util.UUID.randomUUID(),null,"y".repeat(101)));
        verifyNoInteractions(jdbc);
    }
}
