package com.myhealth.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.myhealth.auth.AuthDtos.UserResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A self-contained snapshot of everything we hold for a user, suitable for download as a
 * single JSON file ("export my data"). Item arrays are embedded as real nested JSON rather
 * than escaped strings.
 */
public final class ExportDtos {
    private ExportDtos() {
    }

    public record ExportFile(
            String exportedAt,
            UserResponse account,
            List<MeasurementExport> bodyMeasurements,
            List<WorkoutExport> workouts,
            List<MealExport> meals,
            WeightGoalExport weightGoal,
            List<FavoriteMealExport> favoriteMeals,
            List<HabitExport> habits
    ) {
    }

    public record MeasurementExport(
            Instant measuredAt,
            BigDecimal weightKg,
            BigDecimal bodyFatPct,
            BigDecimal muscleMassKg,
            Integer bmrKcal,
            BigDecimal waistCm,
            BigDecimal bodyWaterPct,
            String note
    ) {
    }

    public record WorkoutExport(
            LocalDate date,
            String category,
            JsonNode items,
            int totalKcal,
            Integer burnedKcal,
            boolean done,
            Instant createdAt
    ) {
    }

    public record MealExport(
            LocalDate date,
            String slot,
            String description,
            String imageUrl,
            JsonNode items,
            int totalKcal,
            BigDecimal totalProtein,
            BigDecimal totalFat,
            BigDecimal totalCarb,
            String aiSuggestion,
            Instant createdAt
    ) {
    }

    public record WeightGoalExport(
            BigDecimal targetWeightKg,
            BigDecimal startWeightKg,
            LocalDate startDate,
            LocalDate targetDate,
            Instant createdAt
    ) {
    }

    public record FavoriteMealExport(
            String name,
            String slot,
            String description,
            JsonNode items,
            int totalKcal,
            BigDecimal totalProtein,
            BigDecimal totalFat,
            BigDecimal totalCarb,
            String aiSuggestion,
            Instant createdAt
    ) {
    }

    public record HabitExport(
            LocalDate date,
            String type,
            Instant completedAt
    ) {
    }
}
