package ru.max.raganalyzer.client;

public record OllamaGenerateRequest(
        String model,
        String prompt,
        boolean stream
) {
}