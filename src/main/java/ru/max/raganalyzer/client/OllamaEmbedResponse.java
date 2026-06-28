package ru.max.raganalyzer.client;

import java.util.List;

public record OllamaEmbedResponse(
        String model,
        List<List<Double>> embeddings
) {
}