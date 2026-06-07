package com.myhealth.workout;

import java.time.LocalDate;
import java.util.List;

public final class WorkoutVolumeDtos {
    private WorkoutVolumeDtos() {
    }

    /** Aggregated training for one workout category over the analysed range. */
    public record CategoryVolume(
            String category,
            int sessions,
            int sets,
            int kcal
    ) {
    }

    /** One Monday-aligned week bucket for the trend series. */
    public record WeekVolume(
            LocalDate weekStart,
            int sessions,
            int sets,
            int kcal
    ) {
    }

    public record VolumeResponse(
            LocalDate from,
            LocalDate to,
            int weeks,
            int totalSessions,
            int totalSets,
            int totalKcal,
            int activeDays,
            double avgSessionsPerWeek,
            List<CategoryVolume> byCategory,
            List<WeekVolume> series
    ) {
    }
}
