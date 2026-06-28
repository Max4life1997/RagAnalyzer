package ru.max.raganalyzer.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ru.max.raganalyzer.dto.AskRequest;
import ru.max.raganalyzer.dto.AskResponse;
import ru.max.raganalyzer.dto.GenerateTitleRequest;
import ru.max.raganalyzer.service.ChatService;
import ru.max.raganalyzer.service.LlmService;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final LlmService llmService;

    public ChatController(ChatService chatService, LlmService llmService) {
        this.chatService = chatService;
        this.llmService = llmService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        return chatService.ask(request);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AskRequest request) {
        return chatService.askStream(request);
    }

    @PostMapping("/generate-title")
    public Map<String, String> generateTitle(@RequestBody GenerateTitleRequest request) {
        String title = llmService.generateTitle(request.history());
        return Map.of("title", title);
    }
}
