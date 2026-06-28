package ru.max.raganalyzer.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import ru.max.raganalyzer.dto.TextExtractionResult;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class TextExtractionService {

    private static final int PREVIEW_LENGTH = 300;

    private final Tika tika = new Tika();

    public TextExtractionResult extractText(Path filePath) {
        try {
            String text = tika.parseToString(filePath);

            String normalizedText = normalizeText(text);
            String preview = buildPreview(normalizedText);

            return new TextExtractionResult(
                    normalizedText,
                    normalizedText.length(),
                    preview
            );
        } catch (IOException | TikaException e) {
            throw new RuntimeException("Не удалось прочитать файл: " + filePath, e);
        }
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String buildPreview(String text) {
        if (text.length() <= PREVIEW_LENGTH) {
            return text;
        }

        return text.substring(0, PREVIEW_LENGTH) + "...";
    }
}