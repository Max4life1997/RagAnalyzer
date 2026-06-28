package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.max.raganalyzer.client.OllamaGenerateRequest;
import ru.max.raganalyzer.client.OllamaGenerateResponse;
import ru.max.raganalyzer.config.OllamaProperties;

import java.util.Map;

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
                false,
                Map.of(
                        "temperature", 0,
                        "top_p", 0.9
                )
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
            Ты RAG-ассистент.
            Ответь на вопрос пользователя только на основе контекста.

            Правила:
            1. Верни только текст ответа.
            2. Не возвращай JSON.
            3. Не используй фигурные скобки.
            4. Не пиши "Ответ:".
            5. Не пиши рассуждения.
            6. Не используй markdown.
            7. Не добавляй информацию не из контекста.
            8. Ответ должен быть на русском языке.
            9. Ответ должен быть коротким, 1 предложение.

            Если ответа нет в контексте, верни строго:
            В загруженных документах нет информации для ответа на этот вопрос.

            Контекст:
            ---
            %s
            ---

            Вопрос:
            %s

            Короткий ответ без JSON, без кавычек и без слова "Ответ:":
            """.formatted(context, question);
    }

    private String cleanAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }

        String cleaned = answer;

        // Удаляем thinking-блоки
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "");

        if (cleaned.contains("</think>")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf("</think>") + "</think>".length());
        }

        // Убираем markdown
        cleaned = cleaned
                .replace("```json", "")
                .replace("```", "")
                .replace("`", "")
                .replace("**", "")
                .replace("*", "")
                .trim();

        // Убираем фигурные скобки, если модель вернула что-то типа {Ответ: ...}
        cleaned = cleaned
                .replaceAll("^\\s*\\{\\s*", "")
                .replaceAll("\\s*}\\s*$", "")
                .trim();

        // Убираем кавычки вокруг всего ответа
        cleaned = cleaned
                .replaceAll("^\"+", "")
                .replaceAll("\"+$", "")
                .trim();

        // Убираем маркеры ответа
        cleaned = removeAnswerPrefix(cleaned);

        // Убираем лишние кавычки после удаления префикса
        cleaned = cleaned
                .replaceAll("^\"+", "")
                .replaceAll("\"+$", "")
                .trim();

        // Нормализуем пробелы
        cleaned = cleaned
                .replaceAll("\\s+", " ")
                .trim();

        // Если модель вернула несколько предложений с мусором, берём первое нормальное русское
        cleaned = takeFirstUsefulRussianSentence(cleaned);

        return cleaned;
    }

    private String removeAnswerPrefix(String text) {
        return text
                .replaceFirst("(?i)^answer\\s*:\\s*", "")
                .replaceFirst("(?i)^final answer\\s*:\\s*", "")
                .replaceFirst("^Ответ\\s*:\\s*", "")
                .replaceFirst("^Финальный ответ\\s*:\\s*", "")
                .trim();
    }

    private String takeFirstUsefulRussianSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] sentences = text.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            String candidate = sentence.trim();

            boolean hasRussian = candidate.matches(".*[А-Яа-яЁё].*");
            boolean hasBadText = candidate.toLowerCase().contains("draft")
                    || candidate.toLowerCase().contains("analyze")
                    || candidate.toLowerCase().contains("context")
                    || candidate.toLowerCase().contains("question")
                    || candidate.toLowerCase().contains("check constraints")
                    || candidate.toLowerCase().contains("final review");

            if (hasRussian && !hasBadText) {
                return candidate;
            }
        }

        return text.trim();
    }

    private String extractAfterLastMarker(String text, String marker) {
        if (text.contains(marker)) {
            return text.substring(text.lastIndexOf(marker) + marker.length()).trim();
        }

        return text;
    }

    private String takeBestRussianSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // Делим по точкам, восклицательным и вопросительным знакам
        String[] sentences = text.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            String candidate = sentence.trim();

            boolean hasRussian = candidate.matches(".*[А-Яа-яЁё].*");
            boolean hasBadEnglish = candidate.toLowerCase().contains("draft")
                    || candidate.toLowerCase().contains("tags")
                    || candidate.toLowerCase().contains("constraints")
                    || candidate.toLowerCase().contains("analyze")
                    || candidate.toLowerCase().contains("question")
                    || candidate.toLowerCase().contains("context")
                    || candidate.toLowerCase().contains("answer");

            if (hasRussian && !hasBadEnglish) {
                return candidate;
            }
        }

        return text.trim();
    }
}