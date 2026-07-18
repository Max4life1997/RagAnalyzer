package ru.max.raganalyzer.dto;

import java.util.UUID;

public record CreateFolderRequest(String name, UUID parentFolderId) {
}
