package ru.max.raganalyzer.client;

public record OllamaEmbedRequest(
        String model,
        Object input
) {
}