package ru.max.raganalyzer.controller;

import org.springframework.web.bind.annotation.*;
import ru.max.raganalyzer.dto.EmbeddingTestRequest;
import ru.max.raganalyzer.dto.EmbeddingTestResponse;
import ru.max.raganalyzer.service.EmbeddingService;

import java.util.List;

@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/test")
    public EmbeddingTestResponse testEmbedding(@RequestBody EmbeddingTestRequest request) {
        List<Double> embedding = embeddingService.createEmbedding(request.text());

        return new EmbeddingTestResponse(
                embedding.size(),
                embedding.subList(0, Math.min(5, embedding.size())).toString()
        );
    }
}