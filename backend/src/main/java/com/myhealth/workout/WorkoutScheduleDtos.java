package com.myhealth.workout;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkoutScheduleDtos {
    private WorkoutScheduleDtos() {
    }

    public record GeneratePlanRequest(
            @NotNull LocalDate startDate,
            @NotNull @Min(2) @Max(6) Integer daysPerWeek,
            @NotNull @Min(1) @Max(4) Integer weeks,
            WorkoutIntensity intensity
    ) {
    }

    /** Materialize one weekday of a schedule into a real workout plan on {@code date}. */
    public record ApplyDayRequest(
            @NotNull LocalDate date,
            @NotNull @Min(1) @Max(7) Integer weekday
    ) {
    }

    public record ScheduleDayDto(
            int weekday,
            boolean rest,
            String category,
            int durationMin,
            String focus
    ) {
    }

    public record WorkoutScheduleResponse(
            Long id,
            String goal,
            LocalDate startDate,
            int weeks,
            int daysPerWeek,
            String intensity,
            List<ScheduleDayDto> days,
            Instant createdAt
    ) {
    }
}
