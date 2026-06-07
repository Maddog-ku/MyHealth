package com.myhealth.workout;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public final class WorkoutGoalDtos {
    private WorkoutGoalDtos() {
    }

    public record SetWorkoutGoalRequest(
            @NotNull @Min(1) @Max(14) Integer targetSessionsPerWeek
    ) {
    }

    /**
     * Live progress toward this week's training target.
     *
     * @param completedThisWeek done workouts since Monday (inclusive)
     * @param remaining         max(0, target − completed)
     * @param progressPct       0–100, clamped
     * @param achieved          completed ≥ target
     */
    public record WorkoutGoalProgress(
            int targetSessionsPerWeek,
            int completedThisWeek,
            int remaining,
            int progressPct,
            LocalDate weekStart,
            boolean achieved,
            Instant createdAt
    ) {
    }

    /** GET/PUT envelope; {@code progress} is null when the user has no goal set. */
    public record WorkoutGoalResponse(WorkoutGoalProgress progress) {
    }
}
