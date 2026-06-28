package ru.max.raganalyzer.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.max.raganalyzer.dto.ChunkDto;
import ru.max.raganalyzer.dto.DocumentDto;
import ru.max.raganalyzer.dto.DocumentUploadResponse;
import ru.max.raganalyzer.service.DocumentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public DocumentUploadResponse uploadDocument(@RequestParam("file") MultipartFile file) {
        return documentService.uploadDocument(file);
    }

    @GetMapping
    public List<DocumentDto> getAllDocuments() {
        return documentService.getAllDocuments();
    }

    @GetMapping("/{documentId}/chunks")
    public List<ChunkDto> getDocumentChunks(@PathVariable UUID documentId) {
        return documentService.getDocumentChunks(documentId);
    }
}