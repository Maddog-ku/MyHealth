package com.myhealth.habit;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class HabitDtos {
    private HabitDtos() {
    }

    public record HabitItemResponse(
            HabitType type,
            String title,
            String description,
            boolean completed,
            Instant completedAt,
            int streak
    ) {
    }

    public record DailyHabitsResponse(
            LocalDate date,
            int completed,
            int total,
            List<HabitItemResponse> items
    ) {
    }

    public record ToggleHabitRequest(
            @NotNull LocalDate date,
            @NotNull Boolean completed
    ) {
    }
}
