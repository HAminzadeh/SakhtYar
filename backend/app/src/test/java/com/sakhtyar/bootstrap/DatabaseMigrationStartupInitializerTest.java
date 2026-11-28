package com.sakhtyar.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationStartupInitializerTest {
    private MockEnvironment local() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("app.database-migration.startup-enabled", "true");
        env.setProperty("spring.flyway.enabled", "false");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/sakhtyar");
        return env;
    }

    @Test void disabledByDefaultDoesNotRun() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertFalse(DatabaseMigrationStartupInitializer.enabled(env));
        assertDoesNotThrow(() -> DatabaseMigrationStartupInitializer.validate(env));
    }

    @Test void localProfileAndLocalDatabaseAreAccepted() {
        assertDoesNotThrow(() -> DatabaseMigrationStartupInitializer.validate(local()));
    }

    @Test void developmentAndProductionAreRejected() {
        for (String profile : new String[]{"development", "production"}) {
            MockEnvironment env = local();
            env.setActiveProfiles(profile);
            assertThrows(IllegalStateException.class,
                    () -> DatabaseMigrationStartupInitializer.validate(env));
        }
    }

    @Test void remoteOrUnexpectedDatabaseIsRejected() {
        MockEnvironment remote = local();
        remote.setProperty("spring.datasource.url", "jdbc:postgresql://db.example.com:5432/sakhtyar");
        assertThrows(IllegalStateException.class,
                () -> DatabaseMigrationStartupInitializer.validate(remote));

        MockEnvironment wrongDb = local();
        wrongDb.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/production");
        assertThrows(IllegalStateException.class,
                () -> DatabaseMigrationStartupInitializer.validate(wrongDb));
    }

    @Test void flywayMustRemainDisabled() {
        MockEnvironment env = local();
        env.setProperty("spring.flyway.enabled", "true");
        assertThrows(IllegalStateException.class,
                () -> DatabaseMigrationStartupInitializer.validate(env));
    }

    @Test void repositoryRootIsFoundFromNestedIntellijDirectory() throws Exception {
        Path root = Files.createTempDirectory("sakhtyar-root-");
        try {
            Path nested = root.resolve("backend/app");
            Files.createDirectories(nested);
            Files.createDirectories(root.resolve(".git"));
            Files.createDirectories(root.resolve("scripts/database"));
            Files.createDirectories(root.resolve("backend/app/src/main/resources"));
            Files.createFile(root.resolve("scripts/database/startup_migration.py"));
            Files.createFile(root.resolve("compose.spring-local.yml"));
            assertEquals(root.toAbsolutePath().normalize(),
                    DatabaseMigrationStartupInitializer.findProjectRoot(nested));
        } finally {
            try (var walk = Files.walk(root)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }
}
