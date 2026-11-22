package com.sakhtyar.document.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class Phase11OfficeValidationTest {
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private byte[] zip(Map<String, String> parts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (var entry : parts.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void validMinimalDocxAndXlsxContainersAreAccepted() throws IOException {
        var docx = new MockMultipartFile("file", "report.docx", DOCX, zip(Map.of(
                "[Content_Types].xml", "types", "word/document.xml", "document")));
        var xlsx = new MockMultipartFile("file", "data.xlsx", XLSX, zip(Map.of(
                "[Content_Types].xml", "types", "xl/workbook.xml", "workbook")));
        assertDoesNotThrow(() -> Phase03DocumentSafety.validateUpload(docx));
        assertDoesNotThrow(() -> Phase03DocumentSafety.validateUpload(xlsx));
    }

    @Test
    void arbitraryZipIsNotOfficeDocument() throws IOException {
        var fake = new MockMultipartFile("file", "fake.docx", DOCX,
                zip(Map.of("payload.bin", "arbitrary zip")));
        assertThrows(IllegalArgumentException.class, () -> Phase03DocumentSafety.validateUpload(fake));
    }

    @Test
    void traversalAndMacroPartsAreRejected() throws IOException {
        var traversal = new MockMultipartFile("file", "fake.docx", DOCX, zip(Map.of(
                "[Content_Types].xml", "types", "word/document.xml", "document", "../escape", "bad")));
        var macro = new MockMultipartFile("file", "macro.docx", DOCX, zip(Map.of(
                "[Content_Types].xml", "types", "word/document.xml", "document",
                "word/vbaProject.bin", "bad")));
        assertThrows(IllegalArgumentException.class, () -> Phase03DocumentSafety.validateUpload(traversal));
        assertThrows(IllegalArgumentException.class, () -> Phase03DocumentSafety.validateUpload(macro));
    }
}
