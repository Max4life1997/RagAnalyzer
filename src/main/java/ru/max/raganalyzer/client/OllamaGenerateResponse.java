package ru.max.raganalyzer.client;

public record OllamaGenerateResponse(
        String model,
        String response,
        boolean done
) {
}