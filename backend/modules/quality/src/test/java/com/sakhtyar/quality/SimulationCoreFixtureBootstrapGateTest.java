package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SimulationCoreFixtureBootstrapGateTest {
    @Test void rejectsProvisioningWithoutExplicitOptInBeforeOpeningAnyConnection() {
        var provider=mock(T10FixtureAdminConnectionProvider.class);
        var source=mock(DataSource.class);
        when(provider.dataSource()).thenReturn(source);
        when(provider.isDedicated()).thenReturn(true);
        var service=new SimulationCoreFixtureService(provider,false);
        assertThrows(ResponseStatusException.class,()->service.create(UUID.randomUUID(),"saferequest123","tester"));
        verifyNoInteractions(source);
    }
    @Test void rejectsProvisioningWithSharedApplicationDatasourceEvenIfOptedIn() {
        var provider=mock(T10FixtureAdminConnectionProvider.class);
        var source=mock(DataSource.class);
        when(provider.dataSource()).thenReturn(source);
        when(provider.isDedicated()).thenReturn(false);
        var service=new SimulationCoreFixtureService(provider,true);
        assertThrows(ResponseStatusException.class,()->service.create(UUID.randomUUID(),"saferequest123","tester"));
        verifyNoInteractions(source);
    }
}
