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

    /**
     * Decide whether a chat message is the user logging a meal they ate (vs. asking
     * for advice), and extract the slot + food description. Returns {@link MealLog#none()}
     * on anything that isn't a clear logging intent, or on any model/parse failure.
     */
    MealLog detectMealLog(String userMessage);

    record MealLog(boolean isMeal, String slot, String food) {
        public static MealLog none() {
            return new MealLog(false, null, null);
        }
    }

    /**
     * Decide whether a chat message is the user asking to plan/arrange a workout (vs. a
     * how-to question), and extract category, duration and intensity. Returns
     * {@link WorkoutRequest#none()} on anything that isn't a clear plan request or on failure.
     */
    WorkoutRequest detectWorkoutRequest(String userMessage);

    record WorkoutRequest(boolean isWorkout, String category, int durationMin, String intensity) {
        public static WorkoutRequest none() {
            return new WorkoutRequest(false, null, 0, null);
        }
    }

    /**
     * Decide whether a chat message is the user reporting their current body weight (vs.
     * asking about weight in general), and extract the value in kilograms. Returns
     * {@link WeightLog#none()} on anything that isn't a clear logging intent, on an
     * out-of-range value, or on any model/parse failure.
     */
    WeightLog detectWeightLog(String userMessage);

    record WeightLog(boolean isWeight, double weightKg) {
        public static WeightLog none() {
            return new WeightLog(false, 0);
        }
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
