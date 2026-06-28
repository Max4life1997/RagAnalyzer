package ru.max.raganalyzer.dto;

import java.util.List;

public record AskResponse(
        String answer,
        List<SourceDto> sources
) {
}