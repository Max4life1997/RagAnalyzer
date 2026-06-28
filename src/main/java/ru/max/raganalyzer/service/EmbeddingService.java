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

        OllamaEmbedRequest request = new OllamaEmbedRequest(
                ollamaProperties.getEmbeddingModel(),
                text
        );

        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(request)
                .retrieve()
                .body(OllamaEmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new RuntimeException("Ollama не вернул embedding");
        }

        return response.embeddings().get(0);
    }
}