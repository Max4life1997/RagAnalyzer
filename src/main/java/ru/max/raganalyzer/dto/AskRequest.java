package ru.max.raganalyzer.dto;

import java.util.List;
import java.util.UUID;

public record AskRequest(
        String question,
        List<UUID> documentIds,
        List<MessageDto> history
) {
}
