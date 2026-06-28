package ru.max.raganalyzer.client;

import java.util.Map;

public record OllamaGenerateRequest(
        String model,
        String prompt,
        boolean stream,
        Map<String, Object> options
) {
}