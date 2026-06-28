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

        List<SearchResultDto> chunks = vectorStoreService.findSimilarChunks(
                questionEmbedding,
                CONTEXT_CHUNKS_LIMIT
        );

        if (chunks.isEmpty()) {
            return new AskResponse(
                    "В загруженных документах нет информации для ответа на этот вопрос.",
                    List.of()
            );
        }

        String context = buildContext(chunks);

        String answer = llmService.generateAnswer(request.question(), context);

        List<SourceDto> sources = chunks.stream()
                .map(chunk -> new SourceDto(
                        chunk.documentId(),
                        chunk.fileName(),
                        chunk.chunkIndex(),
                        chunk.distance()
                ))
                .toList();

        return new AskResponse(answer, sources);
    }

    private String buildContext(List<SearchResultDto> chunks) {
        return chunks.stream()
                .map(chunk -> """
                        Источник: %s, chunkIndex=%d
                        Текст:
                        %s
                        """.formatted(
                        chunk.fileName(),
                        chunk.chunkIndex(),
                        chunk.content()
                ))
                .collect(Collectors.joining("\n---\n"));
    }
}