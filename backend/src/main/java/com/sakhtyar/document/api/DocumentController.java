package com.sakhtyar.document.api;

import com.sakhtyar.document.api.DocumentDtos.DocumentResponse;
import com.sakhtyar.document.application.DocumentService;
import com.sakhtyar.document.application.DocumentService.DownloadedDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/cases/{caseId}/documents")
    public List<DocumentResponse> list(@PathVariable UUID caseId) {
        return service.list(caseId);
    }

    @PostMapping("/api/v1/cases/{caseId}/documents")
    public DocumentResponse upload(
            @PathVariable UUID caseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return service.upload(caseId, file, authentication.getName());
    }

    @GetMapping("/api/v1/documents/{documentId}/content")
    public ResponseEntity<?> download(@PathVariable UUID documentId) {
        DownloadedDocument document = service.download(documentId);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(document.contentType())
                .contentLength(document.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(document.resource());
    }
}
