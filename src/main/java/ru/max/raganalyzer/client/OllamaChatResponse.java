package ru.max.raganalyzer.client;

public record OllamaChatResponse(
        String model,
        OllamaChatMessage message,
        boolean done
) {
}
