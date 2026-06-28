package ru.max.raganalyzer.dto;

import java.util.UUID;

public record SearchResultDto(
        UUID chunkId,
        UUID documentId,
        String fileName,
        int chunkIndex,
        String content,
        double distance,
        double similarity
) {
}