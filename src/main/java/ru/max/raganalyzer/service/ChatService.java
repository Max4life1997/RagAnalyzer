package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import ru.max.raganalyzer.dto.AskRequest;
import ru.max.raganalyzer.dto.AskResponse;
import ru.max.raganalyzer.dto.SearchResultDto;
import ru.max.raganalyzer.dto.SourceDto;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final int CONTEXT_CHUNKS_LIMIT = 5;
    private static final double MAX_DISTANCE = 0.55;

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final LlmService llmService;

    public ChatService(
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            LlmService llmService
    ) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.llmService = llmService;
    }

    public AskResponse ask(AskRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }

        List<Double> questionEmbedding = embeddingService.createEmbedding(request.question());

        List<SearchResultDto> foundChunks = vectorStoreService.findSimilarChunks(
                questionEmbedding,
                CONTEXT_CHUNKS_LIMIT
        );

        List<SearchResultDto> relevantChunks = foundChunks.stream()
                .filter(chunk -> chunk.distance() <= MAX_DISTANCE)
                .toList();

        if (relevantChunks.isEmpty()) {
            return new AskResponse(
                    "В загруженных документах нет достаточно релевантной информации для ответа на этот вопрос.",
                    List.of()
            );
        }

        String context = buildContext(relevantChunks);

        String answer = llmService.generateAnswer(request.question(), context);

        List<SourceDto> sources = relevantChunks.stream()
                .map(chunk -> new SourceDto(
                        chunk.documentId(),
                        chunk.fileName(),
                        chunk.chunkIndex(),
                        chunk.distance(),
                        chunk.similarity()
                ))
                .toList();

        return new AskResponse(answer, sources);
    }

    private String buildContext(List<SearchResultDto> chunks) {
        return chunks.stream()
                .map(chunk -> """
                    [Источник]
                    Файл: %s
                    Номер чанка: %d
                    Similarity: %.4f

                    [Текст]
                    %s
                    """.formatted(
                        chunk.fileName(),
                        chunk.chunkIndex(),
                        chunk.similarity(),
                        chunk.content()
                ))
                .collect(Collectors.joining("\n---\n"));
    }
}