package ru.max.raganalyzer.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.max.raganalyzer.dto.ChunkDto;
import ru.max.raganalyzer.dto.TextExtractionResult;
import ru.max.raganalyzer.entity.DocumentChunkEntity;
import ru.max.raganalyzer.entity.DocumentEntity;
import ru.max.raganalyzer.entity.DocumentStatus;
import ru.max.raganalyzer.repository.DocumentChunkRepository;
import ru.max.raganalyzer.repository.DocumentRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIndexingService {

    private final TextExtractionService textExtractionService;
    private final ChunkService chunkService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ImageExtractionService imageExtractionService;

    public DocumentIndexingService(
            TextExtractionService textExtractionService,
            ChunkService chunkService,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            ImageExtractionService imageExtractionService
    ) {
        this.textExtractionService = textExtractionService;
        this.chunkService = chunkService;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.imageExtractionService = imageExtractionService;
    }

    @Async
    @Transactional
    public void indexDocument(UUID documentId) {
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return;
        }

        Path storedPath = Path.of(document.getStoredPath());

        try {
            TextExtractionResult extractionResult = textExtractionService.extractText(storedPath);
            List<ChunkDto> chunks = chunkService.splitIntoChunks(extractionResult.text());

            List<DocumentChunkEntity> chunkEntities = chunks.stream()
                    .map(chunk -> new DocumentChunkEntity(
                            document,
                            chunk.index(),
                            chunk.content(),
                            chunk.length(),
                            chunk.pageNumber()
                    ))
                    .toList();

            List<DocumentChunkEntity> savedChunks = documentChunkRepository.saveAll(chunkEntities);
            documentChunkRepository.flush();

            List<String> chunkTexts = savedChunks.stream()
                    .map(DocumentChunkEntity::getContent)
                    .toList();

            List<List<Double>> embeddings = embeddingService.createEmbeddings(chunkTexts);

            for (int i = 0; i < savedChunks.size(); i++) {
                vectorStoreService.updateChunkEmbedding(savedChunks.get(i).getId(), embeddings.get(i));
            }

            document.updateTextLength(extractionResult.length());
            document.markIndexed(chunks.size());

            extractImages(storedPath, document);
        } catch (Exception e) {
            document.markFailed(e.getMessage());
        }
    }

    private void extractImages(Path storedPath, DocumentEntity document) {
        if (document.getStatus() != DocumentStatus.INDEXED) {
            return;
        }

        try {
            imageExtractionService.extractAndSave(storedPath.toAbsolutePath(), document);
        } catch (Exception e) {
            System.err.println("Warning: failed to extract images from "
                    + document.getOriginalFileName() + ": " + e.getMessage());
        }
    }
}
