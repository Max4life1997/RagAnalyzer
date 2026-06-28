package ru.max.raganalyzer.dto;

import java.util.List;
import java.util.UUID;

public record DocumentUploadResponse(
        UUID documentId,
        String fileName,
        long size,
        String storedPath,
        int textLength,
        String textPreview,
        int chunksCount,
        List<ChunkDto> chunks,
        String status
) {
}