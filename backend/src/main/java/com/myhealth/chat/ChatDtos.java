package com.myhealth.chat;

import com.myhealth.ai.AiProvider.FoodItem;
import com.myhealth.meal.MealDtos.MealPreviewResponse;
import com.myhealth.meal.MealDtos.MealResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
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

    public record ChatMealConfirmRequest(
            @NotNull LocalDate date,
            @NotBlank @Pattern(regexp = "breakfast|lunch|dinner|snack") String slot,
            @Size(max = 300) String description,
            @NotNull @Size(max = 5) List<@Valid FoodItem> items,
            @Size(max = 120) @Pattern(regexp = "^[\\p{L}\\p{N}\\s，。,.!?、:：()（）_-]*$") String aiSuggestion
    ) {
    }

    public record ChatMealConfirmResponse(
            MealResponse meal,
            ChatMessageResponse reply,
            String loggedDate
    ) {
    }

    /**
     * @param mealLogged    true if this turn recorded a meal into 飲食追蹤
     * @param workoutLogged true if this turn generated a workout plan into 運動菜單
     * @param weightLogged  true if this turn recorded a body-weight measurement
     * @param loggedDate    ISO date the meal/workout/weight was logged to (for cache invalidation), else null
     * @param mealPreview   non-null when a meal-log turn needs user confirmation before persisting
     */
    public record ChatReplyResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse reply,
            boolean mealLogged,
            boolean workoutLogged,
            boolean weightLogged,
            String loggedDate,
            MealPreviewResponse mealPreview
    ) {
        public ChatReplyResponse(ChatMessageResponse userMessage, ChatMessageResponse reply, boolean mealLogged,
                                 boolean workoutLogged, boolean weightLogged, String loggedDate) {
            this(userMessage, reply, mealLogged, workoutLogged, weightLogged, loggedDate, null);
        }
    }
}
