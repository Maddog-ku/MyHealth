package com.myhealth.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record ChatRequest(@NotBlank @Size(max = 1000) String message) {
    }

    public record ChatMessageResponse(Long id, String role, String content, Instant createdAt) {
        static ChatMessageResponse from(ChatMessage message) {
            return new ChatMessageResponse(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
        }
    }

    public record ChatHistoryResponse(List<ChatMessageResponse> messages) {
    }

    /**
     * @param mealLogged    true if this turn recorded a meal into 飲食追蹤
     * @param workoutLogged true if this turn generated a workout plan into 運動菜單
     * @param loggedDate    ISO date the meal/workout was logged to (for cache invalidation), else null
     */
    public record ChatReplyResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse reply,
            boolean mealLogged,
            boolean workoutLogged,
            String loggedDate
    ) {
    }
}
