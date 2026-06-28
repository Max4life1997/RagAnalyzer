package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import ru.max.raganalyzer.dto.SearchRequest;
import ru.max.raganalyzer.dto.SearchResultDto;

import java.util.List;

@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 5;

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public SearchService(
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService
    ) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    public List<SearchResultDto> search(SearchRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }

        List<Double> questionEmbedding = embeddingService.createEmbedding(request.question());

        return vectorStoreService.findSimilarChunks(questionEmbedding, DEFAULT_LIMIT);
    }
}