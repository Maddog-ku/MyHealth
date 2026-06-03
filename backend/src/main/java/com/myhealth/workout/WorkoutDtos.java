package com.myhealth.workout;

import com.myhealth.ai.AiProvider.ExerciseItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkoutDtos {
    private WorkoutDtos() {
    }

    public record GenerateWorkoutRequest(
            @NotNull LocalDate date,
            @NotNull WorkoutCategory category,
            @Min(10) @Max(180) Integer durationMin,
            WorkoutIntensity intensity,
            @Size(max = 10) List<@NotBlank @Size(max = 30) @Pattern(regexp = "^[\\p{L}\\p{N}\\s._-]+$") String> equipmentOverride
    ) {
    }

    public record CompleteWorkoutRequest(
            @Min(0) @Max(3000) Integer actualKcal,
            @Size(max = 200) @Pattern(regexp = "^[\\p{L}\\p{N}\\s，。,.!?、:：()（）_-]*$") String note
    ) {
    }

    public record RemoveItemsRequest(
            @NotNull @Size(min = 1, max = 20) List<@NotNull @Min(0) @Max(20) Integer> indices
    ) {
    }

    public record WorkoutPlanResponse(
            Long id,
            LocalDate date,
            String category,
            List<ExerciseItem> items,
            int totalKcal,
            Integer burnedKcal,
            boolean done,
            Instant createdAt
    ) {
    }
}
