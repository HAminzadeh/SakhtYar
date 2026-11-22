package com.sakhtyar.knowledge.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pre-extraction safety gate for stored knowledge ZIPs. No database/network operations.
 * The caller must also verify the manifest and execute domain import transactionally.
 */
public final class Phase04ArchiveGuard {
    private Phase04ArchiveGuard() {}
    private static final long MAX_ENTRY = 512L * 1024 * 1024;
    private static final long RATIO_THRESHOLD_BYTES = 1024L * 1024;
    private static final int MAX_RATIO = 400;

    public record Inspection(int entries, long uncompressedBytes) {}

    /** Prevent a swapped MinIO object from exhausting the runner disk before ZIP inspection. */
    public static long copyBounded(InputStream input, Path target, long limit) throws IOException {
        if (input == null || target == null || limit < 1 || Files.exists(target))
            throw new SecurityException("Unsafe bounded ZIP copy target");
        long total = 0;
        boolean complete = false;
        try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > limit - total) throw new SecurityException("Stored ZIP exceeds maximum bytes");
                total += count;
                output.write(buffer, 0, count);
            }
            complete = true;
            return total;
        } finally {
            if (!complete) Files.deleteIfExists(target);
        }
    }

    public static Inspection inspect(Path archive, long maxArchiveBytes, int maxEntries,
                                     long maxExpandedBytes) throws IOException {
        if (archive == null || !Files.isRegularFile(archive) || Files.isSymbolicLink(archive))
            throw new SecurityException("Missing or unsafe dataset ZIP");
        long size = Files.size(archive);
        if (size < 4 || size > maxArchiveBytes || maxEntries < 1 || maxExpandedBytes < 1)
            throw new SecurityException("ZIP size or inspection limits invalid");
        Set<String> encountered = new HashSet<>();
        int count = 0;
        long expanded = 0;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > maxEntries) throw new SecurityException("ZIP entry limit exceeded");
                String name = requireSafePath(entry.getName());
                if (!encountered.add(name.toLowerCase(Locale.ROOT)))
                    throw new SecurityException("Duplicate or case-colliding ZIP name");
                if (entry.isDirectory()) continue;
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED)
                    throw new SecurityException("Unsupported ZIP compression method");
                long announced = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (announced < 0 || announced > MAX_ENTRY || compressed < 0)
                    throw new SecurityException("Unknown or excessive ZIP entry size");
                if (announced > RATIO_THRESHOLD_BYTES &&
                    (compressed == 0 || announced / compressed > MAX_RATIO))
                    throw new SecurityException("Suspicious ZIP compression ratio");
                if (announced > maxExpandedBytes - expanded)
                    throw new SecurityException("ZIP expanded size exceeded");
                CRC32 crc = new CRC32();
                long actual = 0;
                try (InputStream stream = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = stream.read(buffer)) != -1) {
                        actual += n;
                        if (actual > MAX_ENTRY || actual > maxExpandedBytes - expanded)
                            throw new SecurityException("ZIP stream exceeds safe limits");
                        crc.update(buffer, 0, n);
                    }
                }
                if (actual != announced || entry.getCrc() != crc.getValue())
                    throw new SecurityException("ZIP entry length or CRC mismatch");
                expanded += actual;
            }
        }
        if (count == 0) throw new SecurityException("Empty ZIP denied");
        return new Inspection(count, expanded);
    }

    /** Reject drive letters, absolute paths, backslash and normalization ambiguity. */
    public static String requireSafePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") ||
            path.contains("\\") || path.contains(":") || path.indexOf('\0') >= 0 ||
            path.chars().anyMatch(c -> c < 32 || c == 127))
            throw new SecurityException("Unsafe ZIP path");
        String raw = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (raw.isEmpty() || raw.length() > 1000)
            throw new SecurityException("Unsafe ZIP path length");
        for (String segment : raw.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") ||
                segment.endsWith(".") || segment.endsWith(" "))
                throw new SecurityException("Unsafe ZIP segment");
        }
        return path;
    }
}
