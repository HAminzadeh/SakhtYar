package com.sakhtyar.quality;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class T10FixtureAdminConnectionProviderTest {
    private final DataSource app = mock(DataSource.class);

    @Test void legacyFallbackPreservesExistingConnection() {
        var provider = new T10FixtureAdminConnectionProvider(app, "", "", "", false);
        assertSame(app, provider.dataSource());
        assertFalse(provider.isDedicated());
    }
    @Test void rejectsPartialConfiguration() {
        assertThrows(IllegalStateException.class, () ->
            new T10FixtureAdminConnectionProvider(app, "jdbc:postgresql://localhost:5432/sakhtyar", "admin", "", false));
    }
    @Test void failClosedWhenDedicatedRequired() {
        assertThrows(IllegalStateException.class, () ->
            new T10FixtureAdminConnectionProvider(app, "", "", "", true));
    }
    @Test void dedicatedSettingsDoNotReplaceApplicationDataSource() {
        var provider = new T10FixtureAdminConnectionProvider(app,
            "jdbc:postgresql://localhost:5432/sakhtyar", "fixture_admin", "secret", true);
        assertTrue(provider.isDedicated());
        assertNotSame(app, provider.dataSource());
    }
    @Test void rejectsCredentialsEmbeddedInUrl() {
        assertThrows(IllegalStateException.class, () ->
            new T10FixtureAdminConnectionProvider(app,
                "jdbc:postgresql://localhost/sakhtyar?user=admin", "admin", "secret", true));
    }
}