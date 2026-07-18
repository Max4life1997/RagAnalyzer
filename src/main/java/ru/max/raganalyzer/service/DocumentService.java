package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ru.max.raganalyzer.dto.ChunkDto;
import ru.max.raganalyzer.dto.DocumentDto;
import ru.max.raganalyzer.dto.DocumentUploadResponse;
import ru.max.raganalyzer.entity.DocumentEntity;
import ru.max.raganalyzer.repository.DocumentChunkRepository;
import ru.max.raganalyzer.repository.DocumentRepository;
import ru.max.raganalyzer.repository.FolderRepository;
import ru.max.raganalyzer.security.SecurityUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final FileStorageService fileStorageService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentIndexingService documentIndexingService;
    private final ImageExtractionService imageExtractionService;
    private final FolderRepository folderRepository;

    public DocumentService(
            FileStorageService fileStorageService,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentIndexingService documentIndexingService,
            ImageExtractionService imageExtractionService,
            FolderRepository folderRepository
    ) {
        this.fileStorageService = fileStorageService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentIndexingService = documentIndexingService;
        this.imageExtractionService = imageExtractionService;
        this.folderRepository = folderRepository;
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        return uploadDocument(file, null);
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file, UUID folderId) {
        validateFile(file);

        Path storedPath = fileStorageService.save(file);

        DocumentEntity document = new DocumentEntity(
                file.getOriginalFilename(),
                storedPath.toString(),
                file.getSize(),
                0
        );

        // Привязываем документ к текущему пользователю
        UUID userId = getCurrentUserId();
        if (userId != null) {
            document.setUserId(userId);
        }

        if (folderId != null) {
            if (userId == null || !folderRepository.existsByIdAndUserId(folderId, userId)) {
                throw new IllegalArgumentException("Папка не найдена");
            }
            document.setFolderId(folderId);
        }

        DocumentEntity savedDocument = documentRepository.save(document);
        scheduleIndexingAfterCommit(savedDocument.getId());

        return new DocumentUploadResponse(
                savedDocument.getId(),
                savedDocument.getOriginalFileName(),
                savedDocument.getSizeBytes(),
                savedDocument.getStoredPath(),
                savedDocument.getTextLength(),
                "",
                0,
                List.of(),
                savedDocument.getStatus().name(),
                savedDocument.getErrorMessage()
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> getAllDocuments() {
        UUID userId = getCurrentUserId();
        List<DocumentEntity> docs = (userId != null)
                ? documentRepository.findByUserId(userId)
                : documentRepository.findAll(); // Telegram-бот
        return docs.stream().map(this::toDocumentDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ChunkDto> getDocumentChunks(UUID documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }

        return documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId)
                .stream()
                .map(chunk -> new ChunkDto(
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        chunk.getLength(),
                        chunk.getPageNumber()
                ))
                .toList();
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        UUID userId = getCurrentUserId();
        DocumentEntity document = (userId != null)
                ? documentRepository.findByIdAndUserId(documentId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("Документ не найден или нет доступа"))
                : documentRepository.findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException("Документ не найден"));

        documentChunkRepository.deleteByDocumentId(documentId);
        imageExtractionService.deleteImages(documentId);
        documentRepository.deleteById(documentId);
        fileStorageService.deleteFile(document.getStoredPath());
    }

    @Transactional
    public DocumentDto moveDocument(UUID documentId, UUID folderId) {
        UUID userId = getCurrentUserId();
        DocumentEntity document = (userId != null)
                ? documentRepository.findByIdAndUserId(documentId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("Документ не найден или нет доступа"))
                : documentRepository.findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException("Документ не найден"));

        if (folderId != null) {
            if (userId == null || !folderRepository.existsByIdAndUserId(folderId, userId)) {
                throw new IllegalArgumentException("Папка не найдена");
            }
        }

        document.setFolderId(folderId);
        return toDocumentDto(documentRepository.save(document));
    }

    private UUID getCurrentUserId() {
        try {
            return SecurityUtils.currentUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private void scheduleIndexingAfterCommit(UUID documentId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            documentIndexingService.indexDocument(documentId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentIndexingService.indexDocument(documentId);
            }
        });
    }

    private DocumentDto toDocumentDto(DocumentEntity document) {
        return new DocumentDto(
                document.getId(),
                document.getOriginalFileName(),
                document.getStoredPath(),
                document.getSizeBytes(),
                document.getTextLength(),
                document.getChunksCount(),
                document.getStatus().name(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getFolderId()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or missing");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null || !isSupportedFile(fileName)) {
            throw new IllegalArgumentException("Only .txt, .pdf and .docx files are supported");
        }
    }

    private boolean isSupportedFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();

        return lowerFileName.endsWith(".txt")
                || lowerFileName.endsWith(".pdf")
                || lowerFileName.endsWith(".docx");
    }
}
