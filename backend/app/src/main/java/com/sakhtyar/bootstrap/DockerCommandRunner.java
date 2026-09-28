package com.sakhtyar.bootstrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class DockerCommandRunner {

    record Result(int exitCode, List<String> lines) {
        String joined() {
            return String.join(System.lineSeparator(), lines);
        }

        String firstLine() {
            return lines.isEmpty() ? "" : lines.getFirst().trim();
        }
    }

    private final Path workingDirectory;

    DockerCommandRunner(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    Result run(Duration timeout, String... command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            List<String> lines = new ArrayList<>();

            Thread reader = Thread.startVirtualThread(() -> {
                try (BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        synchronized (lines) {
                            lines.add(line);
                        }
                    }
                } catch (IOException ignored) {
                    // The process exit code remains the source of truth.
                }
            });

            boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "Command timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command)
                );
            }

            reader.join(Duration.ofSeconds(5));

            synchronized (lines) {
                return new Result(process.exitValue(), List.copyOf(lines));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not execute command: " + String.join(" ", command),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while executing command: " + String.join(" ", command),
                    e
            );
        }
    }

    Result docker(Duration timeout, String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "docker";
        System.arraycopy(args, 0, command, 1, args.length);
        return run(timeout, command);
    }
}