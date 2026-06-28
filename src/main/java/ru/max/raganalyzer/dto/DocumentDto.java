package ru.max.raganalyzer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        String fileName,
        String storedPath,
        long sizeBytes,
        int textLength,
        String status,
        LocalDateTime createdAt
) {
}