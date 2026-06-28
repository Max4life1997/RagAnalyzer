package ru.max.raganalyzer.controller;

import org.springframework.web.bind.annotation.*;
import ru.max.raganalyzer.dto.AskRequest;
import ru.max.raganalyzer.dto.AskResponse;
import ru.max.raganalyzer.service.ChatService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        return chatService.ask(request);
    }
}