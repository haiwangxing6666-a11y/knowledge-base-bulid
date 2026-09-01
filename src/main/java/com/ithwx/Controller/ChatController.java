package com.ithwx.Controller;

import com.ithwx.Dto.ChatRequest;
import com.ithwx.Dto.ChatResponse;
import com.ithwx.Service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库问答接口。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 根据知识库中的资料回答问题。
     */
    @PostMapping
    public ChatResponse chat(
            @Valid
            @RequestBody
            ChatRequest request
    ) {
        return chatService.ask(request);
    }
}