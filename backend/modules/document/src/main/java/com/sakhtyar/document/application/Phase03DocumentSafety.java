package com.sakhtyar.document.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Validates uploads and exact case/document object ownership before MinIO I/O. */
public final class Phase03DocumentSafety {
    private Phase03DocumentSafety() {}
    public static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Set<String> MIME_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp", "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public static void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0 || file.getSize() > MAX_BYTES)
            throw new IllegalArgumentException("Invalid document size; maximum 25 MiB");
        String contentType = file.getContentType();
        if (contentType == null || !MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Unsupported document content type");
        String type = contentType.toLowerCase(Locale.ROOT);
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            boolean match = switch (type) {
                case "application/pdf" -> starts(head, new int[]{0x25,0x50,0x44,0x46,0x2D});
                case "image/png" -> starts(head, new int[]{137,80,78,71,13,10,26,10});
                case "image/jpeg" -> starts(head, new int[]{255,216,255});
                case "image/webp" -> starts(head, new int[]{82,73,70,70}) &&
                        head.length >= 12 && head[8]=='W' && head[9]=='E' && head[10]=='B' && head[11]=='P';
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                     "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                        starts(head, new int[]{80,75,3,4});
                case "text/plain" -> head.length > 0 && !containsNull(head);
                default -> false;
            };
            if (!match) throw new IllegalArgumentException("File signature does not match content type");
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to inspect uploaded document", ex);
        }
    }

    public static String safeFilename(String original) {
        if (original == null || original.isBlank()) return "document.bin";
        String value = original.replace('\\','_').replace('/','_')
                .replaceAll("[\\p{Cntrl}]", "_").trim();
        if (value.length() > 240) value = value.substring(value.length() - 240);
        if (value.isBlank() || value.equals(".") || value.equals("..")) return "document.bin";
        return value;
    }

    public static void requireOwnedObjectKey(UUID caseId, UUID documentId, String key) {
        if (caseId == null || documentId == null || key == null)
            throw new SecurityException("Missing document object ownership");
        String prefix = "cases/" + caseId + "/" + documentId + "/";
        if (!key.startsWith(prefix)) throw new SecurityException("Document object outside authorized case");
        String suffix = key.substring(prefix.length());
        if (suffix.isBlank() || suffix.length() > 240 || suffix.equals(".") || suffix.equals("..") ||
                suffix.contains("/") || suffix.contains("\\") || suffix.indexOf(0) >= 0 ||
                suffix.chars().anyMatch(c -> c < 32 || c == 127))
            throw new SecurityException("Invalid document object key");
    }

    private static boolean containsNull(byte[] bytes) {
        for (byte b : bytes) if (b == 0) return true;
        return false;
    }
    private static boolean starts(byte[] bytes, int[] magic) {
        if (bytes.length < magic.length) return false;
        for (int i=0; i<magic.length; i++) if ((bytes[i] & 0xff) != magic[i]) return false;
        return true;
    }
}
