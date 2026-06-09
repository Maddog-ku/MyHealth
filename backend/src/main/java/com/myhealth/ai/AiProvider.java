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

    /**
     * Generate a short, grounded weekly health review from {@code context} — a factual
     * block of the week's real numbers (intake/burn/weight/workouts). Returns free text;
     * implementations fall back to a conservative template if the model is unavailable.
     */
    String weeklyReport(String context);

    /**
     * Plan a one-week training split for the given {@code goal} label, number of training
     * days ({@code daysPerWeek}), {@code intensity} and the user's training {@code experience}
     * label (so the split matches a beginner vs. an advanced lifter). Returns exactly 7
     * {@link ScheduleDay} entries (Monday..Sunday), of which {@code daysPerWeek} are training
     * days and the rest are recovery days. Implementations fall back to a deterministic
     * template split when the model is unavailable or returns something unusable.
     */
    List<ScheduleDay> planWorkoutSchedule(String goal, int daysPerWeek, String intensity, String experience);

    record ScheduleDay(
            @Min(1) @Max(7) int weekday,
            boolean rest,
            @Size(max = 20) String category,
            @Min(10) @Max(180) int durationMin,
            @Size(max = 40) String focus
    ) {
        public static ScheduleDay restDay(int weekday) {
            return new ScheduleDay(weekday, true, null, 0, "休息與恢復");
        }
    }

    record ChatTurn(boolean fromUser, String content) {
    }

    /**
     * Classify a chat message into a single actionable intent — logging a meal, planning a
     * workout, logging body weight, or none — in ONE model call (instead of one call per
     * intent type), extracting the relevant fields. Returns {@link ChatIntent#none()} on
     * plain conversation or any model/parse failure. Implementations validate/normalize
     * fields so callers can map directly to {@link MealLog}/{@link WorkoutRequest}/{@link WeightLog}.
     */
    ChatIntent detectIntent(String userMessage);

    /**
     * @param action one of "log_meal", "plan_workout", "log_weight", "none"
     */
    record ChatIntent(
            String action,
            String slot,
            String food,
            String category,
            int durationMin,
            String intensity,
            double weightKg
    ) {
        public static ChatIntent none() {
            return new ChatIntent("none", "", "", "", 30, "medium", 0);
        }
    }

    record MealLog(boolean isMeal, String slot, String food) {
        public static MealLog none() {
            return new MealLog(false, null, null);
        }
    }

    record WorkoutRequest(boolean isWorkout, String category, int durationMin, String intensity) {
        public static WorkoutRequest none() {
            return new WorkoutRequest(false, null, 0, null);
        }
    }

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
