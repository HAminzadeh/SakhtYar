package com.sakhtyar.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Runs the reviewed, local-only database migration lifecycle before the read-only
 * migration preflight and before Spring creates the datasource/JPA infrastructure.
 * Production and the shared development profile can never use this initializer.
 */
public final class DatabaseMigrationStartupInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String ENABLED = "app.database-migration.startup-enabled";
    private static final String RUNTIME_ROLE = "sakhtyar_runtime";
    private static final Pattern LOCAL_DATABASE_URL = Pattern.compile(
            "^jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):5432/sakhtyar(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final String MIGRATION_RUNNER = "scripts/database/startup_migration.py";
    private static final String COMPOSE_FILE = "compose.spring-local.yml";

    static boolean enabled(Environment environment) {
        return environment.getProperty(ENABLED, Boolean.class, false);
    }

    static void validate(Environment environment) {
        if (!enabled(environment)) return;

        if (!environment.matchesProfiles("local")
                || environment.matchesProfiles("development", "production")) {
            throw new IllegalStateException(
                    "Automatic database migration is restricted to the isolated local profile."
            );
        }

        if (environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Keep spring.flyway.enabled=false; SakhtYar uses the guarded migration lifecycle."
            );
        }

        String url = environment.getProperty("spring.datasource.url", "");
        if (!LOCAL_DATABASE_URL.matcher(url).matches()) {
            throw new IllegalStateException(
                    "Automatic migration refused: local profile datasource must target "
                            + "localhost:5432/sakhtyar. No remote database will be migrated."
            );
        }

    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (!enabled(environment)) return;
        validate(environment);

        Path root = findProjectRoot(Paths.get(System.getProperty("user.dir")));
        Path runner = root.resolve(MIGRATION_RUNNER);
        if (!Files.isRegularFile(runner) || !Files.isRegularFile(root.resolve(COMPOSE_FILE))) {
            throw new IllegalStateException(
                    "Automatic migration could not locate the SakhtYar repository root "
                            + "or local Compose file. Start IntelliJ from the project workspace."
            );
        }

        String python = environment.getProperty(
                "app.database-migration.python-executable", "python"
        ).trim();
        if (python.isEmpty()) python = "python";

        String runtimeUser = environment.getProperty("spring.datasource.username", "");
        String runtimePassword = environment.getProperty("spring.datasource.password", "");
        ProcessBuilder builder = new ProcessBuilder(
                python,
                "-B",
                runner.toString(),
                "--root", root.toString(),
                "--profile", "local",
                "--runtime-role", RUNTIME_ROLE,
                "--app-runtime-user", runtimeUser,
                "--app-runtime-password-present", Boolean.toString(!runtimePassword.isBlank())
        );
        builder.directory(root.toFile());
        builder.inheritIO();
        builder.environment().put("SAKHTYAR_STARTUP_MIGRATION_PROFILE", "local");

        System.out.println("[SakhtYar Migration] Local startup migration gate started.");
        System.out.println("[SakhtYar Migration] Source: " + root);
        System.out.println("[SakhtYar Migration] Runtime role: " + RUNTIME_ROLE);
        System.out.println("[SakhtYar Migration] Backup and isolated clone are required before live apply.");

        try {
            Process process = builder.start();
            // Do not forcibly terminate Flyway while it may be applying a transactional/non-transactional
            // migration. Each Docker/Git/SQL operation already has a bounded timeout in migration_lifecycle.py.
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Automatic database migration was blocked (exit " + exitCode + "). "
                                + "The application will not start against an outdated or unverified schema. "
                                + "Follow the one-time runtime-policy review instructions or inspect "
                                + "the private MigrationLifecycle report."
                );
            }
            System.out.println("[SakhtYar Migration] Local database is verified and current.");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not start the configured Python migration runner. "
                            + "Install Python 3.9+ or configure app.database-migration.python-executable.",
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for the guarded database migration. "
                            + "The child operator is not forcibly terminated to avoid interrupting Flyway. "
                            + "Inspect the lifecycle lock and private report before retrying.",
                    e
            );
        }
    }

    static Path findProjectRoot(Path workingDirectory) {
        Path current = workingDirectory.toAbsolutePath().normalize();
        for (int depth = 0; depth < 12 && current != null; depth++) {
            if (Files.isRegularFile(current.resolve(MIGRATION_RUNNER))
                    && Files.isRegularFile(current.resolve(COMPOSE_FILE))
                    && Files.exists(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "Could not find the SakhtYar repository root from the IntelliJ working directory."
        );
    }
}
