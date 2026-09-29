package com.sakhtyar.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LocalInfrastructureInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String COMPOSE_FILE = "compose.spring-local.yml";
    private static final Duration SHORT = Duration.ofSeconds(30);
    private static final Duration COMPOSE_TIMEOUT = Duration.ofMinutes(8);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        if (!isLocal(environment)) {
            return;
        }

        Path projectRoot = findProjectRoot();
        Path composePath = projectRoot.resolve(COMPOSE_FILE);
        DockerCommandRunner docker = new DockerCommandRunner(projectRoot);
        LocalDockerImageCache cache = new LocalDockerImageCache(projectRoot, docker);

        LocalInfrastructureLog.step("============================================================");
        LocalInfrastructureLog.step("SakhtYar local environment bootstrap started");
        LocalInfrastructureLog.step("============================================================");

        checkDocker(docker);
        checkImages(docker, cache, composePath);
        logCurrentServices(docker, composePath);
        startOrRepairServices(docker, composePath);
        validateServices(docker, composePath);
        verifyEndpoints(docker, composePath);

        LocalInfrastructureLog.ok("============================================================");
        LocalInfrastructureLog.ok("Local infrastructure is READY.");
        LocalInfrastructureLog.ok("UI (start from IntelliJ npm dev): http://localhost:5173");
        LocalInfrastructureLog.ok("Backend will start next on: http://localhost:8080");
        LocalInfrastructureLog.ok("============================================================");
    }

    private void checkDocker(DockerCommandRunner docker) {
        LocalInfrastructureLog.step("[1/7] Checking Docker Desktop...");

        DockerCommandRunner.Result result = docker.docker(
                SHORT,
                "info"
        );

        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "Docker Desktop is not ready. Start Docker Desktop and run SakhtYar again."
            );
        }

        LocalInfrastructureLog.ok("Docker Desktop is ready.");
    }

    private void checkImages(
            DockerCommandRunner docker,
            LocalDockerImageCache cache,
            Path composePath
    ) {
        LocalInfrastructureLog.step(
                "[2/7] Comparing required images with cached known-good images..."
        );

        DockerCommandRunner.Result result = docker.docker(
                SHORT,
                "compose",
                "-f",
                composePath.toString(),
                "config",
                "--images"
        );

        requireSuccess(result, "Could not read required images from Docker Compose.");

        List<String> images = result.lines().stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();

        for (String image : images) {
            cache.ensureKnownGoodImage(image);
        }
    }

    private void logCurrentServices(
            DockerCommandRunner docker,
            Path composePath
    ) {
        LocalInfrastructureLog.step("[3/7] Checking current SakhtYar containers...");

        for (String service : composeServices(docker, composePath)) {
            String id = composeContainerId(docker, composePath, service);

            if (id.isBlank()) {
                LocalInfrastructureLog.warn(
                        service + ": container missing; it will be created."
                );
                continue;
            }

            LocalInfrastructureLog.info(
                    service + ": " + containerState(docker, id)
            );
        }
    }

    private void startOrRepairServices(
            DockerCommandRunner docker,
            Path composePath
    ) {
        LocalInfrastructureLog.step(
                "[4/7] Starting/repairing services using local cached images only..."
        );

        DockerCommandRunner.Result result = docker.docker(
                COMPOSE_TIMEOUT,
                "compose",
                "-f",
                composePath.toString(),
                "up",
                "-d",
                "--wait",
                "--pull",
                "never",
                "--no-build"
        );

        result.lines().forEach(LocalInfrastructureLog::info);
        requireSuccess(result, "docker compose up failed.");

        LocalInfrastructureLog.ok("Compose startup completed.");
    }

    private void validateServices(
            DockerCommandRunner docker,
            Path composePath
    ) {
        LocalInfrastructureLog.step("[5/7] Validating service states...");

        boolean failed = false;

        for (String service : composeServices(docker, composePath)) {
            String id = composeContainerId(docker, composePath, service);

            if (id.isBlank()) {
                LocalInfrastructureLog.error(service + ": MISSING");
                failed = true;
                continue;
            }

            String state = containerState(docker, id);

            if (state.startsWith("running|healthy")
                    || state.startsWith("running|no-healthcheck")) {
                LocalInfrastructureLog.ok(service + ": " + state);
            } else if (state.startsWith("running|starting")) {
                LocalInfrastructureLog.warn(service + ": " + state);
            } else {
                LocalInfrastructureLog.error(service + ": " + state);
                failed = true;
            }
        }

        if (failed) {
            throw new IllegalStateException(
                    "One or more local Docker services are not running correctly."
            );
        }
    }

    private void verifyEndpoints(
            DockerCommandRunner docker,
            Path composePath
    ) {
        LocalInfrastructureLog.step("[6/6] Checking MinIO...");
        waitForHttp(
                "MinIO",
                "http://127.0.0.1:9000/minio/health/live",
                Duration.ofSeconds(60)
        );
    }
    private static List<String> composeServices(
            DockerCommandRunner docker,
            Path composePath
    ) {
        DockerCommandRunner.Result result = docker.docker(
                SHORT,
                "compose",
                "-f",
                composePath.toString(),
                "config",
                "--services"
        );

        requireSuccess(result, "Could not read Docker Compose services.");

        return result.lines().stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String composeContainerId(
            DockerCommandRunner docker,
            Path composePath,
            String service
    ) {
        DockerCommandRunner.Result result = docker.docker(
                SHORT,
                "compose",
                "-f",
                composePath.toString(),
                "ps",
                "-q",
                service
        );

        if (result.exitCode() != 0) {
            return "";
        }

        return result.firstLine();
    }

    private static String containerState(
            DockerCommandRunner docker,
            String containerId
    ) {
        DockerCommandRunner.Result result = docker.docker(
                SHORT,
                "inspect",
                "-f",
                "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}",
                containerId
        );

        if (result.exitCode() != 0) {
            return "unknown";
        }

        return result.firstLine();
    }

    private static String resolveUiUrl(
            DockerCommandRunner docker,
            Path composePath
    ) {
        List<String> services = composeServices(docker, composePath);
        String frontend = services.stream()
                .filter(service -> service.toLowerCase(Locale.ROOT).contains("frontend"))
                .findFirst()
                .orElse(null);

        if (frontend == null) {
            return "http://localhost:5173";
        }

        DockerCommandRunner.Result result = docker.docker(
                SHORT,
                "compose",
                "-f",
                composePath.toString(),
                "port",
                frontend,
                "5173"
        );

        if (result.exitCode() == 0) {
            String line = result.firstLine();
            int colon = line.lastIndexOf(':');

            if (colon >= 0 && colon < line.length() - 1) {
                String port = line.substring(colon + 1).trim();
                if (port.chars().allMatch(Character::isDigit)) {
                    return "http://localhost:" + port;
                }
            }
        }

        return "http://localhost:5173";
    }

    private static void waitForHttp(
            String name,
            String url,
            Duration timeout
    ) {
        LocalInfrastructureLog.step("Checking " + name + " at " + url);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<Void> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.discarding()
                );

                if (response.statusCode() >= 200
                        && response.statusCode() < 500) {
                    LocalInfrastructureLog.ok(name + " is reachable.");
                    return;
                }
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(Duration.ofSeconds(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for " + name,
                        e
                );
            }
        }

        throw new IllegalStateException(
                name + " did not become reachable within "
                        + timeout.toSeconds()
                        + " seconds: "
                        + url
        );
    }

    private static void requireSuccess(
            DockerCommandRunner.Result result,
            String message
    ) {
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    message
                            + System.lineSeparator()
                            + result.joined()
            );
        }
    }

    private static boolean isLocal(ConfigurableEnvironment environment) {
        if (environment.matchesProfiles("local")) {
            return true;
        }

        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return Arrays.stream(environment.getDefaultProfiles())
                    .anyMatch("local"::equalsIgnoreCase);
        }

        return false;
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir"))
                .toAbsolutePath()
                .normalize();

        for (int depth = 0; depth < 8 && current != null; depth++) {
            if (Files.isRegularFile(current.resolve(COMPOSE_FILE))) {
                return current;
            }

            current = current.getParent();
        }

        throw new IllegalStateException(
                "Could not find "
                        + COMPOSE_FILE
                        + " from working directory "
                        + System.getProperty("user.dir")
        );
    }
}