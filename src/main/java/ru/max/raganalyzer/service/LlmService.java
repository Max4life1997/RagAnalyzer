package ru.max.raganalyzer.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.max.raganalyzer.client.OllamaChatMessage;
import ru.max.raganalyzer.client.OllamaChatRequest;
import ru.max.raganalyzer.client.OllamaChatResponse;
import ru.max.raganalyzer.config.OllamaProperties;
import ru.max.raganalyzer.dto.MessageDto;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    // Останавливаем генерацию как только модель начинает повторять вопрос
    // или съезжать в другой язык — типичная проблема маленьких моделей
    private static final List<String> STOP_SEQUENCES = List.of(
            "\nВопрос:", "\nQuestion:", "\nQ:", "\n问题", "\n答案", "\n问", "\nПользователь:"
    );

    private static final String SYSTEM_PROMPT = """
            Отвечай ИСКЛЮЧИТЕЛЬНО на русском языке. Никогда не используй китайский, английский или любой другой язык.

            Контекст документов может быть на английском или другом иностранном языке (например, если документ —
            книга или статья на английском). Это не повод отвечать на этом языке или вставлять иностранные фразы.
            ПЕРЕВОДИ на русский абсолютно всё, что попадает в твой ответ: имена должностей, дословные цитаты,
            термины, названия. Не оставляй ни одного английского/иностранного слова или фразы в финальном ответе,
            кроме случаев, когда сам термин является собственным именем без устоявшегося перевода (например,
            "Хогвартс"). Должности и описания переводи полностью, например "Keeper of the Keys and Grounds"
            нужно перевести как "Хранитель ключей и территории", а не оставлять английский вариант.

            Ты помощник, который отвечает на вопросы по загруженным документам пользователя.
            В сообщении пользователя будет контекст из документов (помечен "## Документ: название") и сам вопрос.

            Правила:
            1. Отвечай только на основе предоставленного контекста документов.
            2. Не придумывай факты, которых нет в контексте — если текст не говорит явно кем кто-то является,
               не делай предположений (например, не придумывай родственные связи, должности, даты, если они
               не указаны буквально в контексте).
            3. Учитывай историю переписки — пользователь может уточнять предыдущий вопрос.
            4. Отвечай развёрнуто и по существу.
            5. Используй markdown: **жирный** для терминов, "- " для списков, "> " для цитат из документа
               (цитаты тоже переводи на русский).
            6. Не пиши "Ответ:" в начале и не повторяй вопрос пользователя.
            7. Для вычислений не используй LaTeX/frac/cdot — пиши формулы обычным текстом,
               например: E = P * i * (1 + i)^n / ((1 + i)^n - 1).

            Если ответа нет ни в одном из документов, напиши строго:
            В загруженных документах нет информации для ответа на этот вопрос.""";

    private static final String ADVICE_SUFFIX = """

            Дополнительно: после ответа по документам добавь отдельный раздел "Мой взгляд" —
            твоё осторожное практическое мнение или рекомендацию. Явно отдели его от фактов из документов
            и не выдумывай факты которых нет в контексте.""";

    // Стриминг — отдаёт токены по одному в onToken, в конце вызывает onDone
    public void streamAnswer(String question, String context, List<MessageDto> history, boolean adviceEnabled,
                             Consumer<String> onToken, Runnable onDone) {
        List<OllamaChatMessage> messages = buildMessages(question, context, history, adviceEnabled);

        OllamaChatRequest request = new OllamaChatRequest(
                ollamaProperties.getChatModel(),
                messages,
                true,   // stream = true
                Map.of("temperature", 0, "stop", STOP_SEQUENCES)
        );

        restClient.post()
                .uri("/api/chat")
                .body(request)
                .exchange((req, res) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            try {
                                OllamaChatResponse chunk =
                                        objectMapper.readValue(line, OllamaChatResponse.class);
                                if (chunk.message() != null && chunk.message().content() != null
                                        && !chunk.message().content().isEmpty()) {
                                    onToken.accept(chunk.message().content());
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

        OllamaChatRequest request = new OllamaChatRequest(
                ollamaProperties.getChatModel(),
                List.of(
                        new OllamaChatMessage("system", "Отвечай только на русском языке, кратко, без рассуждений."),
                        new OllamaChatMessage("user", prompt)
                ),
                false,
                Map.of("temperature", 0)
        );

        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null
                || response.message().content() == null || response.message().content().isBlank()) {
            return "Диалог";
        }

        return cleanTitle(response.message().content());
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

    public String generateAnswer(String question, String context, List<MessageDto> history, boolean adviceEnabled) {
        List<OllamaChatMessage> messages = buildMessages(question, context, history, adviceEnabled);

        OllamaChatRequest request = new OllamaChatRequest(
                ollamaProperties.getChatModel(),
                messages,
                false,
                Map.of(
                        "temperature", 0,
                        "top_p", 0.9,
                        "stop", STOP_SEQUENCES
                )
        );

        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null
                || response.message().content() == null || response.message().content().isBlank()) {
            throw new RuntimeException("Ollama не вернул ответ");
        }

        return cleanAnswer(response.message().content());
    }

    private List<OllamaChatMessage> buildMessages(String question, String context,
                                                   List<MessageDto> history, boolean adviceEnabled) {
        List<OllamaChatMessage> messages = new ArrayList<>();

        String system = adviceEnabled ? SYSTEM_PROMPT + ADVICE_SUFFIX : SYSTEM_PROMPT;
        messages.add(new OllamaChatMessage("system", system));

        // Реальная история диалога как отдельные сообщения — модель видит её
        // через свой родной chat-template, а не как текст внутри одного промпта
        for (MessageDto msg : history) {
            String role = "user".equals(msg.role()) ? "user" : "assistant";
            messages.add(new OllamaChatMessage(role, msg.text()));
        }

        String userMessage = """
                Контекст из документов:
                %s

                Вопрос: %s""".formatted(context, question);

        messages.add(new OllamaChatMessage("user", userMessage));
        return messages;
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

        // Страховка: если stop-последовательность не сработала и модель всё же
        // съехала в китайский/японский — обрезаем с этого места
        cleaned = cutAtForeignScript(cleaned);

        return cleaned.trim();
    }

    // Обрезает текст на первом блоке из 3+ символов CJK (китайский/японский/корейский) подряд
    private String cutAtForeignScript(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]{3,}")
                .matcher(text);
        return m.find() ? text.substring(0, m.start()) : text;
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
}
