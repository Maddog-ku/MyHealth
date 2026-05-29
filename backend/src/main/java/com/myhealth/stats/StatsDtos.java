package com.myhealth.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StatsDtos {
    private StatsDtos() {
    }

    public record DailyStatsResponse(
            LocalDate date,
            int intakeKcal,
            int burnKcal,
            int netKcal,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carb,
            BigDecimal weightKg,
            int goalKcal,
            int workoutsDone,
            int workoutsPlanned
    ) {
    }

    public record RangeStatsResponse(LocalDate from, LocalDate to, List<SeriesPoint> series) {
    }

    public record SeriesPoint(LocalDate date, int intakeKcal, int burnKcal, BigDecimal weightKg) {
    }
}
