package com.myhealth.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public interface AiProvider {
    String provider();

    String textModel();

    String visionModel();

    boolean loaded();

    Instant lastUsedAt();

    void markUsed();

    void unload();

    List<ExerciseItem> generateWorkout(String category, int durationMin, String intensity);

    MealAnalysis analyzeMeal(String description, MealImage image);

    /**
     * Free-text coaching reply. {@code context} is a short factual block about the
     * user (profile + today's numbers) injected into the system prompt; {@code history}
     * is the prior conversation (oldest first) and {@code userMessage} the new turn.
     */
    String chat(String context, List<ChatTurn> history, String userMessage);

    record ChatTurn(boolean fromUser, String content) {
    }

    record ExerciseItem(
            @NotBlank @Size(max = 80) String name,
            @Min(1) @Max(6) int sets,
            @NotBlank @Size(max = 20) @Pattern(regexp = "^[\\p{L}\\p{N}\\s/.-]+$") String reps,
            @Min(15) @Max(180) int restSec,
            @Min(10) @Max(600) int durationSec,
            @Min(5) @Max(250) int kcal,
            @NotBlank @Size(max = 60) String note,
            @Size(max = 2) List<@NotBlank @Size(max = 80) String> alt
    ) {
    }

    record FoodItem(
            @NotBlank @Size(max = 80) String name,
            @DecimalMin("0.0") @DecimalMax("5000.0") double grams,
            @Min(0) @Max(5000) int kcal,
            @DecimalMin("0.0") @DecimalMax("500.0") double protein,
            @DecimalMin("0.0") @DecimalMax("500.0") double fat,
            @DecimalMin("0.0") @DecimalMax("1000.0") double carb,
            @DecimalMin("0.0") @DecimalMax("1.0") double confidence
    ) {
    }

    record MealImage(String contentType, byte[] bytes) {
        public boolean present() {
            return bytes != null && bytes.length > 0;
        }
    }

    record MealAnalysis(List<FoodItem> items, String suggestion) {
        public static MealAnalysis empty() {
            return new MealAnalysis(List.of(), "AI 暫不可用，請手動補上餐點熱量與營養素。");
        }
    }
}
