package com.sakhtyar.bootstrap;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationPreflightInitializerTest {
    private final String first = "V1__extensions.sql";
    private final String second = "V2__schema.sql";
    private DatabaseMigrationPreflightInitializer.HistoryRow row(String v, String file, boolean ok) {
        return new DatabaseMigrationPreflightInitializer.HistoryRow(v, file, "SQL", ok, 123);
    }
    @Test void pendingFailsBeforeHibernate() {
        var expected = DatabaseMigrationPreflightInitializer.inventory(List.of(second, first));
        var error = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrationPreflightInitializer.verify(expected, List.of(row("1", first, true))));
        assertTrue(error.getMessage().contains("DATABASE_MIGRATION_PENDING"));
    }
    @Test void matchingHistoryPasses() {
        var expected = DatabaseMigrationPreflightInitializer.inventory(List.of(first, second));
        assertDoesNotThrow(() -> DatabaseMigrationPreflightInitializer.verify(expected,
                List.of(row("1", first, true), row("2", second, true))));
    }
    @Test void mismatchedOrFailedHistoryBlocks() {
        var expected = DatabaseMigrationPreflightInitializer.inventory(List.of(first));
        assertThrows(IllegalStateException.class, () -> DatabaseMigrationPreflightInitializer.verify(expected,List.of(row("1", first, false))));
        assertThrows(IllegalStateException.class, () -> DatabaseMigrationPreflightInitializer.verify(expected,List.of(row("1", second, true))));
    }
    @Test void duplicateOrGapBlocks() {
        assertThrows(IllegalStateException.class, () -> DatabaseMigrationPreflightInitializer.inventory(List.of(first, first)));
        assertThrows(IllegalStateException.class, () -> DatabaseMigrationPreflightInitializer.inventory(List.of(second)));
    }
}
