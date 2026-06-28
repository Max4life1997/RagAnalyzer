package ru.max.raganalyzer.service;

import org.springframework.stereotype.Service;
import ru.max.raganalyzer.dto.ChunkDto;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkService {

    private static final int MAX_CHUNK_SIZE = 1200;
    private static final int MIN_CHUNK_SIZE = 100;

    public List<ChunkDto> splitIntoChunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeText(text);

        // Шаг 1: делим на параграфы с отслеживанием номеров страниц
        List<Paragraph> paragraphs = parseParagraphs(normalizedText);

        // Шаг 2: собираем параграфы в чанки
        List<String[]> chunkTextsWithPages = buildChunks(paragraphs);

        List<ChunkDto> chunks = new ArrayList<>();
        for (int i = 0; i < chunkTextsWithPages.size(); i++) {
            String content = chunkTextsWithPages.get(i)[0].trim();
            int pageNumber = Integer.parseInt(chunkTextsWithPages.get(i)[1]);
            if (!content.isBlank()) {
                chunks.add(new ChunkDto(i, content, content.length(), pageNumber));
            }
        }

        return chunks;
    }

    // Параграф с пометкой — является ли он заголовком, и на какой странице находится
    private record Paragraph(String text, boolean isHeading, int pageNumber) {}

    // Разбиваем текст на параграфы, считая страницы по \f (form feed)
    private List<Paragraph> parseParagraphs(String text) {
        List<Paragraph> result = new ArrayList<>();
        int currentPage = 1;

        // Разбиваем по \f сначала — получаем страницы
        String[] pages = text.split("\f");

        for (String page : pages) {
            String[] rawParagraphs = page.split("\n\n+");

            for (String raw : rawParagraphs) {
                String p = raw.trim();
                if (p.isBlank()) continue;

                if (isHeading(p)) {
                    result.add(new Paragraph(p, true, currentPage));
                } else if (p.length() <= MAX_CHUNK_SIZE) {
                    result.add(new Paragraph(p, false, currentPage));
                } else {
                    for (String sentence : splitBySentences(p)) {
                        result.add(new Paragraph(sentence, false, currentPage));
                    }
                }
            }

            currentPage++;
        }

        return result;
    }

    // Заголовок: короткая строка (до 120 символов), без точки в конце,
    // не является нумерованным элементом списка
    private boolean isHeading(String text) {
        if (text.contains("\n")) return false;
        if (text.length() > 120) return false;
        if (text.endsWith(".") || text.endsWith(",") || text.endsWith(":")) return false;
        if (text.matches("^\\d+\\.\\s+.*")) return false; // "1. Пункт списка"
        return true;
    }

    private List<String> splitBySentences(String text) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() + 1 > MAX_CHUNK_SIZE && current.length() > 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(" ");
            current.append(sentence);
        }

        if (!current.toString().isBlank()) result.add(current.toString().trim());
        return result;
    }

    // Возвращает пары [текст чанка, номер страницы]
    private List<String[]> buildChunks(List<Paragraph> paragraphs) {
        List<String[]> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentHeading = "";
        String lastSentenceOverlap = "";
        int chunkStartPage = 1;

        for (Paragraph para : paragraphs) {
            if (para.isHeading()) {
                currentHeading = para.text();
                continue;
            }

            boolean wouldExceed = current.length() > 0
                    && current.length() + para.text().length() + 2 > MAX_CHUNK_SIZE;

            if (wouldExceed) {
                String chunkText = current.toString().trim();
                if (chunkText.length() >= MIN_CHUNK_SIZE) {
                    chunks.add(new String[]{chunkText, String.valueOf(chunkStartPage)});
                    lastSentenceOverlap = extractLastSentence(chunkText);
                }

                current = new StringBuilder();
                chunkStartPage = para.pageNumber();

                if (!currentHeading.isBlank()) {
                    current.append("[Раздел: ").append(currentHeading).append("]\n\n");
                }
                if (!lastSentenceOverlap.isBlank()) {
                    current.append(lastSentenceOverlap).append("\n\n");
                }
            }

            if (current.length() == 0) {
                chunkStartPage = para.pageNumber();
                if (!currentHeading.isBlank()) {
                    current.append("[Раздел: ").append(currentHeading).append("]\n\n");
                }
            }

            if (current.length() > 0 && !current.toString().endsWith("\n\n")) {
                current.append("\n\n");
            }
            current.append(para.text());
        }

        String remaining = current.toString().trim();
        if (remaining.length() >= MIN_CHUNK_SIZE) {
            chunks.add(new String[]{remaining, String.valueOf(chunkStartPage)});
        } else if (!remaining.isBlank() && !chunks.isEmpty()) {
            String[] last = chunks.get(chunks.size() - 1);
            chunks.set(chunks.size() - 1, new String[]{last[0] + "\n\n" + remaining, last[1]});
        } else if (!remaining.isBlank()) {
            chunks.add(new String[]{remaining, String.valueOf(chunkStartPage)});
        }

        return chunks;
    }

    private String extractLastSentence(String text) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length == 0) return "";
        return sentences[sentences.length - 1].trim();
    }

    private String normalizeText(String text) {
        // Текст уже нормализован в TextExtractionService, \f сохранён как разделитель страниц
        return text.trim();
    }
}
