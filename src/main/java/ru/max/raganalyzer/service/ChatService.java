package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import ru.max.raganalyzer.config.RagSearchProperties;
import ru.max.raganalyzer.dto.*;
import ru.max.raganalyzer.entity.DocumentImageEntity;
import ru.max.raganalyzer.repository.DocumentImageRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final RagSearchProperties ragSearchProperties;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final LlmService llmService;
    private final DocumentImageRepository documentImageRepository;

    public ChatService(
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            LlmService llmService,
            RagSearchProperties ragSearchProperties,
            DocumentImageRepository documentImageRepository
    ) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.llmService = llmService;
        this.ragSearchProperties = ragSearchProperties;
        this.documentImageRepository = documentImageRepository;
    }

    public AskResponse ask(AskRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }

        if (request.documentIds() == null || request.documentIds().isEmpty()) {
            throw new IllegalArgumentException("Нужно выбрать хотя бы один документ");
        }

        List<Double> questionEmbedding = embeddingService.createEmbedding(request.question());

        List<SearchResultDto> foundChunks = vectorStoreService.findSimilarChunksByDocuments(
                questionEmbedding,
                request.documentIds(),
                ragSearchProperties.getTopK()
        );

        List<SearchResultDto> relevantChunks = foundChunks.stream()
                .filter(chunk -> chunk.distance() <= ragSearchProperties.getMaxDistance())
                .toList();

        if (relevantChunks.isEmpty()) {
            return new AskResponse(
                    "В выбранных документах нет достаточно релевантной информации для ответа на этот вопрос.",
                    List.of(),
                    List.of()
            );
        }

        String context = buildContext(relevantChunks);

        List<MessageDto> history = request.history() != null ? request.history() : List.of();

        String answer = llmService.generateAnswer(request.question(), context, history);

        List<SourceDto> sources = relevantChunks.stream()
                .map(chunk -> new SourceDto(
                        chunk.documentId(),
                        chunk.fileName(),
                        chunk.chunkIndex(),
                        chunk.pageNumber(),
                        chunk.distance(),
                        chunk.similarity()
                ))
                .toList();

        List<ImageDto> images = fetchImagesForSources(relevantChunks);

        return new AskResponse(answer, sources, images);
    }

    // Собираем изображения со страниц, которые стали источниками ответа
    private List<ImageDto> fetchImagesForSources(List<SearchResultDto> chunks) {
        // Группируем страницы по документу
        var pagesByDocument = chunks.stream()
                .collect(Collectors.groupingBy(
                        SearchResultDto::documentId,
                        Collectors.mapping(SearchResultDto::pageNumber, Collectors.toList())
                ));

        return pagesByDocument.entrySet().stream()
                .flatMap(entry -> {
                    UUID documentId = entry.getKey();
                    List<Integer> pages = entry.getValue().stream().distinct().toList();

                    return documentImageRepository
                            .findByDocumentIdAndPageNumberIn(documentId, pages)
                            .stream()
                            .map(img -> new ImageDto(
                                    "/api/images/%s/%d_%d.png".formatted(
                                            documentId,
                                            img.getPageNumber(),
                                            img.getImageIndex()
                                    ),
                                    img.getPageNumber()
                            ));
                })
                .toList();
    }

    private String buildContext(List<SearchResultDto> chunks) {
        return chunks.stream()
                .map(chunk -> """
                    [Источник]
                    Файл: %s, Страница: %d
                    Similarity: %.4f

                    [Текст]
                    %s
                    """.formatted(
                        chunk.fileName(),
                        chunk.pageNumber(),
                        chunk.similarity(),
                        chunk.content()
                ))
                .collect(Collectors.joining("\n---\n"));
    }
}
