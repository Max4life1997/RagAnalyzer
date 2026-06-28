package ru.max.raganalyzer.dto;

import java.util.UUID;

public record SourceDto(
        UUID documentId,
        String fileName,
        int chunkIndex,
        double distance,
        double similarity
) {
}