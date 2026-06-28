package ru.max.raganalyzer.dto;

public record ChunkDto(
        int index,
        String content,
        int length
) {
}