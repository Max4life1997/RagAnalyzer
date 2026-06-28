package ru.max.raganalyzer.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
        String fileName = filePath.getFileName().toString().toLowerCase();

        try {
            String text = fileName.endsWith(".pdf")
                    ? extractFromPdf(filePath)
                    : extractWithTika(filePath);

            String preview = text.length() <= PREVIEW_LENGTH
                    ? text
                    : text.substring(0, PREVIEW_LENGTH) + "...";

            return new TextExtractionResult(text, text.length(), preview);

        } catch (IOException | TikaException e) {
            throw new RuntimeException("Не удалось прочитать файл: " + filePath, e);
        }
    }

    // Для PDF: извлекаем текст постранично, страницы разделяем \f
    // ChunkService использует \f чтобы определить номер страницы каждого чанка
    private String extractFromPdf(Path filePath) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(filePath.toAbsolutePath().toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            StringBuilder sb = new StringBuilder();

            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = stripper.getText(pdf);
                String normalized = normalizeText(pageText);

                if (!normalized.isBlank()) {
                    if (sb.length() > 0) sb.append('\f');
                    sb.append(normalized);
                }
            }

            return sb.toString();
        }
    }

    private String extractWithTika(Path filePath) throws IOException, TikaException {
        return normalizeText(tika.parseToString(filePath));
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
