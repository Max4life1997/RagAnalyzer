package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.max.raganalyzer.client.OllamaEmbedRequest;
import ru.max.raganalyzer.client.OllamaEmbedResponse;
import ru.max.raganalyzer.config.OllamaProperties;

import java.util.List;

@Service
public class EmbeddingService {

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

        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(request)
                .retrieve()
                .body(OllamaEmbedResponse.class);

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
}