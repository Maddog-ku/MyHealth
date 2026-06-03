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

    public record ChatReplyResponse(ChatMessageResponse userMessage, ChatMessageResponse reply) {
    }
}
