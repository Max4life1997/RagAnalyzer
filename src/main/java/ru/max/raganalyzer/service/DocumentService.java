package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.max.raganalyzer.dto.ChunkDto;
import ru.max.raganalyzer.dto.DocumentDto;
import ru.max.raganalyzer.dto.DocumentUploadResponse;
import ru.max.raganalyzer.dto.TextExtractionResult;
import ru.max.raganalyzer.entity.DocumentChunkEntity;
import ru.max.raganalyzer.entity.DocumentEntity;
import ru.max.raganalyzer.repository.DocumentChunkRepository;
import ru.max.raganalyzer.repository.DocumentRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final ChunkService chunkService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public DocumentService(
            FileStorageService fileStorageService,
            TextExtractionService textExtractionService,
            ChunkService chunkService,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService
    ) {
        this.fileStorageService = fileStorageService;
        this.textExtractionService = textExtractionService;
        this.chunkService = chunkService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        validateFile(file);

        Path storedPath = fileStorageService.save(file);

        TextExtractionResult extractionResult = textExtractionService.extractText(storedPath);

        List<ChunkDto> chunks = chunkService.splitIntoChunks(extractionResult.text());

        DocumentEntity document = new DocumentEntity(
                file.getOriginalFilename(),
                storedPath.toString(),
                file.getSize(),
                extractionResult.length()
        );

        DocumentEntity savedDocument = documentRepository.save(document);

        try {
            List<DocumentChunkEntity> chunkEntities = chunks.stream()
                    .map(chunk -> new DocumentChunkEntity(
                            savedDocument,
                            chunk.index(),
                            chunk.content(),
                            chunk.length()
                    ))
                    .toList();

            List<DocumentChunkEntity> savedChunks = documentChunkRepository.saveAll(chunkEntities);

            documentChunkRepository.flush();

            List<String> chunkTexts = savedChunks.stream()
                    .map(DocumentChunkEntity::getContent)
                    .toList();

            List<List<Double>> embeddings = embeddingService.createEmbeddings(chunkTexts);

            for (int i = 0; i < savedChunks.size(); i++) {
                DocumentChunkEntity savedChunk = savedChunks.get(i);
                List<Double> embedding = embeddings.get(i);

                vectorStoreService.updateChunkEmbedding(savedChunk.getId(), embedding);
            }

            savedDocument.markIndexed(chunks.size());
        } catch (Exception e) {
            savedDocument.markFailed(e.getMessage());
        }

        return new DocumentUploadResponse(
                savedDocument.getId(),
                savedDocument.getOriginalFileName(),
                savedDocument.getSizeBytes(),
                savedDocument.getStoredPath(),
                savedDocument.getTextLength(),
                extractionResult.preview(),
                chunks.size(),
                chunks,
                savedDocument.getStatus().name(),
                savedDocument.getErrorMessage()
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> getAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(this::toDocumentDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChunkDto> getDocumentChunks(UUID documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new IllegalArgumentException("Документ не найден: " + documentId);
        }

        return documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId)
                .stream()
                .map(chunk -> new ChunkDto(
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        chunk.getLength()
                ))
                .toList();
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
                document.getCreatedAt()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой или не передан");
        }

        String fileName = file.getOriginalFilename();

        if (fileName == null || !isSupportedFile(fileName)) {
            throw new IllegalArgumentException("Поддерживаются только .txt, .pdf, .docx файлы");
        }
    }

    private boolean isSupportedFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();

        return lowerFileName.endsWith(".txt")
                || lowerFileName.endsWith(".pdf")
                || lowerFileName.endsWith(".docx");
    }
}