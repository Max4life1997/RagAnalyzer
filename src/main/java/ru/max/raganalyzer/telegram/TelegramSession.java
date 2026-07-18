package ru.max.raganalyzer.telegram;

import ru.max.raganalyzer.dto.MessageDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TelegramSession {

    private final List<UUID> documentIds = new ArrayList<>();
    private final List<MessageDto> history = new ArrayList<>();

    public List<UUID> getDocumentIds() { return List.copyOf(documentIds); }
    public List<MessageDto> getHistory() { return history; }

    public void addDocument(UUID id) {
        if (!documentIds.contains(id)) documentIds.add(id);
    }

    public boolean ownsDocument(UUID id) {
        return documentIds.contains(id);
    }

    // Возвращает список ID для удаления и очищает сессию
    public List<UUID> drainDocumentIds() {
        List<UUID> copy = new ArrayList<>(documentIds);
        documentIds.clear();
        return copy;
    }

    public void addToHistory(String role, String text) {
        history.add(new MessageDto(role, text));
        while (history.size() > 20) history.remove(0);
    }

    public void clearHistory() {
        history.clear();
    }
}
