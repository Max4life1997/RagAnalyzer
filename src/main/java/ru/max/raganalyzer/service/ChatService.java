package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import ru.max.raganalyzer.config.RagSearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.max.raganalyzer.dto.*;
import ru.max.raganalyzer.entity.DocumentImageEntity;
import ru.max.raganalyzer.repository.DocumentImageRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final RagSearchProperties ragSearchProperties;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final LlmService llmService;
    private final DocumentImageRepository documentImageRepository;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // Ищем раздельно по каждому документу — каждый документ гарантированно представлен
        int perDocLimit = Math.max(4, ragSearchProperties.getTopK() / Math.max(1, request.documentIds().size()));

        List<SearchResultDto> foundChunks = request.documentIds().stream()
                .flatMap(docId -> vectorStoreService.findSimilarChunksByDocuments(
                        questionEmbedding,
                        List.of(docId),
                        perDocLimit
                ).stream())
                .filter(chunk -> chunk.distance() <= ragSearchProperties.getMaxDistance())
                .toList();

        // Группируем по документу и считаем среднюю дистанцию — более релевантный документ идёт первым
        Map<UUID, List<SearchResultDto>> byDocument = foundChunks.stream()
                .collect(Collectors.groupingBy(SearchResultDto::documentId));

        // Сортируем документы по средней дистанции их чанков
        List<SearchResultDto> relevantChunks = byDocument.values().stream()
                .sorted(Comparator.comparingDouble(
                        chunks -> chunks.stream().mapToDouble(SearchResultDto::distance).average().orElse(1.0)
                ))
                .flatMap(List::stream)
                .limit(ragSearchProperties.getTopK())
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

    public SseEmitter askStream(AskRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                if (request.question() == null || request.question().isBlank()
                        || request.documentIds() == null || request.documentIds().isEmpty()) {
                    emitter.send(SseEmitter.event().data(StreamEvent.error("Некорректный запрос")));
                    emitter.complete();
                    return;
                }

                List<Double> questionEmbedding = embeddingService.createEmbedding(request.question());

                int perDocLimit = Math.max(4, ragSearchProperties.getTopK() / Math.max(1, request.documentIds().size()));

                List<SearchResultDto> foundChunks = request.documentIds().stream()
                        .flatMap(docId -> vectorStoreService.findSimilarChunksByDocuments(
                                questionEmbedding, List.of(docId), perDocLimit).stream())
                        .filter(c -> c.distance() <= ragSearchProperties.getMaxDistance())
                        .toList();

                Map<UUID, List<SearchResultDto>> byDocument = foundChunks.stream()
                        .collect(Collectors.groupingBy(SearchResultDto::documentId));

                List<SearchResultDto> relevantChunks = byDocument.values().stream()
                        .sorted(Comparator.comparingDouble(
                                chunks -> chunks.stream().mapToDouble(SearchResultDto::distance).average().orElse(1.0)))
                        .flatMap(List::stream)
                        .limit(ragSearchProperties.getTopK())
                        .toList();

                if (relevantChunks.isEmpty()) {
                    emitter.send(SseEmitter.event().data(
                            StreamEvent.token("В выбранных документах нет достаточно релевантной информации для ответа на этот вопрос.")));
                    emitter.send(SseEmitter.event().data(StreamEvent.done()));
                    emitter.complete();
                    return;
                }

                List<SourceDto> sources = relevantChunks.stream()
                        .map(c -> new SourceDto(c.documentId(), c.fileName(), c.chunkIndex(),
                                c.pageNumber(), c.distance(), c.similarity()))
                        .toList();

                List<ImageDto> images = fetchImagesForSources(relevantChunks);

                // Сначала отправляем метаданные (источники и изображения)
                emitter.send(SseEmitter.event().data(
                        objectMapper.writeValueAsString(StreamEvent.meta(sources, images))));

                String context = buildContext(relevantChunks);
                List<MessageDto> history = request.history() != null ? request.history() : List.of();

                // Стримим токены — сериализуем вручную для гарантии формата
                llmService.streamAnswer(request.question(), context, history,
                        token -> {
                            try {
                                emitter.send(SseEmitter.event().data(
                                        objectMapper.writeValueAsString(StreamEvent.token(token))));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().data(
                                        objectMapper.writeValueAsString(StreamEvent.done())));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(StreamEvent.error(e.getMessage())));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
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
        // Группируем чанки по документу, сохраняя порядок
        Map<UUID, List<SearchResultDto>> byDoc = chunks.stream()
                .collect(Collectors.groupingBy(
                        SearchResultDto::documentId,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));

        StringBuilder sb = new StringBuilder();
        for (List<SearchResultDto> docChunks : byDoc.values()) {
            String fileName = docChunks.get(0).fileName();
            sb.append("## Документ: ").append(fileName).append("\n\n");

            for (SearchResultDto chunk : docChunks) {
                sb.append("Страница ").append(chunk.pageNumber()).append(":\n");
                sb.append(chunk.content().trim()).append("\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString().trim();
    }
}
