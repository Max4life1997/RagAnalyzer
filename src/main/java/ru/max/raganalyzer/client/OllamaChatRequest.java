package ru.max.raganalyzer.client;

import java.util.List;
import java.util.Map;

public record OllamaChatRequest(
        String model,
        List<OllamaChatMessage> messages,
        boolean stream,
        Map<String, Object> options
) {
}
