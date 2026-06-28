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

        return cleanAnswer(response.response());
    }

    private String buildPrompt(String question, String context) {
        return """
            /no_think

            Ты RAG-ассистент. Отвечай только на основе контекста.

            ВАЖНО:
            - Верни только финальный ответ.
            - Не показывай рассуждения.
            - Не пиши план анализа.
            - Не используй markdown.
            - Не используй английский язык.
            - Не используй теги <think>.
            - Не добавляй факты от себя.

            Правила ответа:
            1. Используй только информацию из контекста.
            2. Если в контексте есть точная фраза, которая отвечает на вопрос, используй её максимально близко к оригиналу.
            3. Не переформулируй без необходимости.
            4. Если ответа нет в контексте, ответь строго:
            В загруженных документах нет информации для ответа на этот вопрос.
            5. Ответ должен быть коротким, 1-2 предложения.

            Контекст:
            ---
            %s
            ---

            Вопрос:
            %s

            Ответ только на русском языке:
            """.formatted(context, question);
    }

    private String cleanAnswer(String answer) {
        if (answer == null) {
            return "";
        }

        String cleaned = answer;

        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "");

        if (cleaned.contains("</think>")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf("</think>") + "</think>".length());
        }

        if (cleaned.contains("Answer:")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf("Answer:") + "Answer:".length());
        }

        if (cleaned.contains("Ответ:")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf("Ответ:") + "Ответ:".length());
        }

        return cleaned
                .replace("```", "")
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}