package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.max.raganalyzer.client.OllamaGenerateRequest;
import ru.max.raganalyzer.client.OllamaGenerateResponse;
import ru.max.raganalyzer.config.OllamaProperties;

@Service
public class LlmService {

    private final RestClient restClient;
    private final OllamaProperties ollamaProperties;

    public LlmService(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
        this.restClient = RestClient.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .build();
    }

    public String generateAnswer(String question, String context) {
        String prompt = buildPrompt(question, context);

        OllamaGenerateRequest request = new OllamaGenerateRequest(
                ollamaProperties.getChatModel(),
                prompt,
                false
        );

        OllamaGenerateResponse response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(OllamaGenerateResponse.class);

        if (response == null || response.response() == null || response.response().isBlank()) {
            throw new RuntimeException("Ollama не вернул ответ");
        }

        return response.response().trim();
    }

    private String buildPrompt(String question, String context) {
        return """
                Ты помощник, который отвечает на вопросы только на основе переданного контекста.

                Правила:
                1. Используй только информацию из контекста.
                2. Если в контексте нет ответа, скажи: "В загруженных документах нет информации для ответа на этот вопрос."
                3. Не придумывай факты.
                4. Отвечай кратко и понятно.
                5. Отвечай на русском языке.

                Контекст:
                ---
                %s
                ---

                Вопрос пользователя:
                %s

                Ответ:
                """.formatted(context, question);
    }
}