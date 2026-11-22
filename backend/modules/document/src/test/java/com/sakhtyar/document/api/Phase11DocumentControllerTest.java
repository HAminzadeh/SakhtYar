package com.sakhtyar.document.api;

import com.sakhtyar.document.application.DocumentService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Phase11DocumentControllerTest {
    @Test
    void downloadedContentIsAttachmentWithSafeNoSniffHeaders() throws Exception {
        DocumentService service = mock(DocumentService.class);
        UUID id = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        when(service.download(id)).thenReturn(new DocumentService.DownloadedDocument(
                "قرارداد.pdf", MediaType.APPLICATION_PDF, bytes.length,
                new InputStreamResource(new ByteArrayInputStream(bytes))));
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentController(service)).build();
        mvc.perform(get("/api/v1/documents/{id}/content", id))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(bytes));
    }
}
