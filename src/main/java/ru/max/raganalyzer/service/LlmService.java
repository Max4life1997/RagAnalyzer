package ru.max.raganalyzer.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.max.raganalyzer.client.OllamaGenerateRequest;
import ru.max.raganalyzer.client.OllamaGenerateResponse;
import ru.max.raganalyzer.config.OllamaProperties;
import ru.max.raganalyzer.dto.MessageDto;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class LlmService {

    private final RestClient restClient;
    private final OllamaProperties ollamaProperties;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public LlmService(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
        this.restClient = RestClient.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .build();
    }

    // Стриминг — отдаёт токены по одному в onToken, в конце вызывает onDone
    public void streamAnswer(String question, String context, List<MessageDto> history,
                             Consumer<String> onToken, Runnable onDone) {
        String prompt = buildPrompt(question, context, history);

        OllamaGenerateRequest request = new OllamaGenerateRequest(
                ollamaProperties.getChatModel(),
                prompt,
                true,   // stream = true
                Map.of("temperature", 0)
        );

        restClient.post()
                .uri("/api/generate")
                .body(request)
                .exchange((req, res) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            try {
                                OllamaGenerateResponse chunk =
                                        objectMapper.readValue(line, OllamaGenerateResponse.class);
                                if (chunk.response() != null && !chunk.response().isEmpty()) {
                                    onToken.accept(chunk.response());
                                }
                                if (chunk.done()) break;
                            } catch (Exception ignored) {}
                        }
                    }
                    onDone.run();
                    return null;
                });
    }

    public String generateTitle(List<MessageDto> history) {
        String dialog = history.stream()
                .map(m -> ("user".equals(m.role()) ? "Пользователь: " : "Ассистент: ") + m.text())
                .collect(java.util.stream.Collectors.joining("\n"));

        String prompt = """
                На основе диалога придумай очень короткое название чата (2-5 слов, без кавычек, без точки в конце).
                Только само название, ничего больше.

                Диалог:
                %s

                Название:
                """.formatted(dialog);

        OllamaGenerateRequest request = new OllamaGenerateRequest(
                ollamaProperties.getChatModel(),
                prompt,
                false,
                Map.of("temperature", 0)
        );

        OllamaGenerateResponse response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(OllamaGenerateResponse.class);

        if (response == null || response.response() == null || response.response().isBlank()) {
            return "Диалог";
        }

        return cleanTitle(response.response());
    }

    private String cleanTitle(String raw) {
        return raw
                .replaceAll("(?s)<think>.*?</think>", "")
                .replace("\"", "")
                .replace("«", "").replace("»", "")
                .replaceAll("^\\*+|\\*+$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public String generateAnswer(String question, String context, List<MessageDto> history) {
        String prompt = buildPrompt(question, context, history);

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

    private String buildPrompt(String question, String context, List<MessageDto> history) {
        String historyBlock = "";
        if (!history.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (MessageDto msg : history) {
                String role = "user".equals(msg.role()) ? "Пользователь" : "Ассистент";
                sb.append(role).append(": ").append(msg.text()).append("\n");
            }
            historyBlock = """

            История переписки (для понимания контекста диалога):
            ---
            %s---
            """.formatted(sb);
        }

        return """
            You are a Russian-language assistant. Always respond in Russian only. Never use Chinese, English, or other languages.
            Ты помощник, который отвечает на вопросы по загруженным документам.
            Контекст содержит отрывки из одного или нескольких документов, каждый помечен заголовком "## Документ: название".

            Правила:
            1. Внимательно прочитай все документы в контексте и найди ответ в любом из них.
            2. Учитывай историю переписки — пользователь может уточнять предыдущий вопрос.
            3. Отвечай развёрнуто и подробно, если вопрос этого требует.
            4. Отвечай на русском языке.
            5. Используй markdown для форматирования:
               - **жирный** — для важных терминов
               - - пункт — для перечислений
               - > цитата — для дословных фраз из документа
            6. Не пиши "Ответ:" в начале.
            7. Не добавляй информацию, которой нет в контексте.

            Если ответа нет ни в одном из документов, напиши строго:
            В загруженных документах нет информации для ответа на этот вопрос.

            Контекст:
            %s
            %s
            Вопрос: %s

            """.formatted(context, historyBlock, question);
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

        // Убираем только JSON-блоки кода, остальной markdown оставляем
        cleaned = cleaned
                .replace("```json", "")
                .replace("```", "")
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

        // Нормализуем пробелы внутри строк, но сохраняем переносы
        cleaned = cleaned
                .replaceAll("[ \\t]+", " ")
                .trim();

        // Если модель написала список в одну строку через "* пункт * пункт",
        // разбиваем на отдельные строки чтобы marked распознал это как список
        cleaned = normalizeInlineLists(cleaned);

        return cleaned;
    }

    // Если модель пишет "* пункт 1. * пункт 2." в одну строку — разбиваем на строки
    private String normalizeInlineLists(String text) {
        // Ищем паттерн: пробел + "* " посреди строки и ставим перенос перед *
        String result = text.replaceAll("(?<=\\S)\\s+\\*\\s+", "\n* ");

        // То же для нумерованных списков: "1. текст 2. текст" → перенос перед цифрой
        result = result.replaceAll("(?<=\\.)\\s+(\\d+\\.\\s)", "\n$1");

        return result;
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