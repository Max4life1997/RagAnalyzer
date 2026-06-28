package ru.max.raganalyzer.dto;

public record TextExtractionResult(
        String text,
        int length,
        String preview
) {
}