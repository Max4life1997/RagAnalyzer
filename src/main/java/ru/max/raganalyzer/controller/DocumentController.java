package ru.max.raganalyzer.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.max.raganalyzer.dto.ChunkDto;
import ru.max.raganalyzer.dto.DocumentDto;
import ru.max.raganalyzer.dto.DocumentUploadResponse;
import ru.max.raganalyzer.dto.MoveDocumentRequest;
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
    public DocumentUploadResponse uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) UUID folderId
    ) {
        return documentService.uploadDocument(file, folderId);
    }

    @GetMapping
    public List<DocumentDto> getAllDocuments() {
        return documentService.getAllDocuments();
    }

    @GetMapping("/{documentId}/chunks")
    public List<ChunkDto> getDocumentChunks(@PathVariable UUID documentId) {
        return documentService.getDocumentChunks(documentId);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{documentId}/move")
    public DocumentDto moveDocument(@PathVariable UUID documentId, @RequestBody MoveDocumentRequest request) {
        return documentService.moveDocument(documentId, request.folderId());
    }
}
