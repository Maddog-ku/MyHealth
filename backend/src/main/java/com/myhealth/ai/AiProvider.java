package com.myhealth.ai;

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

    MealAnalysis analyzeMeal(String description, boolean hasImage);

    record ExerciseItem(String name, int sets, String reps, int restSec, int kcal, String note, List<String> alt) {
    }

    record FoodItem(String name, double grams, int kcal, double protein, double fat, double carb, double confidence) {
    }

    record MealAnalysis(List<FoodItem> items, String suggestion) {
        public static MealAnalysis empty() {
            return new MealAnalysis(List.of(), "AI 暫不可用，請手動補上餐點熱量與營養素。");
        }
    }
}
