package com.myhealth.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {
    }

    /**
     * Live-computed numbers for the covered week. Always reflects the latest data
     * (the AI narrative is cached separately and may be older).
     *
     * @param weightStart first known weight in the week (carried forward), or null
     * @param weightEnd   last known weight in the week, or null
     * @param weightDelta weightEnd - weightStart when both known, else null
     */
    public record WeeklySummary(
            int totalIntakeKcal,
            int avgIntakeKcal,
            int totalBurnKcal,
            int netKcal,
            int goalKcal,
            BigDecimal weightStart,
            BigDecimal weightEnd,
            BigDecimal weightDelta,
            int workoutsDone,
            int mealsLogged,
            int daysCovered
    ) {
    }

    /**
     * Goal-attainment rates for the covered week (each clamped 0–100). How each is derived:
     * <ul>
     *   <li>calorie: logged days whose intake stayed within the daily calorie goal ÷ logged days</li>
     *   <li>protein: total protein eaten ÷ (daily protein target × days covered)</li>
     *   <li>workout: completed sessions ÷ weekly session target (0 when no goal is set)</li>
     *   <li>logging: distinct days with a logged meal ÷ days covered</li>
     * </ul>
     *
     * @param workoutTarget weekly session goal, or null when the user has no workout goal
     * @param daysLogged    distinct days in the week with at least one logged meal
     * @param daysCovered   elapsed days of the week (7 for a past week, fewer mid-week)
     */
    public record WeeklyAdherence(
            int caloriePct,
            int proteinPct,
            int workoutPct,
            Integer workoutTarget,
            int loggingPct,
            int daysLogged,
            int daysCovered
    ) {
    }

    /**
     * A detected pattern worth surfacing this week (e.g. protein consistently low, training
     * volume down vs. last week, weight plateauing against a fat-loss/muscle-gain goal).
     *
     * @param type     stable machine code (PROTEIN_LOW, LOGGING_GAP, WORKOUT_DECLINE, WEIGHT_PLATEAU)
     * @param severity "info" or "warn" — drives UI emphasis
     */
    public record WeeklyTrend(
            String type,
            String severity,
            String title,
            String detail
    ) {
    }

    /**
     * @param adherence   goal-attainment rates for the week
     * @param trends      detected patterns worth acting on (may be empty)
     * @param narrative   the cached AI narrative, or null if none has been generated yet
     * @param generatedAt when the narrative was generated, or null if none
     */
    public record WeeklyReportResponse(
            LocalDate weekStart,
            LocalDate weekEnd,
            WeeklySummary summary,
            WeeklyAdherence adherence,
            List<WeeklyTrend> trends,
            String narrative,
            Instant generatedAt
    ) {
    }

    /** weekStart may be any date within the target week (normalized to Monday), or null for the current week. */
    public record GenerateReportRequest(LocalDate weekStart) {
    }
}
