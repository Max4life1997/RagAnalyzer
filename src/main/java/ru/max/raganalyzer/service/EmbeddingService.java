package ru.max.raganalyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import ru.max.raganalyzer.client.OllamaEmbedRequest;
import ru.max.raganalyzer.client.OllamaEmbedResponse;
import ru.max.raganalyzer.config.OllamaProperties;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    // Ollama выгружает неиспользуемые модели и поднимает runner-процесс заново —
    // первый запрос после простоя может попасть в окно гонки (connection refused)
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1500;

    private final RestClient restClient;
    private final OllamaProperties ollamaProperties;

    public EmbeddingService(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
        this.restClient = RestClient.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .build();
    }

    public List<Double> createEmbedding(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст для embedding пустой");
        }

        return createEmbeddings(List.of(text)).get(0);
    }

    public List<List<Double>> createEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("Список текстов для embedding пустой");
        }

        List<String> notBlankTexts = texts.stream()
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (notBlankTexts.isEmpty()) {
            throw new IllegalArgumentException("Все тексты для embedding пустые");
        }

        OllamaEmbedRequest request = new OllamaEmbedRequest(
                ollamaProperties.getEmbeddingModel(),
                notBlankTexts
        );

        OllamaEmbedResponse response = callWithRetry(request);

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new RuntimeException("Ollama не вернул embeddings");
        }

        if (response.embeddings().size() != notBlankTexts.size()) {
            throw new RuntimeException(
                    "Количество embeddings не совпадает с количеством текстов. texts="
                            + notBlankTexts.size()
                            + ", embeddings="
                            + response.embeddings().size()
            );
        }

        return response.embeddings();
    }

    private OllamaEmbedResponse callWithRetry(OllamaEmbedRequest request) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restClient.post()
                        .uri("/api/embed")
                        .body(request)
                        .retrieve()
                        .body(OllamaEmbedResponse.class);
            } catch (ResourceAccessException e) {
                // Сетевая ошибка (connection refused/reset) — обычно временная,
                // пока Ollama поднимает runner-процесс модели
                lastError = e;
                log.warn("Ollama embed недоступна (попытка {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_DELAY_MS * attempt);
                }
            }
        }

        throw new RuntimeException("Ollama embed недоступна после " + MAX_RETRIES + " попыток", lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}