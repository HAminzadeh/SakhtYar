package com.sakhtyar.document.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class Phase03DocumentSafetyTest {
    @Test void validPdfSignatureAcceptedWithoutOpeningStorage() {
        var file = new MockMultipartFile("file","report.pdf","application/pdf",
                "%PDF-1.7 synthetic".getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> Phase03DocumentSafety.validateUpload(file));
    }
    @Test void disguisedExecutableIsRejected() {
        var file = new MockMultipartFile("file","report.pdf","application/pdf",
                "MZ-fake".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> Phase03DocumentSafety.validateUpload(file));
    }
    @Test void disallowedMimeAndEmptyUploadRejected() {
        var file = new MockMultipartFile("file","x.exe","application/x-msdownload",new byte[]{77,90});
        assertThrows(IllegalArgumentException.class, () -> Phase03DocumentSafety.validateUpload(file));
        assertThrows(IllegalArgumentException.class, () -> Phase03DocumentSafety.validateUpload(
                new MockMultipartFile("file",new byte[0])));
    }
    @Test void crossCaseObjectKeyOrPathTraversalRejected() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), document = UUID.randomUUID();
        String correct = "cases/"+a+"/"+document+"/report.pdf";
        assertDoesNotThrow(() -> Phase03DocumentSafety.requireOwnedObjectKey(a,document,correct));
        assertThrows(SecurityException.class, () -> Phase03DocumentSafety.requireOwnedObjectKey(
                a,document,"cases/"+b+"/"+document+"/report.pdf"));
        assertThrows(SecurityException.class, () -> Phase03DocumentSafety.requireOwnedObjectKey(
                a,document,correct+"/../other"));
    }
    @Test void filenameControlsAndSeparatorsAreScrubbed() {
        assertEquals("document.bin", Phase03DocumentSafety.safeFilename(".."));
        assertEquals("hello_world.txt", Phase03DocumentSafety.safeFilename("hello/world.txt"));
        assertFalse(Phase03DocumentSafety.safeFilename("x\nreport.pdf").contains("\n"));
    }
}
