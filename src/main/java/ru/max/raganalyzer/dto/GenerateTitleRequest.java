package ru.max.raganalyzer.dto;

import java.util.List;

public record GenerateTitleRequest(
        List<MessageDto> history
) {
}
