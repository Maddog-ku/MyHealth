package com.myhealth.chat;

import com.myhealth.ai.AiEndpointRateLimiter;
import com.myhealth.auth.CurrentUser;
import com.myhealth.chat.ChatDtos.ChatHistoryResponse;
import com.myhealth.chat.ChatDtos.ChatMealConfirmRequest;
import com.myhealth.chat.ChatDtos.ChatMealConfirmResponse;
import com.myhealth.chat.ChatDtos.ChatReplyResponse;
import com.myhealth.chat.ChatDtos.ChatRequest;
import com.myhealth.user.AppUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class ChatController {
    private final CurrentUser currentUser;
    private final ChatService chatService;
    private final AiEndpointRateLimiter rateLimiter;

    public ChatController(CurrentUser currentUser, ChatService chatService, AiEndpointRateLimiter rateLimiter) {
        this.currentUser = currentUser;
        this.chatService = chatService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/history")
    ChatHistoryResponse history() {
        return new ChatHistoryResponse(chatService.history(currentUser.require()));
    }

    @PostMapping
    ChatReplyResponse send(@Valid @RequestBody ChatRequest request) {
        AppUser user = currentUser.require();
        rateLimiter.checkChat(user);
        return chatService.send(user, request.message());
    }

    @PostMapping("/meal/confirm")
    ChatMealConfirmResponse confirmMeal(@Valid @RequestBody ChatMealConfirmRequest request) {
        AppUser user = currentUser.require();
        rateLimiter.checkMealCreate(user);
        return chatService.confirmMealPreview(user, request);
    }

    @DeleteMapping("/history")
    ResponseEntity<Void> clear() {
        chatService.clear(currentUser.require());
        return ResponseEntity.noContent().build();
    }
}
