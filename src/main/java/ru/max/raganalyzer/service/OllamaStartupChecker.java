package ru.max.raganalyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.max.raganalyzer.client.OllamaEmbedRequest;
import ru.max.raganalyzer.client.OllamaGenerateRequest;
import ru.max.raganalyzer.config.OllamaProperties;

import java.util.List;
import java.util.Map;

@Component
public class OllamaStartupChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaStartupChecker.class);
    private static final int MAX_WAIT_SECONDS = 15;

    private final OllamaProperties properties;
    private final RestClient restClient;

    public OllamaStartupChecker(OllamaProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isOllamaUp()) {
            log.info("Ollama уже запущена ({})", properties.getBaseUrl());
            warmUpModels();
            return;
        }

        log.warn("Ollama не отвечает на {} — пробую запустить 'ollama serve'...", properties.getBaseUrl());

        if (!tryStartOllama()) {
            log.error("Не удалось запустить Ollama автоматически. " +
                    "Запустите вручную командой 'ollama serve', иначе чат и эмбеддинги не будут работать.");
            return;
        }

        if (waitForOllama()) {
            log.info("Ollama успешно запущена и отвечает.");
            warmUpModels();
        } else {
            log.error("Ollama была запущена, но не отвечает спустя {} сек. " +
                    "Проверьте 'ollama serve' вручную.", MAX_WAIT_SECONDS);
        }
    }

    // Делаем пробные запросы к моделям чтобы Ollama подняла runner-процессы заранее,
    // а не при первом реальном запросе пользователя (где race condition приводил к ошибке)
    private void warmUpModels() {
        warmUpEmbeddingModel();
        warmUpChatModel();
    }

    private void warmUpEmbeddingModel() {
        try {
            restClient.post()
                    .uri("/api/embed")
                    .body(new OllamaEmbedRequest(properties.getEmbeddingModel(), List.of("warmup")))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Embedding-модель '{}' прогрета", properties.getEmbeddingModel());
        } catch (Exception e) {
            log.warn("Не удалось прогреть embedding-модель '{}': {}", properties.getEmbeddingModel(), e.getMessage());
        }
    }

    private void warmUpChatModel() {
        try {
            restClient.post()
                    .uri("/api/generate")
                    .body(new OllamaGenerateRequest(properties.getChatModel(), "Hi", false, Map.of("num_predict", 1)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Chat-модель '{}' прогрета", properties.getChatModel());
        } catch (Exception e) {
            log.warn("Не удалось прогреть chat-модель '{}': {}", properties.getChatModel(), e.getMessage());
        }
    }

    private boolean isOllamaUp() {
        try {
            restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryStartOllama() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "serve");
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            return true;
        } catch (Exception e) {
            log.error("Команда 'ollama serve' не найдена или не запустилась: {}", e.getMessage());
            return false;
        }
    }

    private boolean waitForOllama() {
        for (int i = 0; i < MAX_WAIT_SECONDS; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (isOllamaUp()) return true;
        }
        return false;
    }
}
