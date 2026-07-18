package ru.max.raganalyzer.telegram;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.max.raganalyzer.config.TelegramBotProperties;
import ru.max.raganalyzer.dto.AskRequest;
import ru.max.raganalyzer.dto.AskResponse;
import ru.max.raganalyzer.dto.SourceDto;
import ru.max.raganalyzer.service.ChatService;
import ru.max.raganalyzer.service.DocumentService;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class TelegramBotService extends TelegramLongPollingBot {

    private static final int MAX_DOCS_PER_USER = 5;

    private final TelegramBotProperties props;
    private final DocumentService documentService;
    private final ChatService chatService;

    // Сессия на каждого пользователя Telegram (ключ — chatId)
    private final Map<Long, TelegramSession> sessions = new ConcurrentHashMap<>();

    public TelegramBotService(TelegramBotProperties props,
                              DocumentService documentService,
                              ChatService chatService) {
        super(buildOptions(props), props.getToken());
        this.props           = props;
        this.documentService = documentService;
        this.chatService     = chatService;
    }

    private static DefaultBotOptions buildOptions(TelegramBotProperties props) {
        DefaultBotOptions options = new DefaultBotOptions();
        TelegramBotProperties.Proxy proxy = props.getProxy();
        if (proxy != null && proxy.isEnabled()) {
            options.setProxyType(DefaultBotOptions.ProxyType.valueOf(proxy.getType().toUpperCase()));
            options.setProxyHost(proxy.getHost());
            options.setProxyPort(proxy.getPort());
        }
        return options;
    }

    @PostConstruct
    public void register() {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(this);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Не удалось запустить Telegram бот", e);
        }
    }

    @Override
    public String getBotUsername() { return props.getUsername(); }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage()) return;
        Message msg = update.getMessage();
        long chatId = msg.getChatId();

        if (msg.hasText()) {
            handleText(chatId, msg.getText().trim());
        } else if (msg.hasDocument()) {
            handleDocument(chatId, msg.getDocument());
        }
    }

    // ─── Текстовые сообщения ─────────────────────────────────────────────────

    private void handleText(long chatId, String text) {
        switch (text) {
            case "/start"  -> sendWelcome(chatId);
            case "/help"   -> sendHelp(chatId);
            case "/docs"   -> sendDocsList(chatId);
            case "/reset"  -> resetSession(chatId);
            default        -> handleQuestion(chatId, text);
        }
    }

    private void sendWelcome(long chatId) {
        send(chatId, """
                👋 Привет! Я RAG-ассистент — отвечаю на вопросы по вашим документам.

                📎 Загрузите документ (PDF, TXT или DOCX) и я начну отвечать на вопросы по его содержимому.

                Команды:
                /docs — список загруженных документов
                /reset — сбросить сессию и начать заново
                /help — помощь""");
    }

    private void sendHelp(long chatId) {
        send(chatId, """
                ℹ️ Как пользоваться:

                1. Отправьте мне документ (PDF, TXT, DOCX) — до 20 МБ
                2. Задавайте вопросы текстом
                3. Я найду ответ в ваших документах

                /docs — список документов в сессии
                /reset — сбросить все документы и историю
                /help — эта справка""");
    }

    private void sendDocsList(long chatId) {
        TelegramSession session = getSession(chatId);
        if (session.getDocumentIds().isEmpty()) {
            send(chatId, "📂 Документов нет. Отправьте файл чтобы начать.");
            return;
        }

        long count = session.getDocumentIds().size();
        send(chatId, "📂 Загружено документов: " + count + "\n\nЧтобы сбросить — /reset");
    }

    private void resetSession(long chatId) {
        TelegramSession session = sessions.remove(chatId);
        if (session != null) {
            // Удаляем документы пользователя из системы
            List<UUID> toDelete = session.drainDocumentIds();
            int deleted = 0;
            for (UUID docId : toDelete) {
                try {
                    documentService.deleteDocument(docId);
                    deleted++;
                } catch (Exception e) {
                    // документ мог быть уже удалён — не страшно
                }
            }
            String msg = deleted > 0
                    ? "🔄 Сессия сброшена. Удалено документов: " + deleted + ".\nЗагрузите новый документ чтобы начать."
                    : "🔄 Сессия сброшена. Загрузите новый документ чтобы начать.";
            send(chatId, msg);
        } else {
            send(chatId, "🔄 Сессия уже пуста.");
        }
    }

    private void handleQuestion(long chatId, String question) {
        TelegramSession session = getSession(chatId);
        List<UUID> docIds = session.getDocumentIds();

        if (docIds.isEmpty()) {
            send(chatId, "📎 Сначала загрузите документ (PDF, TXT или DOCX).");
            return;
        }

        // Защита: используем только документы принадлежащие этому пользователю
        List<UUID> ownedIds = docIds.stream()
                .filter(session::ownsDocument)
                .toList();

        if (ownedIds.isEmpty()) {
            send(chatId, "❌ Нет доступных документов. Загрузите файл заново.");
            return;
        }

        send(chatId, "⏳ Думаю...");

        try {
            AskRequest request = new AskRequest(
                    question,
                    ownedIds,
                    session.getHistory(),
                    false,  // adviceEnabled
                    false   // wikiMode — недоступен в Telegram-боте
            );

            AskResponse response = chatService.ask(request);
            String answer = response.answer();

            session.addToHistory("user", question);
            session.addToHistory("assistant", answer);

            StringBuilder reply = new StringBuilder(answer);

            // Добавляем источники
            if (response.sources() != null && !response.sources().isEmpty()) {
                reply.append("\n\n📌 *Источники:*");
                response.sources().stream()
                        .map(s -> s.fileName() + " · стр. " + s.pageNumber())
                        .distinct()
                        .limit(3)
                        .forEach(s -> reply.append("\n• ").append(s));
            }

            sendMarkdown(chatId, reply.toString());

        } catch (Exception e) {
            send(chatId, "❌ Произошла ошибка при получении ответа. Попробуйте ещё раз.");
        }
    }

    // ─── Загрузка документов ─────────────────────────────────────────────────

    private void handleDocument(long chatId, Document doc) {
        String fileName = doc.getFileName() != null ? doc.getFileName() : "document";

        if (!isSupportedFile(fileName)) {
            send(chatId, "❌ Поддерживаются только PDF, TXT и DOCX файлы.");
            return;
        }

        TelegramSession session = getSession(chatId);
        if (session.getDocumentIds().size() >= MAX_DOCS_PER_USER) {
            send(chatId, "⚠️ Максимум " + MAX_DOCS_PER_USER + " документов на сессию. Сделайте /reset чтобы сбросить.");
            return;
        }

        send(chatId, "📥 Загружаю «" + fileName + "»...");

        try {
            byte[] fileBytes = downloadTelegramFile(doc.getFileId());
            String contentType = detectContentType(fileName);

            ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(
                    fileName, contentType, fileBytes
            );

            var uploadResponse = documentService.uploadDocument(multipartFile);
            session.addDocument(uploadResponse.documentId());

            send(chatId, "✅ Документ «" + fileName + "» загружен и индексируется.\n\n" +
                    "Через несколько секунд вы сможете задавать вопросы по нему.");

        } catch (Exception e) {
            send(chatId, "❌ Ошибка при загрузке: " + e.getMessage());
        }
    }

    private byte[] downloadTelegramFile(String fileId) throws Exception {
        GetFile getFile = new GetFile(fileId);
        org.telegram.telegrambots.meta.api.objects.File tgFile = execute(getFile);
        String fileUrl = "https://api.telegram.org/file/bot" + props.getToken() + "/" + tgFile.getFilePath();

        java.net.URLConnection connection;
        TelegramBotProperties.Proxy proxyCfg = props.getProxy();
        if (proxyCfg != null && proxyCfg.isEnabled()) {
            java.net.Proxy.Type proxyType = proxyCfg.getType().equalsIgnoreCase("SOCKS5")
                    ? java.net.Proxy.Type.SOCKS
                    : java.net.Proxy.Type.HTTP;
            java.net.Proxy proxy = new java.net.Proxy(
                    proxyType,
                    new java.net.InetSocketAddress(proxyCfg.getHost(), proxyCfg.getPort())
            );
            connection = new URL(fileUrl).openConnection(proxy);
        } else {
            connection = new URL(fileUrl).openConnection();
        }

        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);

        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    private TelegramSession getSession(long chatId) {
        return sessions.computeIfAbsent(chatId, id -> new TelegramSession());
    }

    private void send(long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        try { execute(msg); } catch (TelegramApiException ignored) {}
    }

    private void sendMarkdown(long chatId, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            // Если Markdown не парсится — отправляем как обычный текст
            send(chatId, text.replaceAll("[*_`\\[\\]]", ""));
        }
    }

    private boolean isSupportedFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".docx");
    }

    private String detectContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "text/plain";
    }
}
