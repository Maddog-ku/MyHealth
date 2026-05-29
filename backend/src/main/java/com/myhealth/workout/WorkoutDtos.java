package com.myhealth.workout;

import com.myhealth.ai.AiProvider.ExerciseItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkoutDtos {
    private WorkoutDtos() {
    }

    public record GenerateWorkoutRequest(
            @NotNull LocalDate date,
            @NotBlank String category,
            @Min(10) @Max(180) Integer durationMin,
            String intensity,
            List<String> equipmentOverride
    ) {
    }

    public record CompleteWorkoutRequest(Integer actualKcal, String note) {
    }

    public record WorkoutPlanResponse(
            Long id,
            LocalDate date,
            String category,
            List<ExerciseItem> items,
            int totalKcal,
            boolean done,
            Instant createdAt
    ) {
    }
}
