package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import ru.max.raganalyzer.dto.ChunkDto;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkService {

    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 200;

    public List<ChunkDto> splitIntoChunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeText(text);

        List<ChunkDto> chunks = new ArrayList<>();

        int start = 0;
        int index = 0;

        while (start < normalizedText.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalizedText.length());

            String chunkText = normalizedText.substring(start, end).trim();

            if (!chunkText.isBlank()) {
                chunks.add(new ChunkDto(
                        index,
                        chunkText,
                        chunkText.length()
                ));

                index++;
            }

            if (end == normalizedText.length()) {
                break;
            }

            start = end - OVERLAP;
        }

        return chunks;
    }

    private String normalizeText(String text) {
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}