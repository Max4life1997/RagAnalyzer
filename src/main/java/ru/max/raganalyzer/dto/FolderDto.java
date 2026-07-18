package ru.max.raganalyzer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FolderDto(
        UUID id,
        String name,
        UUID parentFolderId,
        LocalDateTime createdAt
) {
}
