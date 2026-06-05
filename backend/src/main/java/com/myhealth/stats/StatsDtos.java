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

    /** One macronutrient's daily target vs. what's been eaten (grams). */
    public record MacroBudget(String name, int targetG, int consumedG, int pct) {
    }

    /**
     * The day's calorie budget ring. Exercise earns calories back, so
     * {@code budgetKcal = goalKcal + burnKcal} and {@code remainingKcal} may go negative.
     *
     * @param consumedPct intake / budget as a percentage (uncapped; UI clamps the ring)
     * @param over        true once intake exceeds the budget
     */
    public record CalorieBudgetResponse(
            LocalDate date,
            int goalKcal,
            int intakeKcal,
            int burnKcal,
            int budgetKcal,
            int remainingKcal,
            int consumedPct,
            boolean over,
            List<MacroBudget> macros
    ) {
    }

    public record SeriesPoint(
            LocalDate date,
            int intakeKcal,
            int burnKcal,
            BigDecimal weightKg,
            BigDecimal bodyFatPct,
            BigDecimal muscleMassKg,
            BigDecimal waistCm,
            BigDecimal bodyWaterPct
    ) {
    }
}
