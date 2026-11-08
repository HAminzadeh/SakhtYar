package com.sakhtyar.knowledge.application;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Phase04ArchiveGuardTest {
    @TempDir Path temp;
    private Path zip(String... paths) throws IOException {
        Path file = Files.createTempFile(temp, "phase04-", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String name: paths) {
                out.putNextEntry(new ZipEntry(name));
                out.write("synthetic only".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }
    @Test void acceptsSmallControlledArchive() throws Exception {
        var report = Phase04ArchiveGuard.inspect(zip("manifest.json", "content/data.jsonl"), 1_000_000, 4, 1_000_000);
        assertEquals(2, report.entries());
        assertTrue(report.uncompressedBytes() > 0);
    }
    @Test void rejectsTraversalAndCaseCollisions() throws Exception {
        assertThrows(SecurityException.class, () -> Phase04ArchiveGuard.inspect(zip("../outside.txt"), 1_000_000, 4, 1_000_000));
        assertThrows(SecurityException.class, () -> Phase04ArchiveGuard.inspect(zip("Manifest.json", "manifest.json"), 1_000_000, 4, 1_000_000));
    }
    @Test void rejectsEntryCountAndExpandedLimits() throws Exception {
        Path z=zip("one", "two");
        assertThrows(SecurityException.class, () -> Phase04ArchiveGuard.inspect(z, 1_000_000, 1, 1_000_000));
        assertThrows(SecurityException.class, () -> Phase04ArchiveGuard.inspect(z, 1_000_000, 4, 5));
    }
    @Test void rejectsMalformedArchive() throws Exception {
        Path file = temp.resolve("broken.zip");
        Files.writeString(file,"this-is-not-zip");
        assertThrows(IOException.class, () -> Phase04ArchiveGuard.inspect(file,10000,10,10000));
    }
}
