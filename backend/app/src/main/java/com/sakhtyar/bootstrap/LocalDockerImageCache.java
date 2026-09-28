package com.sakhtyar.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LocalDockerImageCache {

    record Entry(String image, String imageId, String file) {
    }

    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);

    private final Path cacheDirectory;
    private final DockerCommandRunner docker;
    private final Map<String, Entry> entries;

    LocalDockerImageCache(Path projectRoot, DockerCommandRunner docker) {
        this.cacheDirectory = projectRoot.resolve(".local").resolve("docker-images");
        this.docker = docker;
        this.entries = readManifest(cacheDirectory.resolve("manifest.json"));
    }

    void ensureKnownGoodImage(String image) {
        Entry entry = entries.get(image);

        if (entry == null) {
            if (imageExists(image)) {
                LocalInfrastructureLog.warn(
                        "No cached known-good entry for %s; existing local image will be used.".formatted(image)
                );
                return;
            }

            throw new IllegalStateException(
                    "Required Docker image is missing and has no local cache entry: " + image
            );
        }

        if (!imageExists(image)) {
            LocalInfrastructureLog.warn("Image is missing: " + image);
            restore(entry);
            verifyExpectedId(entry);
            return;
        }

        String currentId = imageId(image);
        if (entry.imageId().equals(currentId)) {
            LocalInfrastructureLog.ok("Image OK: " + image);
            return;
        }

        LocalInfrastructureLog.warn(
                "Image differs from cached known-good version: %s".formatted(image)
        );
        LocalInfrastructureLog.step("Restoring cached image: " + image);
        restore(entry);
        verifyExpectedId(entry);
        LocalInfrastructureLog.ok("Image restored: " + image);
    }

    private void restore(Entry entry) {
        Path tar = cacheDirectory.resolve(entry.file());
        if (!Files.isRegularFile(tar)) {
            throw new IllegalStateException(
                    "Cached Docker image file is missing: " + tar
            );
        }

        DockerCommandRunner.Result result = docker.docker(
                Duration.ofMinutes(5),
                "load",
                "-i",
                tar.toString()
        );

        result.lines().forEach(LocalInfrastructureLog::info);

        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "docker load failed for " + entry.image()
            );
        }
    }

    private void verifyExpectedId(Entry entry) {
        String actual = imageId(entry.image());
        if (!entry.imageId().equals(actual)) {
            throw new IllegalStateException(
                    "Cached image restore did not produce the expected image ID for "
                            + entry.image()
                            + ". expected=" + entry.imageId()
                            + ", actual=" + actual
            );
        }
    }

    private boolean imageExists(String image) {
        DockerCommandRunner.Result result = docker.docker(
                Duration.ofSeconds(20),
                "image",
                "inspect",
                image
        );
        return result.exitCode() == 0;
    }

    private String imageId(String image) {
        DockerCommandRunner.Result result = docker.docker(
                Duration.ofSeconds(20),
                "image",
                "inspect",
                image,
                "--format",
                "{{.Id}}"
        );

        if (result.exitCode() != 0) {
            throw new IllegalStateException("Could not inspect Docker image: " + image);
        }

        return result.firstLine();
    }

    private static Map<String, Entry> readManifest(Path manifestPath) {
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalStateException(
                    "Docker image cache manifest not found: " + manifestPath
            );
        }

        try {
            String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
            Map<String, Entry> result = new LinkedHashMap<>();

            Matcher objectMatcher = OBJECT_PATTERN.matcher(json);
            while (objectMatcher.find()) {
                String object = objectMatcher.group(1);
                String image = jsonStringField(object, "image");
                String imageId = jsonStringField(object, "imageId");
                String file = jsonStringField(object, "file");

                if (image != null && imageId != null && file != null) {
                    result.put(image, new Entry(image, imageId, file));
                }
            }

            if (result.isEmpty()) {
                throw new IllegalStateException(
                        "Docker image cache manifest contains no usable entries: " + manifestPath
                );
            }

            return Map.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read Docker image cache manifest: " + manifestPath,
                    e
            );
        }
    }

    private static String jsonStringField(String object, String field) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\""
        );

        Matcher matcher = pattern.matcher(object);
        if (!matcher.find()) {
            return null;
        }

        return unescapeJson(matcher.group(1));
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\/", "/");
    }
}